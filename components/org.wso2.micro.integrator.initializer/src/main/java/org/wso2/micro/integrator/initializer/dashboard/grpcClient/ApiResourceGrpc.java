package org.wso2.micro.integrator.initializer.dashboard.grpcClient;

import org.apache.axiom.om.OMElement;
import org.apache.synapse.api.API;
import org.apache.synapse.api.Resource;
import org.apache.synapse.api.dispatch.DispatcherHelper;
import org.apache.synapse.api.dispatch.URITemplateHelper;
import org.apache.synapse.api.dispatch.URLMappingHelper;
import org.apache.synapse.config.SynapseConfigUtils;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.config.xml.rest.APISerializer;
import org.wso2.micro.integrator.grpc.proto.APIList;
import org.wso2.micro.integrator.grpc.proto.APISummary;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.wso2.carbon.inbound.endpoint.common.Constants.SUPER_TENANT_DOMAIN_NAME;

public class ApiResourceGrpc {

    private static final String URL_VERSION_TYPE = "url";
    private String serverContext = "";  // base server url

    public static APIList populateSearchResults(String searchKey) {
        List<API> searchResultList = getSearchResults(searchKey);
        return setGrpcResponseBody(searchResultList);
    }
    private static List<API> getSearchResults(String searchKey) {
        SynapseConfiguration configuration = SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME);
        return configuration.getAPIs().stream()
                .filter(artifact -> artifact.getAPIName().toLowerCase().contains(searchKey))
                .collect(Collectors.toList());
    }

    public static APIList populateApiList() {

        SynapseConfiguration configuration = SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME);

        Collection<API> apis = configuration.getAPIs();

        return setGrpcResponseBody(apis);

    }

    private static APIList setGrpcResponseBody(Collection<API> resultList) {

        APIList.Builder apiListBuilder = APIList.newBuilder();
        apiListBuilder.setCount(resultList.size());
        for (API api: resultList) {
            APISummary.Builder apiSummaryBuilder = APISummary.newBuilder();
            apiSummaryBuilder.setName(api.getName());
            apiSummaryBuilder.setTracing(api.getAspectConfiguration().isTracingEnabled() ? Constants.ENABLED :
                    Constants.DISABLED);
            apiListBuilder.addApiSummaries(apiSummaryBuilder.build());
        }
        return apiListBuilder.build();
        //Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
    }

    public static org.wso2.micro.integrator.grpc.proto.API populateGrpcApiData(String apiName) {

        org.wso2.micro.integrator.grpc.proto.API apiProtoBuf = getGrpcApiByName(apiName);
        return apiProtoBuf;
        /*
        Have not handled the case where apiProtoBuf is null. Need to handle it.
        if (Objects.nonNull(apiProtoBuf)) {
            Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
        } else {
            axis2MessageContext.setProperty(Constants.HTTP_STATUS_CODE, Constants.NOT_FOUND);
        }
        */
    }

    private static org.wso2.micro.integrator.grpc.proto.API getGrpcApiByName(String apiName) {

        SynapseConfiguration configuration = SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME);
        API api = configuration.getAPI(apiName);
        return convertApiToProtoBuff(api);
    }

    private static org.wso2.micro.integrator.grpc.proto.API convertApiToProtoBuff(API api) {

        if (Objects.isNull(api)) {
            return null;
        }

        org.wso2.micro.integrator.grpc.proto.API.Builder apiBuilder = org.wso2.micro.integrator.grpc.proto.API.newBuilder();

        apiBuilder.setName(api.getName());

        String version = api.getVersion().equals("") ? "N/A" : api.getVersion();

        apiBuilder.setVersion(version);

        String statisticState = api.getAspectConfiguration().isStatisticsEnable() ? Constants.ENABLED : Constants.DISABLED;
        apiBuilder.setStats(statisticState);

        String tracingState = api.getAspectConfiguration().isTracingEnabled() ? Constants.ENABLED : Constants.DISABLED;
        apiBuilder.setTracing(tracingState);

        apiBuilder.setHost(api.getHost());
        apiBuilder.setPort(api.getPort());
        apiBuilder.setPort(api.getPort());

        OMElement apiConfiguration = APISerializer.serializeAPI(api);
        apiBuilder.setConfiguration(apiConfiguration.toString());
        Resource[] resources = api.getResources();

        for (Resource resource : resources) {

            org.wso2.micro.integrator.grpc.proto.Resource.Builder resourceBuilder = org.wso2.micro.integrator.grpc.proto.Resource.newBuilder();

            String[] methods = resource.getMethods();

            resourceBuilder.addAllMethods(Arrays.asList(methods));

            DispatcherHelper dispatcherHelper = resource.getDispatcherHelper();
            if (dispatcherHelper instanceof URITemplateHelper) {
                resourceBuilder.setUrl(dispatcherHelper.getString());

            } else if (dispatcherHelper instanceof URLMappingHelper) {
                resourceBuilder.setUrl(dispatcherHelper.getString());
            } else {
                resourceBuilder.setUrl("N/A");
            }
            apiBuilder.addResources(resourceBuilder.build());
        }
        return apiBuilder.build();
    }

}

