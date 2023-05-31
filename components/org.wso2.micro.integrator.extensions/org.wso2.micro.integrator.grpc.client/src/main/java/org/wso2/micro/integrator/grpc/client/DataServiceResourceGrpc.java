package org.wso2.micro.integrator.grpc.client;

import com.google.gson.Gson;
import org.apache.axis2.AxisFault;
import org.apache.axis2.description.AxisService;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.config.SynapseConfigUtils;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.tomcat.util.modeler.OperationInfo;
import org.json.JSONObject;
import org.wso2.micro.integrator.dataservices.common.DBConstants;
import org.wso2.micro.integrator.dataservices.core.DBUtils;
import org.wso2.micro.integrator.dataservices.core.description.config.Config;
import org.wso2.micro.integrator.dataservices.core.description.operation.Operation;
import org.wso2.micro.integrator.dataservices.core.description.query.Query;
import org.wso2.micro.integrator.dataservices.core.description.resource.Resource;
import org.wso2.micro.integrator.dataservices.core.engine.DataService;
import org.wso2.micro.integrator.dataservices.core.engine.DataServiceSerializer;
import org.wso2.micro.integrator.dataservices.core.engine.QueryParam;
import org.wso2.micro.integrator.grpc.proto.DataServiceList;
import org.wso2.micro.service.mgt.ServiceMetaData;

import java.util.*;
import java.util.stream.Collectors;

import static org.wso2.carbon.inbound.endpoint.common.Constants.SUPER_TENANT_DOMAIN_NAME;

public class DataServiceResourceGrpc {

    private static final Log log = LogFactory.getLog(DataServiceResourceGrpc.class);

    private List<String> getSearchResults(String searchKey) throws AxisFault {
        SynapseConfiguration configuration = SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME);
        AxisConfiguration axisConfiguration = configuration.getAxisConfiguration();
        return Arrays.stream(DBUtils.getAvailableDS(axisConfiguration))
                .filter(serviceName -> serviceName.toLowerCase().contains(searchKey)).collect(Collectors.toList());
    }

    private void populateSearchResults(String searchKey) throws Exception {
        List<String> resultsList = getSearchResults(searchKey);
        setGrpcResponseBody(resultsList);
    }

    private void setGrpcResponseBody(List<String> dataServicesNames) throws Exception {

        DataServiceList.Builder dataServicesListBuilder = DataServiceList.newBuilder().setCount(dataServicesNames.size());

        for (String dataServiceName : dataServicesNames) {
            DataService dataService = getDataServiceByName(dataServiceName);
            ServiceMetaData serviceMetaData = getServiceMetaData(dataService);
            org.wso2.micro.integrator.grpc.proto.DataServiceSummary.Builder dataServiceSummary = org.wso2.micro.integrator.grpc.proto.DataServiceSummary.newBuilder();
            if (serviceMetaData != null) {
                dataServiceSummary.setName(serviceMetaData.getName()).setWsdl11(serviceMetaData.getWsdlURLs()[0]).setWsdl10(serviceMetaData.getWsdlURLs()[1]);
            }
            dataServicesListBuilder.addDataServicesSummaries(dataServiceSummary.build());
        }
        org.wso2.micro.integrator.grpc.proto.DataServiceList dataServiceList = dataServicesListBuilder.build();
        //Utils.setJsonPayLoad(axis2MessageContext, new JSONObject(stringPayload));
    }
    private void populateDataServiceList() throws Exception {
        SynapseConfiguration configuration = SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME);
        AxisConfiguration axisConfiguration = configuration.getAxisConfiguration();
        List<String> dataServicesNames = Arrays.stream(DBUtils.getAvailableDS(axisConfiguration)).collect(Collectors.toList());
        setGrpcResponseBody(dataServicesNames);
    }

    private void populateDataServiceByName(String serviceName) {
        DataService dataService = getDataServiceByName(serviceName);

        if (dataService != null) {
            ServiceMetaData serviceMetaData = getServiceMetaData(dataService);
            org.wso2.micro.integrator.grpc.proto.DataService.Builder dataServiceBuilder = org.wso2.micro.integrator.grpc.proto.DataService.newBuilder();

            if (serviceMetaData != null) {
                dataServiceBuilder.setServiceName(serviceMetaData.getName()).setWsdl11(serviceMetaData.getWsdlURLs()[0]).setWsdl10(serviceMetaData.getWsdlURLs()[1]).setSwaggerUrl(serviceMetaData.getSwaggerUrl()).setServiceDescription(serviceMetaData.getDescription()).setServiceGroupName(serviceMetaData.getServiceGroupName());

                Map<String, Query> queries = dataService.getQueries();
                for (Map.Entry<String, Query> stringQuery : queries.entrySet()) {
                    org.wso2.micro.integrator.grpc.proto.Query.Builder queryBuilder = org.wso2.micro.integrator.grpc.proto.Query.newBuilder();
                    queryBuilder.setId(stringQuery.getKey()).setNamespace(stringQuery.getValue().getNamespace()).setConfigId(stringQuery.getValue().getConfigId());
                    dataServiceBuilder.addQueries(queryBuilder.build());
                }
            }
            dataServiceBuilder.addAllDataSources(getDataSources(dataService));
            dataServiceBuilder.addAllResources(getResources(dataService));
            dataServiceInfo.setResources(getResources(dataService));
            dataServiceInfo.setOperations(getOperations(dataService));
            dataServiceInfo.setConfiguration(DataServiceSerializer.serializeDataService(dataService, true).toString());
            String stringPayload = new Gson().toJson(dataServiceInfo);
            //Utils.setJsonPayLoad(axis2MessageContext, new JSONObject(stringPayload));
        }
    }

    private DataService getDataServiceByName(String serviceName) {
        AxisService axisService = SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME).
                getAxisConfiguration().getServiceForActivation(serviceName);
        DataService dataService = null;
        if (axisService != null) {
            dataService = (DataService) axisService.getParameter(DBConstants.DATA_SERVICE_OBJECT).getValue();
        } else {
            log.debug("DataService {} is null.");
        }
        return dataService;
    }

    private ServiceMetaData getServiceMetaData(DataService dataService) {
        if (dataService != null) {
            return serviceAdmin.getServiceData(dataService.getName());
        } else {
            return null;
        }
    }

    private List<org.wso2.micro.integrator.grpc.proto.DataServiceResource> getResources(DataService dataService) {

        Set<Resource.ResourceID> resourceIDS = dataService.getResourceIds();
        List<org.wso2.micro.integrator.grpc.proto.DataServiceResource> resourceList = new ArrayList<>();
        for (Resource.ResourceID id : resourceIDS) {
            org.wso2.micro.integrator.grpc.proto.DataServiceResource.Builder resourceInfoBuilder = org.wso2.micro.integrator.grpc.proto.DataServiceResource.newBuilder();
            Resource resource = dataService.getResource(id);
            resourceInfoBuilder.setResourcePath(id.getPath()).setResourceMethod(id.getMethod()).setResourceQuery(resource.getCallQuery().getQueryId()).addAllQueryParams(resource.getCallQuery().getQuery().getQueryParams());
            resourceList.add(resourceInfo);
        }
        return resourceList;
    }

    private List<org.wso2.micro.integrator.grpc.proto.QueryParam> getQueryParams(Query query) {

        List<org.wso2.micro.integrator.grpc.proto.QueryParam> queryParams = new ArrayList<>();
        List<QueryParam> queryParamList = resource.getCallQuery().getQuery().getQueryParams();
        for (QueryParam queryParam : queryParamList) {
            org.wso2.micro.integrator.grpc.proto.QueryParam.Builder queryParamBuilder = org.wso2.micro.integrator.grpc.proto.QueryParam.newBuilder();
            queryParamBuilder.setName(queryParam.getName())
                    .setSqlType(queryParam.getSqlType())
                    .setType(queryParam.getType())
                    .setParamType(queryParam.getParamType())
                    .setStructType(queryParam.getStructType())
                    .setDefaultValue(org.wso2.micro.integrator.grpc.proto.ParamValue.newBuilder().setValueType(queryParam.getDefaultValue().getValueType()).setScalarValue(queryParam.getDefaultValue().getScalarValue()).addAllArrayValue(queryParam.getDefaultValue().getArrayValue())).setOrdinal(queryParam.getOrdinal()).setOptional(queryParam.isOptional()).setArray(queryParam.isArray()).setElementName(queryParam.getElementName()).setNamespace(queryParam.getNamespace()).setMinOccurs(queryParam.getMinOccurs()).setMaxOccurs(queryParam.getMaxOccurs()).setNillable(queryParam.isNillable()).setDefaultValue(queryParam.getDefaultVa);
            queryParams.add(queryParamBuilder.build());
        }
        return queryParams;
    }

    private List<OperationInfo> getOperations(DataService dataService) {

        List<OperationInfo> opertionList = new ArrayList<>();
        Set<String> operationNames = dataService.getOperationNames();
        for (String operationName : operationNames) {
            OperationInfo operationInfo = new OperationInfo();
            Operation operation = dataService.getOperation(operationName);
            operationInfo.setOperationName(operationName);
            operationInfo.setQueryName(operation.getCallQuery().getQueryId());
            operationInfo.setQueryParams(operation.getCallQuery().getQuery().getQueryParams());
            opertionList.add(operationInfo);
        }
        return opertionList;
    }

    private List<org.wso2.micro.integrator.grpc.proto.DataSource> getDataSources(DataService dataService) {

        Map<String, Config> configs = dataService.getConfigs();
        List<org.wso2.micro.integrator.grpc.proto.DataSource> dataSources = new ArrayList<>();
        configs.forEach((name, config) -> {
            org.wso2.micro.integrator.grpc.proto.DataSource.Builder dataSourceBuilder = org.wso2.micro.integrator.grpc.proto.DataSource.newBuilder();
            dataSourceBuilder.setDataSourceId(config.getConfigId()).setDataSourceType(config.getType()).putAllDataSourceProperties(config.getProperties());
            dataSources.add(dataSourceBuilder.build());
        });
        return dataSources;
    }
}