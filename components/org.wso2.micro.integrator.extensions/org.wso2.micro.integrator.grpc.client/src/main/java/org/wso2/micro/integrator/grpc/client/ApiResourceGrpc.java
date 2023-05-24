package org.wso2.micro.integrator.grpc.client;

import org.apache.axiom.om.OMElement;
import org.apache.axis2.description.Parameter;
import org.apache.axis2.description.TransportInDescription;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.synapse.api.API;
import org.apache.synapse.config.xml.rest.APISerializer;
import org.wso2.micro.integrator.grpc.proto.APIList;
import org.wso2.micro.integrator.grpc.proto.APISummary;
import org.apache.synapse.api.Resource;
import org.apache.synapse.MessageContext;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.api.dispatch.DispatcherHelper;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.api.dispatch.URITemplateHelper;
import org.wso2.micro.core.util.NetworkUtils;
import org.apache.synapse.api.dispatch.URLMappingHelper;

import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

public class ApiResourceGrpc {

    private static final String URL_VERSION_TYPE = "url";
    private String serverContext = "";  // base server url

    public void setGrpcResponseBody(Collection<API> resultList, MessageContext messageContext) {

        org.apache.axis2.context.MessageContext axis2MessageContext =
                ((Axis2MessageContext) messageContext).getAxis2MessageContext();
        APIList.Builder apiListBuilder = APIList.newBuilder();
        apiListBuilder.setCount((Integer) resultList.size());
        for (API api: resultList) {
            APISummary.Builder apiSummaryBuilder = APISummary.newBuilder();
            String apiUrl = getApiUrl(api, messageContext);
            apiSummaryBuilder.setName(api.getName());
            apiSummaryBuilder.setUrl(apiUrl);
            apiSummaryBuilder.setTracing(api.getAspectConfiguration().isTracingEnabled() ? Constants.ENABLED :
                    Constants.DISABLED);
            apiListBuilder.addApiSummaries(apiSummaryBuilder.build());
        }
        APIList apiList = apiListBuilder.build();
        //Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
    }

    public void populateGrpcApiData(MessageContext messageContext, String apiName) {

        org.apache.axis2.context.MessageContext axis2MessageContext =
                ((Axis2MessageContext) messageContext).getAxis2MessageContext();

        org.wso2.micro.integrator.grpc.proto.API apiProtoBuf = getGrpcApiByName(messageContext, apiName);

        if (Objects.nonNull(apiProtoBuf)) {
            //Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
        } else {
            axis2MessageContext.setProperty(Constants.HTTP_STATUS_CODE, Constants.NOT_FOUND);
        }
    }

    private org.wso2.micro.integrator.grpc.proto.API getGrpcApiByName(MessageContext messageContext, String apiName) {

        SynapseConfiguration configuration = messageContext.getConfiguration();
        API api = configuration.getAPI(apiName);
        return convertApiToProtoBuff(api, messageContext);
    }

    private org.wso2.micro.integrator.grpc.proto.API convertApiToProtoBuff(API api, MessageContext messageContext) {

        if (Objects.isNull(api)) {
            return null;
        }

        org.wso2.micro.integrator.grpc.proto.API.Builder apiBuilder = org.wso2.micro.integrator.grpc.proto.API.newBuilder();

        apiBuilder.setName(api.getName());
        String apiUrl = getApiUrl(api, messageContext);
        apiBuilder.setUrl(apiUrl);

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

    private String getApiUrl(API api, MessageContext msgCtx) {

        org.apache.axis2.context.MessageContext axisMsgCtx = ((Axis2MessageContext) msgCtx).getAxis2MessageContext();
        String serverUrl = getServerContext(axisMsgCtx.getConfigurationContext().getAxisConfiguration());
        String versionUrl = "";
        if (URL_VERSION_TYPE.equals(api.getVersionStrategy().getVersionType())) {
            versionUrl = "/" + api.getVersion();
        }
        return serverUrl.equals("err") ? api.getContext() : serverUrl + api.getContext() + versionUrl;
    }

    private String getServerContext(AxisConfiguration configuration) {

        if (!serverContext.isEmpty()) {
            return serverContext;
        }
        String portValue;
        String protocol;

        TransportInDescription transportInDescription = configuration.getTransportIn("http");
        if (Objects.isNull(transportInDescription)) {
            transportInDescription = configuration.getTransportIn("https");
        }

        if (Objects.nonNull(transportInDescription)) {
            protocol = transportInDescription.getName();
            portValue = (String) transportInDescription.getParameter("port").getValue();
        } else {
            return "err";
        }

        String host;

        Parameter hostParam =  configuration.getParameter("hostname");

        if (Objects.nonNull(hostParam)) {
            host = (String)hostParam.getValue();
        } else {
            try {
                host = NetworkUtils.getLocalHostname();
            } catch (SocketException e) {
                host = "localhost";
            }
        }
        String url;
        try {
            int port = Integer.parseInt(portValue);
            if (("http".equals(protocol) && port == 80) || ("https".equals(protocol) && port == 443)) {
                port = -1;
            }
            URL serverURL = new URL(protocol, host, port, "");
            url = serverURL.toExternalForm();
        } catch (MalformedURLException e) {
            url = "err";
        }
        this.serverContext = url;
        return url;
    }
}

