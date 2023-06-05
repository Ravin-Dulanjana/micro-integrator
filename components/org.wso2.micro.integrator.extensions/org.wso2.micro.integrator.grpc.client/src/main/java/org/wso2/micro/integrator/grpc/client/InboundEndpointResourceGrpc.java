package org.wso2.micro.integrator.grpc.client;

import com.google.gson.JsonObject;
import org.apache.synapse.MessageContext;
import org.apache.synapse.config.SynapseConfigUtils;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.config.xml.inbound.InboundEndpointSerializer;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.inbound.InboundEndpoint;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.wso2.carbon.inbound.endpoint.common.Constants.SUPER_TENANT_DOMAIN_NAME;

public class InboundEndpointResourceGrpc {

    private static final String INBOUND_ENDPOINT_NAME = "inboundEndpointName";

    private List<InboundEndpoint> getSearchResults (String searchKey) {

        SynapseConfiguration configuration = SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME);
        return configuration.getInboundEndpoints().stream()
                .filter(artifact -> artifact.getName().toLowerCase().contains(searchKey))
                .collect(Collectors.toList());
    }

    private void populateSearchResults(String searchKey) {

        List<InboundEndpoint> resultsList = getSearchResults(searchKey);
        setResponseBody(resultsList);
    }

    private void populateInboundEndpointList(MessageContext messageContext) {

        SynapseConfiguration configuration = SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME);
        Collection<InboundEndpoint> inboundEndpoints = configuration.getInboundEndpoints();
        setResponseBody(inboundEndpoints);
    }

    private void setResponseBody(Collection<InboundEndpoint> inboundEndpointCollection) {

        org.wso2.micro.integrator.grpc.proto.InboundEndpointList.Builder inboundEndpointListBuilder =
                org.wso2.micro.integrator.grpc.proto.InboundEndpointList.newBuilder().setCount(inboundEndpointCollection.size());

        for (InboundEndpoint inboundEndpoint : inboundEndpointCollection) {
            org.wso2.micro.integrator.grpc.proto.InboundEndpointSummary.Builder inboundEndpointSummaryBuilder =
                    org.wso2.micro.integrator.grpc.proto.InboundEndpointSummary.newBuilder();
            inboundEndpointSummaryBuilder.setName(inboundEndpoint.getName());
            inboundEndpointSummaryBuilder.setProtocol(inboundEndpoint.getProtocol());

            inboundEndpointListBuilder.addInboundEndpointsSummaries(inboundEndpointSummaryBuilder.build());
        }
        // build the response and send
        //Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
    }

    private void populateInboundEndpointData(String inboundEndpointName) {

        org.wso2.micro.integrator.grpc.proto.InboundEndpoint inboundEndpoint = getInboundEndpointByName(inboundEndpointName);

        if (Objects.nonNull(inboundEndpoint)) {
            //Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
        } else {
            //axis2MessageContext.setProperty(Constants.HTTP_STATUS_CODE, Constants.NOT_FOUND);
        }
    }

    private org.wso2.micro.integrator.grpc.proto.InboundEndpoint getInboundEndpointByName(String inboundEndpointName) {

        SynapseConfiguration configuration = SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME);
        InboundEndpoint ep = configuration.getInboundEndpoint(inboundEndpointName);
        return convertInboundEndpointToProtoBuf(ep);
    }

    private org.wso2.micro.integrator.grpc.proto.InboundEndpoint convertInboundEndpointToProtoBuf(InboundEndpoint inboundEndpoint) {

        if (Objects.isNull(inboundEndpoint)) {
            return null;
        }

        org.wso2.micro.integrator.grpc.proto.InboundEndpoint.Builder inboundEndpointBuilder =
                org.wso2.micro.integrator.grpc.proto.InboundEndpoint.newBuilder();

        inboundEndpointBuilder.setName(inboundEndpoint.getName());
        inboundEndpointBuilder.setProtocol(inboundEndpoint.getProtocol());
        inboundEndpointBuilder.setSequence(inboundEndpoint.getInjectingSeq());
        inboundEndpointBuilder.setError(inboundEndpoint.getOnErrorSeq());
        inboundEndpointBuilder.setStatisticsState(inboundEndpoint.getAspectConfiguration().isStatisticsEnable() ? Constants.ENABLED : Constants.DISABLED);
        inboundEndpointBuilder.setTracing(inboundEndpoint.getAspectConfiguration().isTracingEnabled() ? Constants.ENABLED : Constants.DISABLED);
        inboundEndpointBuilder.setConfiguration(InboundEndpointSerializer.serializeInboundEndpoint(inboundEndpoint).toString());;

        Map<String, String> params = inboundEndpoint.getParametersMap();

        for (Map.Entry<String,String> param : params.entrySet()) {

            org.wso2.micro.integrator.grpc.proto.Param.Builder paramBuilder =
                    org.wso2.micro.integrator.grpc.proto.Param.newBuilder();
            paramBuilder.setName(param.getKey()).setValue(param.getValue());

            inboundEndpointBuilder.addParams(paramBuilder.build());
        }
        return inboundEndpointBuilder.build();
    }

}
