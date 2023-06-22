package org.wso2.micro.integrator.initializer.dashboard.grpcClient;

import org.apache.axis2.AxisFault;
import org.apache.axis2.description.AxisService;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.config.SynapseConfigUtils;
import org.apache.synapse.config.SynapseConfiguration;
import org.wso2.micro.integrator.dataservices.common.DBConstants;
import org.wso2.micro.integrator.dataservices.core.DBUtils;
import org.wso2.micro.integrator.dataservices.core.description.config.Config;
import org.wso2.micro.integrator.dataservices.core.description.operation.Operation;
import org.wso2.micro.integrator.dataservices.core.description.query.Query;
import org.wso2.micro.integrator.dataservices.core.description.resource.Resource;
import org.wso2.micro.integrator.dataservices.core.engine.DataService;
import org.wso2.micro.integrator.dataservices.core.engine.DataServiceSerializer;
import org.wso2.micro.integrator.dataservices.core.engine.ParamValue;
import org.wso2.micro.integrator.dataservices.core.engine.QueryParam;
import org.wso2.micro.integrator.dataservices.core.validation.Validator;
import org.wso2.micro.integrator.dataservices.core.validation.standard.*;
import org.wso2.micro.integrator.grpc.proto.DataServiceList;
import org.wso2.micro.service.mgt.ServiceMetaData;

import java.util.*;
import java.util.stream.Collectors;

import static org.wso2.carbon.inbound.endpoint.common.Constants.SUPER_TENANT_DOMAIN_NAME;

public class DataServiceResourceGrpc {

    private static final Log log = LogFactory.getLog(DataServiceResourceGrpc.class);

    private static List<String> getSearchResults(String searchKey) throws AxisFault {
        SynapseConfiguration configuration = SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME);
        AxisConfiguration axisConfiguration = configuration.getAxisConfiguration();
        return Arrays.stream(DBUtils.getAvailableDS(axisConfiguration))
                .filter(serviceName -> serviceName.toLowerCase().contains(searchKey)).collect(Collectors.toList());
    }

    public static org.wso2.micro.integrator.grpc.proto.DataServiceList populateSearchResults(String searchKey) {
        List<String> resultsList = null;
        try {
            resultsList = getSearchResults(searchKey);
            return setGrpcResponseBody(resultsList);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static org.wso2.micro.integrator.grpc.proto.DataServiceList setGrpcResponseBody(List<String> dataServicesNames) throws Exception {

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
         return dataServicesListBuilder.build();
        //Utils.setJsonPayLoad(axis2MessageContext, new JSONObject(stringPayload));
    }
    public static org.wso2.micro.integrator.grpc.proto.DataServiceList populateDataServiceList() {
        SynapseConfiguration configuration = SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME);
        AxisConfiguration axisConfiguration = configuration.getAxisConfiguration();
        List<String> dataServicesNames = null;
        try {
            dataServicesNames = Arrays.stream(DBUtils.getAvailableDS(axisConfiguration)).collect(Collectors.toList());
            return setGrpcResponseBody(dataServicesNames);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static org.wso2.micro.integrator.grpc.proto.DataService populateDataServiceByName(String serviceName) {
        DataService dataService = getDataServiceByName(serviceName);
        org.wso2.micro.integrator.grpc.proto.DataService.Builder dataServiceBuilder = org.wso2.micro.integrator.grpc.proto.DataService.newBuilder();
        if (dataService != null) {
            ServiceMetaData serviceMetaData = getServiceMetaData(dataService);

            if (serviceMetaData != null) {
                dataServiceBuilder.setServiceName(serviceMetaData.getName()).setWsdl11(serviceMetaData.getWsdlURLs()[0]).setWsdl10(serviceMetaData.getWsdlURLs()[1]).setSwaggerUrl(serviceMetaData.getSwaggerUrl()).setServiceDescription(serviceMetaData.getDescription()).setServiceGroupName(serviceMetaData.getServiceGroupName());

                Map<String, Query> queries = dataService.getQueries();
                for (Map.Entry<String, Query> stringQuery : queries.entrySet()) {
                    org.wso2.micro.integrator.grpc.proto.Query.Builder queryBuilder = org.wso2.micro.integrator.grpc.proto.Query.newBuilder();
                    queryBuilder.setId(stringQuery.getKey()).setNamespace(stringQuery.getValue().getNamespace()).setDataSourceId(stringQuery.getValue().getConfigId());
                    dataServiceBuilder.addQueries(queryBuilder.build());
                }
            }
            dataServiceBuilder.addAllDsDataSources(getDataSources(dataService));
            dataServiceBuilder.addAllResources(getResources(dataService));
            dataServiceBuilder.addAllOperations(getOperations(dataService));
            dataServiceBuilder.setConfiguration(DataServiceSerializer.serializeDataService(dataService, true).toString());

            //Utils.setJsonPayLoad(axis2MessageContext, new JSONObject(stringPayload));
        }
        return dataServiceBuilder.build();
    }

    private static DataService getDataServiceByName(String serviceName) {
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

    private static ServiceMetaData getServiceMetaData(DataService dataService) {
        if (dataService != null) {
            try {
                return GrpcUtils.getServiceAdmin().getServiceData(dataService.getName());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            return null;
        }
    }

    private static List<org.wso2.micro.integrator.grpc.proto.DataServiceResource> getResources(DataService dataService) {

        Set<Resource.ResourceID> resourceIDS = dataService.getResourceIds();
        List<org.wso2.micro.integrator.grpc.proto.DataServiceResource> resourceList = new ArrayList<>();
        for (Resource.ResourceID id : resourceIDS) {
            org.wso2.micro.integrator.grpc.proto.DataServiceResource.Builder resourceInfoBuilder = org.wso2.micro.integrator.grpc.proto.DataServiceResource.newBuilder();
            Resource resource = dataService.getResource(id);
            resourceInfoBuilder.setResourcePath(id.getPath()).setResourceMethod(id.getMethod()).setResourceQuery(resource.getCallQuery().getQueryId()).addAllQueryParams(getQueryParams(resource.getCallQuery().getQuery().getQueryParams()));
            resourceList.add(resourceInfoBuilder.build());
        }
        return resourceList;
    }

    private static List<org.wso2.micro.integrator.grpc.proto.QueryParam> getQueryParams(List<QueryParam> queryParamList) {

        List<org.wso2.micro.integrator.grpc.proto.QueryParam> queryParams = new ArrayList<>();
        for (QueryParam queryParam : queryParamList) {
            org.wso2.micro.integrator.grpc.proto.QueryParam.Builder queryParamBuilder = org.wso2.micro.integrator.grpc.proto.QueryParam.newBuilder();
            queryParamBuilder.setName(queryParam.getName())
                    .setSqlType(queryParam.getSqlType())
                    .setType(queryParam.getType())
                    .setParamType(queryParam.getParamType())
                    .addAllOrdinals(queryParam.getOrdinals())
                    .addAllValidators(getValidators(queryParam.getValidators()))
                    .setStructType(queryParam.getStructType())
                    .setForceDefault(queryParam.isForceDefault())
                    .setOptional(queryParam.isOptional())
                    .setValueType(queryParam.getDefaultValue().getValueType())
                    .setScalarValue(queryParam.getDefaultValue().getScalarValue())
                    //.setUdt(queryParam.getDefaultValue().getUdt())
                    .addAllArrayValues(getParamValues(queryParam.getDefaultValue()));
            queryParams.add(queryParamBuilder.build());
        }
        return queryParams;
    }

    private static List<org.wso2.micro.integrator.grpc.proto.ParamValue> getParamValues(ParamValue defaultValue) {

        List<org.wso2.micro.integrator.grpc.proto.ParamValue> paramValueList = new ArrayList<>();
        for (ParamValue paramValue : defaultValue.getArrayValue()) {
            org.wso2.micro.integrator.grpc.proto.ParamValue.Builder paramValueBuilder = org.wso2.micro.integrator.grpc.proto.ParamValue.newBuilder();
            paramValueBuilder.setValueType(paramValue.getValueType()).setScalarValue(paramValue.getScalarValue());
                    //.setUdt(paramValue.getUdt())
            paramValueList.add(paramValueBuilder.build());
        }
        return paramValueList;
    }

    private static List<org.wso2.micro.integrator.grpc.proto.Validator> getValidators(List<Validator> validators) {

        List<org.wso2.micro.integrator.grpc.proto.Validator> validatorList = new ArrayList<>();
        for (Validator validator : validators) {
            org.wso2.micro.integrator.grpc.proto.Validator.Builder validatorBuilder = org.wso2.micro.integrator.grpc.proto.Validator.newBuilder();
            if(validator instanceof ArrayTypeValidator){
                validatorBuilder.setArrayTypeValidator(org.wso2.micro.integrator.grpc.proto.ArrayTypeValidator.newBuilder().build());
            }else if(validator instanceof DoubleRangeValidator){
                validatorBuilder.setDoubleRangeValidator(org.wso2.micro.integrator.grpc.proto.DoubleRangeValidator.newBuilder().setMinimum(((DoubleRangeValidator) validator).getMinimum()).setMaximum(((DoubleRangeValidator) validator).getMaximum()).setHasMin(((DoubleRangeValidator) validator).isHasMin()).setHasMax(((DoubleRangeValidator) validator).isHasMax()).setMessage(((DoubleRangeValidator) validator).getMessage()).build());
            }else if(validator instanceof LengthValidator){
                validatorBuilder.setLengthValidator(org.wso2.micro.integrator.grpc.proto.LengthValidator.newBuilder().setMinimum(((LengthValidator) validator).getMinLength()).setMaximum(((LengthValidator) validator).getMaxLength()).setHasMin(((LengthValidator) validator).isHasMin()).setHasMax(((LengthValidator) validator).isHasMax()).setMessage(((LengthValidator) validator).getMessage()).build());
            }else if(validator instanceof LongRangeValidator){
                validatorBuilder.setLongRangeValidator(org.wso2.micro.integrator.grpc.proto.LongRangeValidator.newBuilder().setMinimum(((LongRangeValidator) validator).getMinimum()).setMaximum(((LongRangeValidator) validator).getMaximum()).setHasMin(((LongRangeValidator) validator).isHasMin()).setHasMax(((LongRangeValidator) validator).isHasMax()).setMessage(((LongRangeValidator) validator).getMessage()).build());
            } else if(validator instanceof PatternValidator){
                validatorBuilder.setPatternValidator(org.wso2.micro.integrator.grpc.proto.PatternValidator.newBuilder().setMessage(((PatternValidator) validator).getMessage()).setPattern(((PatternValidator) validator).getPattern().toString()).build());
            } else if(validator instanceof ScalarTypeValidator){
                validatorBuilder.setScalarTypeValidator(org.wso2.micro.integrator.grpc.proto.ScalarTypeValidator.newBuilder().build());
            }
            validatorList.add(validatorBuilder.build());
        }
        return validatorList;
    }

    private static List<org.wso2.micro.integrator.grpc.proto.Operation> getOperations(DataService dataService) {

        List<org.wso2.micro.integrator.grpc.proto.Operation> opertionList = new ArrayList<>();
        Set<String> operationNames = dataService.getOperationNames();
        for (String operationName : operationNames) {
            org.wso2.micro.integrator.grpc.proto.Operation.Builder operationBuilder = org.wso2.micro.integrator.grpc.proto.Operation.newBuilder();
            Operation operation = dataService.getOperation(operationName);
            operationBuilder.setOperationName(operationName);
            operationBuilder.setQueryName(operation.getCallQuery().getQueryId());
            operationBuilder.addAllQueryParams(getQueryParams(operation.getCallQuery().getQuery().getQueryParams()));
            opertionList.add(operationBuilder.build());
        }
        return opertionList;
    }

    private static List<org.wso2.micro.integrator.grpc.proto.DSDataSource> getDataSources(DataService dataService) {

        Map<String, Config> configs = dataService.getConfigs();
        List<org.wso2.micro.integrator.grpc.proto.DSDataSource> dataSources = new ArrayList<>();
        configs.forEach((name, config) -> {
            org.wso2.micro.integrator.grpc.proto.DSDataSource.Builder dataSourceBuilder = org.wso2.micro.integrator.grpc.proto.DSDataSource.newBuilder();
            dataSourceBuilder.setDataSourceId(config.getConfigId()).setDataSourceType(config.getType()).putAllDataSourceProperties(config.getProperties());
            dataSources.add(dataSourceBuilder.build());
        });
        return dataSources;
    }
}