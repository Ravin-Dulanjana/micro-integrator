package org.wso2.micro.integrator.initializer.dashboard.grpcClient;

import org.apache.synapse.MessageContext;
import org.apache.synapse.config.SynapseConfigUtils;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.libraries.model.Library;
import org.apache.synapse.libraries.model.SynapseLibrary;
import org.wso2.micro.integrator.grpc.proto.ConnectorList;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.wso2.carbon.inbound.endpoint.common.Constants.SUPER_TENANT_DOMAIN_NAME;

public class ConnectorResourceGrpc {

    private static List<Library> getSearchResults(String searchKey){
        SynapseConfiguration configuration = SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME);
        return configuration.getSynapseLibraries().values().stream()
                .filter(artifact -> artifact.getFileName().toLowerCase().contains(searchKey))
                .collect(Collectors.toList());
    }

    public static org.wso2.micro.integrator.grpc.proto.ConnectorList populateSearchResults(String searchKey) {
        List<Library> searchResultList = getSearchResults(searchKey);
        return setGrpcResponseBody(searchResultList);
    }

    private static org.wso2.micro.integrator.grpc.proto.ConnectorList setGrpcResponseBody(Collection<Library> libraryList) {
        ConnectorList.Builder connectorListBuilder = ConnectorList.newBuilder();
        connectorListBuilder.setCount(libraryList.size());
        for (Library library : libraryList) {
            addToConnectorList(connectorListBuilder, (SynapseLibrary) library);
        }
        return connectorListBuilder.build();
        //Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
    }
    public static org.wso2.micro.integrator.grpc.proto.Connector populateConnectorData(String connectorName) {

        Map<String, Library> libraries = SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME).getSynapseLibraries();

        for (Map.Entry<String, Library> entry : libraries.entrySet()) {
            if (((SynapseLibrary)entry.getValue()).getName().equals(connectorName)) {
                SynapseLibrary connector = (SynapseLibrary)entry.getValue();
                if (Objects.nonNull(connector)) {
                   return getConnectorAsProtoBuff(connector);
                    //Utils.setJsonPayLoad(axis2MessageContext, getConnectorAsJson(connector));
                }
            }
        }
        return null;
    }

    private static org.wso2.micro.integrator.grpc.proto.Connector getConnectorAsProtoBuff(SynapseLibrary connector) {
        return org.wso2.micro.integrator.grpc.proto.Connector.newBuilder()
                .setName(connector.getName())
                .setPackage(connector.getPackage())
                .setDescription(connector.getDescription())
                .setStatus(getConnectorState(connector.getLibStatus()))
                .build();
    }

    public static org.wso2.micro.integrator.grpc.proto.ConnectorList populateConnectorList() {
        Map<String, Library> libraryMap = SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME).getSynapseLibraries();
        Collection<Library> libraryList = libraryMap.values();
        return setGrpcResponseBody(libraryList);
    }

    private static void addToConnectorList(ConnectorList.Builder connectorListBuilder, SynapseLibrary library) {

        org.wso2.micro.integrator.grpc.proto.Connector connector = org.wso2.micro.integrator.grpc.proto.Connector.newBuilder()
                .setName(library.getName())
                .setPackage(library.getPackage())
                .setDescription(library.getDescription())
                .setStatus(getConnectorState(library.getLibStatus()))
                .build();
        connectorListBuilder.addConnectors(connector);
    }

    private static String getConnectorState(Boolean status) {

        if (status) {
            return "enabled";
        }
        return "disabled";
    }
}
