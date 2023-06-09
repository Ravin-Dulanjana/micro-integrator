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

    private List<Library> getSearchResults(String searchKey){
        SynapseConfiguration configuration = SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME);
        return configuration.getSynapseLibraries().values().stream()
                .filter(artifact -> artifact.getFileName().toLowerCase().contains(searchKey))
                .collect(Collectors.toList());
    }

    private void populateSearchResults(String searchKey) {
        List<Library> searchResultList = getSearchResults(searchKey);
        setGrpcResponseBody(searchResultList);
    }

    private void setGrpcResponseBody(Collection<Library> libraryList) {
        ConnectorList.Builder connectorListBuilder = ConnectorList.newBuilder();
        connectorListBuilder.setCount(libraryList.size());
        for (Library library : libraryList) {
            addToConnectorList(connectorListBuilder, (SynapseLibrary) library);
        }
        org.wso2.micro.integrator.grpc.proto.ConnectorList connectorList = connectorListBuilder.build();
        //Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
    }
    private void populateConnectorData(SynapseConfiguration synapseConfiguration, String connectorName) {

        Map<String, Library> libraries = synapseConfiguration.getSynapseLibraries();

        for (Map.Entry<String, Library> entry : libraries.entrySet()) {
            if (((SynapseLibrary)entry.getValue()).getName().equals(connectorName)) {
                SynapseLibrary connector = (SynapseLibrary)entry.getValue();
                if (Objects.nonNull(connector)) {
                    org.wso2.micro.integrator.grpc.proto.Connector protoBufConnector = getConnectorAsProtoBuff(connector);
                    //Utils.setJsonPayLoad(axis2MessageContext, getConnectorAsJson(connector));
                }
            }
        }
    }

    private org.wso2.micro.integrator.grpc.proto.Connector getConnectorAsProtoBuff(SynapseLibrary connector) {
        return org.wso2.micro.integrator.grpc.proto.Connector.newBuilder()
                .setName(connector.getName())
                .setPackage(connector.getPackage())
                .setDescription(connector.getDescription())
                .setStatus(getConnectorState(connector.getLibStatus()))
                .build();
    }

    private void populateConnectorList(MessageContext messageContext,
                                       SynapseConfiguration synapseConfiguration) {
        Map<String, Library> libraryMap = synapseConfiguration.getSynapseLibraries();
        Collection<Library> libraryList = libraryMap.values();
        setGrpcResponseBody(libraryList);
    }

    private void addToConnectorList(ConnectorList.Builder connectorListBuilder, SynapseLibrary library) {

        org.wso2.micro.integrator.grpc.proto.Connector connector = org.wso2.micro.integrator.grpc.proto.Connector.newBuilder()
                .setName(library.getName())
                .setPackage(library.getPackage())
                .setDescription(library.getDescription())
                .setStatus(getConnectorState(library.getLibStatus()))
                .build();
        connectorListBuilder.addConnectors(connector);
    }

    private String getConnectorState(Boolean status) {

        if (status) {
            return "enabled";
        }
        return "disabled";
    }
}
