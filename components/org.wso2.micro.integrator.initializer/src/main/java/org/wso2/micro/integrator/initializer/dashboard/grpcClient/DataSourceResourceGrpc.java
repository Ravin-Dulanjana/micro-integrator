package org.wso2.micro.integrator.initializer.dashboard.grpcClient;

import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PoolConfiguration;
import org.w3c.dom.Element;
import org.wso2.micro.integrator.ndatasource.core.CarbonDataSource;
import org.wso2.micro.integrator.ndatasource.core.DataSourceMetaInfo;
import org.wso2.micro.integrator.ndatasource.core.DataSourceRepository;
import org.wso2.micro.integrator.ndatasource.core.utils.DataSourceUtils;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class DataSourceResourceGrpc {

    private List<CarbonDataSource> getSearchResults(DataSourceRepository dataSourceRepository, String searchKey) {
        Collection<CarbonDataSource> dataSources = dataSourceRepository.getAllDataSources();
        return dataSources.stream()
                .filter(artifact -> artifact.getDSMInfo().getName().toLowerCase().contains(searchKey))
                .collect(Collectors.toList());
    }

    private org.wso2.micro.integrator.grpc.proto.DataSourceList populateSearchResults(DataSourceRepository dataSourceRepository, String searchKey) {
        List<CarbonDataSource> resultsList = getSearchResults(dataSourceRepository, searchKey);
        return generateDatasourceList(resultsList);
    }

    private org.wso2.micro.integrator.grpc.proto.DataSourceList getDatasourceList(DataSourceRepository dataSourceRepository) {
        Collection<CarbonDataSource> datasources = dataSourceRepository.getAllDataSources();
        return generateDatasourceList(datasources);
    }

    private org.wso2.micro.integrator.grpc.proto.DataSourceList generateDatasourceList(Collection<CarbonDataSource> datasources) {
        org.wso2.micro.integrator.grpc.proto.DataSourceList.Builder dataSourceListBuilder =
                org.wso2.micro.integrator.grpc.proto.DataSourceList.newBuilder().setCount(datasources.size());
        for (CarbonDataSource dataSource : datasources) {
            org.wso2.micro.integrator.grpc.proto.DataSourceSummary.Builder dataSourceSummaryBuilder =
                    org.wso2.micro.integrator.grpc.proto.DataSourceSummary.newBuilder();
            dataSourceSummaryBuilder.setName(dataSource.getDSMInfo().getName());
            dataSourceSummaryBuilder.setType(dataSource.getDSMInfo().getDefinition().getType());
            dataSourceListBuilder.addDataSourcesSummaries(dataSourceSummaryBuilder.build());
        }
        return dataSourceListBuilder.build();
    }

    private Object getDatasourceInformation(org.apache.axis2.context.MessageContext axis2MessageContext,
                                                DataSourceRepository dataSourceRepository,
                                                String datasourceName) {
        Object dataSourceInformation = null;
        CarbonDataSource dataSource = dataSourceRepository.getDataSource(datasourceName);
        if (Objects.nonNull(dataSource)) {
            org.wso2.micro.integrator.grpc.proto.DataSource.Builder dataSourceBuilder =
                    org.wso2.micro.integrator.grpc.proto.DataSource.newBuilder();
            DataSourceMetaInfo dataSourceMetaInfo = dataSource.getDSMInfo();
            dataSourceBuilder.setName(dataSourceMetaInfo.getName());
            dataSourceBuilder.setDescription(dataSourceMetaInfo.getDescription());
            dataSourceBuilder.setType(dataSourceMetaInfo.getDefinition().getType());
            dataSourceBuilder.setConfiguration(DataSourceUtils.elementToStringWithMaskedPasswords(
                            (Element) dataSource.getDSMInfo().getDefinition().getDsXMLConfiguration()));

            if (dataSource.getDSObject() instanceof DataSource) {
                DataSource dataSourceObject = (DataSource) dataSource.getDSObject();
                PoolConfiguration pool = dataSourceObject.getPoolProperties();
                dataSourceBuilder.setDriverClass(pool.getDriverClassName());
                dataSourceBuilder.setUrl(DataSourceUtils.maskURLPassword(pool.getUrl()));
                dataSourceBuilder.setUsername(pool.getUsername());
                // set configuration parameters
                org.wso2.micro.integrator.grpc.proto.DSConfigurationParameters.Builder configParameters =
                        org.wso2.micro.integrator.grpc.proto.DSConfigurationParameters.newBuilder();
                configParameters.setDefaultAutoCommit(pool.isDefaultAutoCommit());
                configParameters.setDefaultReadOnly(pool.isDefaultReadOnly());
                configParameters.setRemoveAbandoned(pool.isRemoveAbandoned());
                configParameters.setValidationQuery(pool.getValidationQuery());
                configParameters.setValidationTimeout(pool.getValidationQueryTimeout());
                configParameters.setMaxActive(pool.getMaxActive());
                configParameters.setMaxIdle(pool.getMaxIdle());
                configParameters.setMaxWait(pool.getMaxWait());
                configParameters.setMaxAge(pool.getMaxAge());
                dataSourceBuilder.setDsConfigurationParameters(configParameters.build());
                dataSourceInformation = dataSourceBuilder.build();
            }
        } else {
            org.wso2.micro.integrator.grpc.proto.Error error = GrpcUtils.createProtoError("datasource " + datasourceName + " does not exist");
            dataSourceInformation = error;
        }
        return dataSourceInformation;
    }
}
