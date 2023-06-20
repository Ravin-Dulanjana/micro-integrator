package org.wso2.micro.integrator.initializer.dashboard.grpcClient;


import org.apache.axiom.om.OMElement;
import org.apache.synapse.aspects.AspectConfiguration;
import org.apache.synapse.config.SynapseConfigUtils;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.config.xml.endpoints.EndpointSerializer;
import org.apache.synapse.endpoints.AbstractEndpoint;
import org.apache.synapse.endpoints.Endpoint;
import org.json.JSONObject;
import org.wso2.micro.core.util.AuditLogger;

import javax.xml.namespace.QName;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.wso2.carbon.inbound.endpoint.common.Constants.SUPER_TENANT_DOMAIN_NAME;
import static org.wso2.micro.integrator.initializer.dashboard.grpcClient.Constants.ACTIVE_STATUS;
import static org.wso2.micro.integrator.initializer.dashboard.grpcClient.Constants.INACTIVE_STATUS;

public class EndpointResourceGrpc {
    private static final String ENDPOINT_NAME = "endpointName";
    private static List<Endpoint> getSearchResults(String searchKey) {
        return SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME).getDefinedEndpoints().values().stream()
                .filter(artifact -> artifact.getName().toLowerCase().contains(searchKey))
                .collect(Collectors.toList());
    }

    public static org.wso2.micro.integrator.grpc.proto.EndpointList populateSearchResults(String searchKey) {
        List<Endpoint> searchResultList = getSearchResults(searchKey);
        return setGrpcResponseBody(searchResultList);
    }

    private void handleTracing(String performedBy, String endpointName, String traceState) {
        if (endpointName != null) {
            SynapseConfiguration configuration = SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME);
            Endpoint endpoint = configuration.getEndpoint(endpointName);
            if (endpoint != null) {
                AspectConfiguration aspectConfiguration = ((AbstractEndpoint) endpoint).getDefinition().getAspectConfiguration();
                JSONObject info = new JSONObject();
                info.put(ENDPOINT_NAME, endpointName);
                GrpcUtils.handleTracing(performedBy, Constants.AUDIT_LOG_TYPE_ENDPOINT_TRACE,
                        Constants.ENDPOINTS, info, aspectConfiguration, endpointName, traceState);
            } else {
                GrpcUtils.createProtoError("Specified endpoint ('" + endpointName + "') not found");
            }
        } else {
            GrpcUtils.createProtoError("Unsupported operation");
        }
        //Utils.setJsonPayLoad(axisMsgCtx, response);
        //return GRPCUtils.handleTracing();
    }

    public static org.wso2.micro.integrator.grpc.proto.EndpointList populateEndpointList() {

        Map<String, Endpoint> namedEndpointMap = SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME).getDefinedEndpoints();
        Collection<Endpoint> namedEndpointCollection = namedEndpointMap.values();
        return setGrpcResponseBody(namedEndpointCollection);
    }

    private static org.wso2.micro.integrator.grpc.proto.EndpointList setGrpcResponseBody(Collection<Endpoint> namedEndpointCollection) {

        org.wso2.micro.integrator.grpc.proto.EndpointList.Builder endpointListBuilder =
                org.wso2.micro.integrator.grpc.proto.EndpointList.newBuilder().setCount(namedEndpointCollection.size());
        for (Endpoint ep : namedEndpointCollection) {

            org.wso2.micro.integrator.grpc.proto.EndpointSummary.Builder endpointSummaryBuilder =
                    org.wso2.micro.integrator.grpc.proto.EndpointSummary.newBuilder().setName(ep.getName());
            OMElement element = EndpointSerializer.getElementFromEndpoint(ep);
            OMElement firstElement = element.getFirstElement();
            String type;
            // For template endpoints the endpoint type can not be retrieved from firstElement
            if (firstElement == null) {
                type = element.getAttribute(new QName("template")).getLocalName();
            } else {
                type = firstElement.getLocalName();
            }
            endpointSummaryBuilder.setType(type);
            endpointSummaryBuilder.setIsActive(isEndpointActive(ep));
            endpointListBuilder.addEndPointSummaries(endpointSummaryBuilder.build());
        }
        return endpointListBuilder.build();
        //Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
    }
    public static org.wso2.micro.integrator.grpc.proto.Endpoint populateEndpointData(String endpointName) {

        org.wso2.micro.integrator.grpc.proto.Endpoint protoEndpoint = getEndpointByName(endpointName);
        return protoEndpoint;
        /*
        Have not handled the case where protoEndpoint is null. Need to handle it.
        if (Objects.nonNull(protoEndpoint)) {
            Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
        } else {
            axis2MessageContext.setProperty(Constants.HTTP_STATUS_CODE, Constants.NOT_FOUND);
        }
        */
    }

    private static org.wso2.micro.integrator.grpc.proto.Endpoint getEndpointByName(String endpointName) {

        SynapseConfiguration configuration = SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME);
        Endpoint ep = configuration.getEndpoint(endpointName);
        if (Objects.nonNull(ep)) {
            return getEndpointAsJson(ep);
        } else {
            return null;
        }
    }

    private static org.wso2.micro.integrator.grpc.proto.Endpoint getEndpointAsJson(Endpoint endpoint) {

        org.wso2.micro.integrator.grpc.proto.Endpoint.Builder endpointBuilder =
                org.wso2.micro.integrator.grpc.proto.Endpoint.newBuilder();

        JSONObject endpointObject = endpoint.getJsonRepresentation();
        endpointBuilder.setName(endpointObject.getString(Constants.NAME));
        endpointBuilder.setType(endpointObject.getString(Constants.TYPE));
        endpointBuilder.setMethod(endpointObject.getString(Constants.METHOD));
        endpointBuilder.setUriTemplate(endpointObject.getString("uriTemplate"));
        org.wso2.micro.integrator.grpc.proto.TimeoutState.Builder timeoutStateBuilder =
                org.wso2.micro.integrator.grpc.proto.TimeoutState.newBuilder().addAllErrorCodes((List<Integer>)(endpointObject.getJSONObject("timeoutState").get("errorCodes"))).setRetries((Integer)(endpointObject.getJSONObject("timeoutState").get("reties")));
        org.wso2.micro.integrator.grpc.proto.SuspendState.Builder suspendStateBuilder =
                org.wso2.micro.integrator.grpc.proto.SuspendState.newBuilder().addAllErrorCodes((List<Integer>)(endpointObject.getJSONObject("suspendState").get("errorCodes"))).setMaxDuration((Long)(endpointObject.getJSONObject("suspendState").get("maxDuration"))).setInitialDuration((Long)(endpointObject.getJSONObject("suspendState").get("initialDuration")));

        endpointBuilder.setEpAdvanced(org.wso2.micro.integrator.grpc.proto.EPAdvanced.newBuilder().setTimeoutState(timeoutStateBuilder.build()).setSuspendState(suspendStateBuilder.build()));
        OMElement synapseConfiguration = EndpointSerializer.getElementFromEndpoint(endpoint);
        endpointBuilder.setConfiguration(synapseConfiguration.toString()).setIsActive(isEndpointActive(endpoint)).setTracing(((AbstractEndpoint) endpoint).getDefinition().getAspectConfiguration().isTracingEnabled() ? Constants.ENABLED : Constants.DISABLED);

        return endpointBuilder.build();
    }

    private void changeEndpointStatus(String performedBy, SynapseConfiguration configuration, String endpointName, String status) {

        Endpoint ep = configuration.getEndpoint(endpointName);
        if (ep != null) {
            JSONObject info = new JSONObject();
            info.put(ENDPOINT_NAME, endpointName);
            if (INACTIVE_STATUS.equalsIgnoreCase(status)) {
                ep.getContext().switchOff();
//                jsonResponse.put(Constants.MESSAGE_JSON_ATTRIBUTE, endpointName + " is switched Off");
                AuditLogger.logAuditMessage(performedBy, Constants.AUDIT_LOG_TYPE_ENDPOINT,
                        Constants.AUDIT_LOG_ACTION_DISABLED, info);
            } else if (ACTIVE_STATUS.equalsIgnoreCase(status)) {
                ep.getContext().switchOn();
//                jsonResponse.put(Constants.MESSAGE_JSON_ATTRIBUTE, endpointName + " is switched On");
                AuditLogger.logAuditMessage(performedBy, Constants.AUDIT_LOG_TYPE_ENDPOINT,
                        Constants.AUDIT_LOG_ACTION_ENABLE, info);
            } else {
                GrpcUtils.createProtoError("Provided state is not valid");
            }
            //Utils.setJsonPayLoad(axis2MessageContext, jsonResponse);
        } else {
//            Utils.setJsonPayLoad(axis2MessageContext,  Utils.createJsonError("Endpoint does not exist",
//                    axis2MessageContext, Constants.NOT_FOUND));
        }
    }

    private static Boolean isEndpointActive(Endpoint endpoint) {
        // 1 represents the endpoint active state
        return endpoint.getContext().isState(1);
    }
}
