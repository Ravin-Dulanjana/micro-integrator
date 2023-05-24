package org.wso2.micro.integrator.grpc.client;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.rest.RESTConstants;
import org.json.JSONObject;

import java.util.Objects;

public class GrpcUtils {

    private static final Log LOG = LogFactory.getLog(GrpcUtils.class);

    static org.wso2.micro.integrator.grpc.proto.Error createProtoError(String message, org.apache.axis2.context.MessageContext axis2MessageContext,
                                      String statusCode) {
        LOG.error(message);
        return createResponse(message, axis2MessageContext, statusCode);
    }

    private static org.wso2.micro.integrator.grpc.proto.Error createResponse(String message, org.apache.axis2.context.MessageContext axis2MessageContext,
                                             String statusCode) {
        org.wso2.micro.integrator.grpc.proto.Error error = org.wso2.micro.integrator.grpc.proto.Error.newBuilder()
                .setMessage(message).build();
        axis2MessageContext.setProperty(Constants.HTTP_STATUS_CODE, statusCode);
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
}
