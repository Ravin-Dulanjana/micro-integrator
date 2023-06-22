package org.wso2.micro.integrator.initializer.dashboard.grpcClient;

import org.apache.axiom.om.OMElement;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.ServerConfigurationInformation;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.config.SynapseConfigUtils;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.config.xml.ProxyServiceSerializer;
import org.apache.synapse.core.axis2.ProxyService;
import org.json.JSONObject;
import org.wso2.micro.core.util.AuditLogger;
import org.wso2.micro.service.mgt.ServiceAdmin;
import org.wso2.micro.service.mgt.ServiceMetaData;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.wso2.carbon.inbound.endpoint.common.Constants.SUPER_TENANT_DOMAIN_NAME;
import static org.wso2.micro.integrator.initializer.dashboard.grpcClient.Constants.ACTIVE_STATUS;
import static org.wso2.micro.integrator.initializer.dashboard.grpcClient.Constants.INACTIVE_STATUS;

public class ProxyServiceResourceGrpc {

    private static Log LOG = LogFactory.getLog(ProxyServiceResourceGrpc.class);
    private static final String PROXY_NAME = "proxyName";
    private static ServiceAdmin serviceAdmin = null;

    private static List<ProxyService> getSearchResults(String searchKey) {
        SynapseConfiguration configuration = SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME);
        return configuration.getProxyServices().stream()
                .filter(artifact -> artifact.getName().toLowerCase().contains(searchKey))
                .collect(Collectors.toList());
    }

    public static org.wso2.micro.integrator.grpc.proto.ProxyServiceList populateSearchResults(String searchKey) {
        List<ProxyService> searchResultList = getSearchResults(searchKey);
        return setGrpcResponseBody(searchResultList);
    }

    public static org.wso2.micro.integrator.grpc.proto.ProxyServiceList setGrpcResponseBody(Collection<ProxyService> proxyServices) {
        org.wso2.micro.integrator.grpc.proto.ProxyServiceList.Builder proxyServiceListBuilder =
                org.wso2.micro.integrator.grpc.proto.ProxyServiceList.newBuilder().setCount(proxyServices.size());
        for (ProxyService proxyService : proxyServices) {
            org.wso2.micro.integrator.grpc.proto.ProxyServiceSummary.Builder proxyServiceSummaryBuilder =
                    org.wso2.micro.integrator.grpc.proto.ProxyServiceSummary.newBuilder();
            try {
                ServiceMetaData data = serviceAdmin.getServiceData(proxyService.getName());
                proxyServiceSummaryBuilder.setName(proxyService.getName());
                String[] wsdlUrls = data.getWsdlURLs();
                proxyServiceSummaryBuilder.setWsdl11(wsdlUrls[0]);
                proxyServiceSummaryBuilder.setWsdl20(wsdlUrls[1]);
            } catch (Exception e) {
                LOG.error("Error occurred while processing service data", e);
            }
            proxyServiceListBuilder.addProxyServiceSummaries(proxyServiceSummaryBuilder.build());
        }
        return proxyServiceListBuilder.build();
    }

    private void handleTracing(String performedBy, String proxyName, String traceState){

        if (proxyName != null) {
            SynapseConfiguration configuration = SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME);
            ProxyService proxyService = configuration.getProxyService(proxyName);
            if (proxyService != null) {
                JSONObject info = new JSONObject();
                info.put(PROXY_NAME, proxyName);
                GrpcUtils.handleTracing(performedBy, Constants.AUDIT_LOG_TYPE_PROXY_SERVICE_TRACE,
                        Constants.PROXY_SERVICES, info, proxyService.getAspectConfiguration(),
                        proxyName, traceState);
            } else {
                GrpcUtils.createProtoError("Specified proxy ('" + proxyName + "') not found");
            }
        } else {
            GrpcUtils.createProtoError("Unsupported operation");
        }
        //Utils.setJsonPayLoad(axisMsgCtx, response);
        //return GRPCUtils.handleTracing();
    }

    public static org.wso2.micro.integrator.grpc.proto.ProxyServiceList populateProxyServiceList() {

        SynapseConfiguration configuration = SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME);
        Collection<ProxyService> proxyServices = configuration.getProxyServices();
        return setGrpcResponseBody(proxyServices);
    }

    public static org.wso2.micro.integrator.grpc.proto.ProxyService populateProxyServiceData(String proxyServiceName) {

        org.wso2.micro.integrator.grpc.proto.ProxyService proxyServicePB = getProxyServiceByName(proxyServiceName);
        return proxyServicePB;
        /*
        Have not handled the case where proxyServicePB is null. Need to handle it.
        if (Objects.nonNull(proxyServicePB)) {
            //Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
        } else {
            //axis2MessageContext.setProperty(Constants.HTTP_STATUS_CODE, Constants.NOT_FOUND);
        }
        */
    }

    private static org.wso2.micro.integrator.grpc.proto.ProxyService getProxyServiceByName(String proxyServiceName) {

        SynapseConfiguration configuration = SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME);
        ProxyService proxyService = configuration.getProxyService(proxyServiceName);
        return convertProxyServiceToProtoBuf(proxyService);
    }

    private static org.wso2.micro.integrator.grpc.proto.ProxyService convertProxyServiceToProtoBuf(ProxyService proxyService) {

        if (Objects.isNull(proxyService)) {
            return null;
        }
        org.wso2.micro.integrator.grpc.proto.ProxyService.Builder proxyServiceBuilder =
                org.wso2.micro.integrator.grpc.proto.ProxyService.newBuilder();
        proxyServiceBuilder.setName(proxyService.getName());

        try {

            ServiceMetaData data = serviceAdmin.getServiceData(proxyService.getName());

            String[] wsdlUrls = data.getWsdlURLs();

            proxyServiceBuilder.setWsdl11(wsdlUrls[0]);
            proxyServiceBuilder.setWsdl20(wsdlUrls[1]);
        } catch (Exception e) {
            LOG.error("Error occurred while processing service data", e);
        }

        String statisticState = proxyService.getAspectConfiguration().isStatisticsEnable() ? Constants.ENABLED : Constants.DISABLED;
        proxyServiceBuilder.setStats(statisticState);

        String tracingState = proxyService.getAspectConfiguration().isTracingEnabled() ? Constants.ENABLED : Constants.DISABLED;
        proxyServiceBuilder.setTracing(tracingState);

        OMElement proxyConfiguration = ProxyServiceSerializer.serializeProxy(null, proxyService);
        proxyServiceBuilder.setConfiguration(proxyConfiguration.toString());
        proxyServiceBuilder.addAllEprs(Arrays.asList(proxyService.getAxisService().getEPRs()));
        proxyServiceBuilder.setIsRunning(proxyService.isRunning());
        return proxyServiceBuilder.build();
    }

    private void changeProxyState(String performedBy, JSONObject info, String name, String status) {

        SynapseConfiguration synapseConfiguration = SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME);
        ProxyService proxyService = synapseConfiguration.getProxyService(name);
        if (proxyService == null) {
            GrpcUtils.createProtoError("Proxy service could not be found");
            return;
        }
        List pinnedServers = proxyService.getPinnedServers();
        org.wso2.micro.integrator.grpc.proto.Message message = null;
        if (ACTIVE_STATUS.equalsIgnoreCase(status)) {
            if (pinnedServers.isEmpty() ||
                    pinnedServers.contains(getServerConfigInformation(synapseConfiguration).getServerName())) {
                proxyService.start(synapseConfiguration);
                message = org.wso2.micro.integrator.grpc.proto.Message.newBuilder().setMessage("Proxy service " + name + " started successfully").build();
                //Utils.setJsonPayLoad(axis2MessageContext, jsonResponse);
                AuditLogger.logAuditMessage(performedBy, Constants.AUDIT_LOG_TYPE_PROXY_SERVICE,
                        Constants.AUDIT_LOG_ACTION_ENABLE, info);
            }
        } else if (INACTIVE_STATUS.equalsIgnoreCase(status)) {
            if (pinnedServers.isEmpty() ||
                    pinnedServers.contains(getServerConfigInformation(synapseConfiguration).getSynapseXMLLocation())) {
                proxyService.stop(synapseConfiguration);
                message = org.wso2.micro.integrator.grpc.proto.Message.newBuilder().setMessage("Proxy service " + name + " stopped successfully").build();
                //Utils.setJsonPayLoad(axis2MessageContext, jsonResponse);

                AuditLogger.logAuditMessage(performedBy, Constants.AUDIT_LOG_TYPE_PROXY_SERVICE,
                        Constants.AUDIT_LOG_ACTION_DISABLED, info);
            }
        } else {
                    GrpcUtils.createProtoError("Provided state is not valid");
        }
    }

    private ServerConfigurationInformation getServerConfigInformation(SynapseConfiguration synapseConfiguration) {

        return (ServerConfigurationInformation) synapseConfiguration.getAxisConfiguration().
                getParameter(SynapseConstants.SYNAPSE_SERVER_CONFIG_INFO).getValue();
    }
}
