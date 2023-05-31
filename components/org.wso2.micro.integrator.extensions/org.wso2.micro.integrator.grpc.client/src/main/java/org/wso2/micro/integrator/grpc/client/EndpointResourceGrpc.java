package org.wso2.micro.integrator.grpc.client;

import com.google.gson.JsonObject;
import org.apache.axiom.om.OMElement;
import org.apache.synapse.MessageContext;
import org.apache.synapse.aspects.AspectConfiguration;
import org.apache.synapse.config.SynapseConfigUtils;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.config.xml.endpoints.EndpointSerializer;
import org.apache.synapse.core.axis2.Axis2MessageContext;
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

public class EndpointResourceGrpc {
    private List<Endpoint> getSearchResults(String searchKey) {
        return SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME).getDefinedEndpoints().values().stream()
                .filter(artifact -> artifact.getName().toLowerCase().contains(searchKey))
                .collect(Collectors.toList());
    }

    private void populateSearchResults(String searchKey) {
        List<Endpoint> searchResultList = getSearchResults(searchKey);
        setGrpcResponseBody(searchResultList);
    }

    private void handleTracing(String performedBy, JsonObject payload, MessageContext msgCtx,
                               org.apache.axis2.context.MessageContext axisMsgCtx) {

        JSONObject response;
        if (payload.has(Constants.NAME)) {
            String endpointName = payload.get(Constants.NAME).getAsString();
            SynapseConfiguration configuration = msgCtx.getConfiguration();
            Endpoint endpoint = configuration.getEndpoint(endpointName);
            if (endpoint != null) {
                AspectConfiguration aspectConfiguration = ((AbstractEndpoint) endpoint).getDefinition().getAspectConfiguration();
                JSONObject info = new JSONObject();
                info.put(ENDPOINT_NAME, endpointName);
                response = Utils.handleTracing(performedBy, Constants.AUDIT_LOG_TYPE_ENDPOINT_TRACE,
                        Constants.ENDPOINTS, info, aspectConfiguration, endpointName,
                        axisMsgCtx);
            } else {
                response = Utils.createJsonError("Specified endpoint ('" + endpointName + "') not found", axisMsgCtx,
                        Constants.BAD_REQUEST);
            }
        } else {
            response = Utils.createJsonError("Unsupported operation", axisMsgCtx, Constants.BAD_REQUEST);
        }
        Utils.setJsonPayLoad(axisMsgCtx, response);
    }

    private void populateEndpointList(SynapseConfiguration configuration) {

        Map<String, Endpoint> namedEndpointMap = configuration.getDefinedEndpoints();
        Collection<Endpoint> namedEndpointCollection = namedEndpointMap.values();
        setGrpcResponseBody(namedEndpointCollection);
    }

    private void setGrpcResponseBody(Collection<Endpoint> namedEndpointCollection) {

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
        //Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
    }
    private void populateEndpointData(String endpointName) {

        JSONObject jsonBody = getEndpointByName(endpointName);

        if (Objects.nonNull(jsonBody)) {
            //Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
        } else {
            //axis2MessageContext.setProperty(Constants.HTTP_STATUS_CODE, Constants.NOT_FOUND);
        }
    }

    private JSONObject getEndpointByName(String endpointName) {

        SynapseConfiguration configuration = SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME);
        Endpoint ep = configuration.getEndpoint(endpointName);
        if (Objects.nonNull(ep)) {
            return getEndpointAsJson(ep);
        } else {
            return null;
        }
    }

    private JSONObject getEndpointAsJson(Endpoint endpoint) {

        org.wso2.micro.integrator.grpc.proto.Endpoint.Builder endpointBuilder =
                org.wso2.micro.integrator.grpc.proto.Endpoint.newBuilder();
        JSONObject endpointObject = endpoint.getJsonRepresentation();
        OMElement synapseConfiguration = EndpointSerializer.getElementFromEndpoint(endpoint);
        endpointBuilder.setConfiguration(synapseConfiguration.toString()).setIsActive(isEndpointActive(endpoint)).setTracing(((AbstractEndpoint) endpoint).getDefinition().getAspectConfiguration().isTracingEnabled() ? Constants.ENABLED : Constants.DISABLED);


        return endpointObject;
    }

    private void changeEndpointStatus(String performedBy, org.apache.axis2.context.MessageContext axis2MessageContext,
                                      SynapseConfiguration configuration, JsonObject payload) {

        String endpointName = payload.get(Constants.NAME).getAsString();
        String status = payload.get(STATUS).getAsString();
        Endpoint ep = configuration.getEndpoint(endpointName);
        if (ep != null) {
            JSONObject jsonResponse = new JSONObject();
            JSONObject info = new JSONObject();
            info.put(ENDPOINT_NAME, endpointName);
            if (INACTIVE_STATUS.equalsIgnoreCase(status)) {
                ep.getContext().switchOff();
                jsonResponse.put(Constants.MESSAGE_JSON_ATTRIBUTE, endpointName + " is switched Off");
                AuditLogger.logAuditMessage(performedBy, Constants.AUDIT_LOG_TYPE_ENDPOINT,
                        Constants.AUDIT_LOG_ACTION_DISABLED, info);
            } else if (ACTIVE_STATUS.equalsIgnoreCase(status)) {
                ep.getContext().switchOn();
                jsonResponse.put(Constants.MESSAGE_JSON_ATTRIBUTE, endpointName + " is switched On");
                AuditLogger.logAuditMessage(performedBy, Constants.AUDIT_LOG_TYPE_ENDPOINT,
                        Constants.AUDIT_LOG_ACTION_ENABLE, info);
            } else {
                jsonResponse = Utils.createJsonError("Provided state is not valid", axis2MessageContext, Constants.BAD_REQUEST);
            }
            Utils.setJsonPayLoad(axis2MessageContext, jsonResponse);
        } else {
            Utils.setJsonPayLoad(axis2MessageContext,  Utils.createJsonError("Endpoint does not exist",
                    axis2MessageContext, Constants.NOT_FOUND));
        }
    }

    private Boolean isEndpointActive(Endpoint endpoint) {
        // 1 represents the endpoint active state
        return endpoint.getContext().isState(1);
    }
}
