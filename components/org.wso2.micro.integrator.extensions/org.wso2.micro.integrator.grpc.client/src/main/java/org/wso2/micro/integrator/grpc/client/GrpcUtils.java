package org.wso2.micro.integrator.grpc.client;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.aspects.AspectConfiguration;
import org.apache.synapse.commons.json.JsonUtil;
import org.apache.synapse.config.SynapseConfigUtils;
import org.apache.synapse.rest.RESTConstants;
import org.json.JSONObject;
import org.wso2.micro.core.util.AuditLogger;
import org.wso2.micro.integrator.initializer.dashboard.ArtifactUpdateListener;
import org.wso2.micro.service.mgt.ServiceAdmin;

import java.util.Objects;

import static org.wso2.carbon.inbound.endpoint.common.Constants.SUPER_TENANT_DOMAIN_NAME;

public class GrpcUtils {

    private static final Log LOG = LogFactory.getLog(GrpcUtils.class);

    static org.wso2.micro.integrator.grpc.proto.Error createProtoError(String message, String statusCode) {
        LOG.error(message);
        return createResponse(message, statusCode);
    }

    private static org.wso2.micro.integrator.grpc.proto.Error createResponse(String message, String statusCode) {
        org.wso2.micro.integrator.grpc.proto.Error error = org.wso2.micro.integrator.grpc.proto.Error.newBuilder()
                .setMessage(message).build();
        //axis2MessageContext.setProperty(Constants.HTTP_STATUS_CODE, statusCode);
        return error;
    }

    public static String getPathParameter(MessageContext messageContext, String key){
        if (Objects.nonNull(messageContext.getProperty(RESTConstants.REST_URI_VARIABLE_PREFIX + key))) {
            return messageContext.getProperty(RESTConstants.REST_URI_VARIABLE_PREFIX + key).toString();
        }
        return null;
    }

    public static String getCarbonHome() {

        String carbonHome = System.getProperty("carbon.home");
        if (carbonHome == null) {
            carbonHome = System.getenv("CARBON_HOME");
            System.setProperty("carbon.home", carbonHome);
        }
        return carbonHome;
    }

    public static ServiceAdmin getServiceAdmin() {

        ServiceAdmin serviceAdmin = ServiceAdmin.getInstance();
        if (!serviceAdmin.isInitailized()) {
            serviceAdmin.init(SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME).getAxisConfiguration());
        }
        return serviceAdmin;
    }

    static org.wso2.micro.integrator.grpc.proto.Message handleTracing(String performedBy, String type, String artifactType, JSONObject info,
                                    AspectConfiguration config, String artifactName, String traceState) {
        String msg = null;
        org.wso2.micro.integrator.grpc.proto.Message.Builder responseBuilder = org.wso2.micro.integrator.grpc.proto.Message.newBuilder();
        if (traceState != null && !traceState.isEmpty()) {
            if (Constants.ENABLE.equalsIgnoreCase(traceState)) {
                config.enableTracing();
                msg = "Enabled tracing for ('" + artifactName + "')";
                responseBuilder.setMessage(msg);
                AuditLogger.logAuditMessage(performedBy, type, Constants.AUDIT_LOG_ACTION_ENABLE, info);
                ArtifactUpdateListener.addToUpdatedArtifactsQueue(artifactType, artifactName);
            } else if (Constants.DISABLE.equalsIgnoreCase(traceState)) {
                config.disableTracing();
                msg = "Disabled tracing for ('" + artifactName + "')";
                responseBuilder.setMessage(msg);
                AuditLogger.logAuditMessage(performedBy, type, Constants.AUDIT_LOG_ACTION_DISABLED, info);
                ArtifactUpdateListener.addToUpdatedArtifactsQueue(artifactType, artifactName);
            }
        }
        LOG.info(msg);
        return responseBuilder.build();
    }
}
