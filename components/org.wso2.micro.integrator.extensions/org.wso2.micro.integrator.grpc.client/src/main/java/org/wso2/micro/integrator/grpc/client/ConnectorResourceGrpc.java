package org.wso2.micro.integrator.grpc.client;

import com.google.gson.JsonObject;
import org.apache.axis2.AxisFault;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.config.SynapseConfigUtils;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.libraries.imports.SynapseImport;
import org.apache.synapse.libraries.model.Library;
import org.apache.synapse.libraries.model.SynapseLibrary;
import org.json.JSONArray;
import org.json.JSONObject;
import org.wso2.micro.core.util.AuditLogger;
import org.wso2.micro.integrator.initializer.ServiceBusUtils;
import org.wso2.micro.integrator.initializer.deployment.synapse.deployer.SynapseAppDeployer;
import org.wso2.micro.integrator.initializer.persistence.MediationPersistenceManager;

import java.util.*;
import java.util.stream.Collectors;

import static org.wso2.carbon.inbound.endpoint.common.Constants.SUPER_TENANT_DOMAIN_NAME;

public class ConnectorResourceGrpc {

    private static final String NAME_ATTRIBUTE = "name";
    private static final String STATUS_ATTRIBUTE = "status";
    private static final String PACKAGE_ATTRIBUTE = "package";
    private static final String DESCRIPTION_ATTRIBUTE = "description";
    private static final String CONNECTOR_NAME = "connectorName";
    private static final String PACKAGE_NAME = "packageName";

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
        //JSONObject jsonBody = Utils.createJSONList(libraryList.size());
        for (Library library : libraryList) {
            addToJsonList(jsonBody.getJSONArray(Constants.LIST), (SynapseLibrary) library);
        }
        //Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
    }
    private void populateConnectorData(org.apache.axis2.context.MessageContext axis2MessageContext,
                                       SynapseConfiguration synapseConfiguration, String connectorName) {

        Map<String, Library> libraries = synapseConfiguration.getSynapseLibraries();

        for (Map.Entry<String, Library> entry : libraries.entrySet()) {
            if (((SynapseLibrary)entry.getValue()).getName().equals(connectorName)) {
                SynapseLibrary connector = (SynapseLibrary)entry.getValue();
                if (Objects.nonNull(connector)) {
                    Utils.setJsonPayLoad(axis2MessageContext, getConnectorAsJson(connector));
                }
            }
        }
    }

    private Object getConnectorAsJson(SynapseLibrary connector) {
        JSONObject connectorObject  = new JSONObject();
        connectorObject.put(NAME_ATTRIBUTE, connector.getName());
        connectorObject.put(PACKAGE_ATTRIBUTE, connector.getPackage());
        connectorObject.put(DESCRIPTION_ATTRIBUTE, connector.getDescription());
        connectorObject.put(STATUS_ATTRIBUTE, getConnectorState(connector.getLibStatus()));
        return connectorObject;
    }

    private void populateConnectorList(MessageContext messageContext,
                                       SynapseConfiguration synapseConfiguration) {
        Map<String, Library> libraryMap = synapseConfiguration.getSynapseLibraries();
        Collection<Library> libraryList = libraryMap.values();
        setGrpcResponseBody(libraryList);
    }

    private void addToJsonList(JSONArray jsonList, SynapseLibrary library) {

        JSONObject connectorObject = new JSONObject();
        connectorObject.put(NAME_ATTRIBUTE, library.getName());
        connectorObject.put(PACKAGE_ATTRIBUTE, library.getPackage());
        connectorObject.put(DESCRIPTION_ATTRIBUTE, library.getDescription());
        connectorObject.put(STATUS_ATTRIBUTE, getConnectorState(library.getLibStatus()));
        jsonList.put(connectorObject);
    }

    private String getConnectorState(Boolean status) {

        if (status) {
            return "enabled";
        }
        return "disabled";
    }

    private void changeConnectorState(String performedBy, org.apache.axis2.context.MessageContext axis2MessageContext,
                                      JsonObject payload, SynapseConfiguration synapseConfiguration) throws AxisFault {

        String connector = payload.get(NAME_ATTRIBUTE).getAsString();
        String packageName = payload.get(PACKAGE_ATTRIBUTE).getAsString();
        String state = payload.get(STATUS_ATTRIBUTE).getAsString();
        AxisConfiguration axisConfiguration = axis2MessageContext.getConfigurationContext().getAxisConfiguration();
        SynapseAppDeployer appDeployer = new SynapseAppDeployer();
        String qualifiedName = getQualifiedName(connector, packageName);
        Boolean updated = appDeployer.
                updateStatus(qualifiedName, connector, packageName, state, axisConfiguration);
        JSONObject jsonResponse = new JSONObject();
        if (updated) {
            persistStatus(axisConfiguration, synapseConfiguration, qualifiedName);
            JSONObject info = new JSONObject();
            info.put(CONNECTOR_NAME, connector);
            info.put(PACKAGE_NAME, packageName);
            info.put(STATUS_ATTRIBUTE, state);
            AuditLogger.logAuditMessage(performedBy, Constants.AUDIT_LOG_TYPE_CONNECTOR,
                    Constants.AUDIT_LOG_ACTION_CREATED, info);

            jsonResponse.put("Message", "Status updated successfully");
        } else {
            jsonResponse.put("Message", "Status updated failed");
        }
        Utils.setJsonPayLoad(axis2MessageContext, jsonResponse);
    }

    private String getQualifiedName(String libName, String packageName) {

        return ("{" + packageName.trim() + "}" + libName.trim()).toLowerCase();
    }

    /**
     * Persist the change state of the connector by writing to the import file
     *
     * @param axisConfiguration    axisconfiguration
     * @param synapseConfiguration synapseconfiguration
     * @param qualifiedName        fully qualified name of the library
     */
    private void persistStatus(AxisConfiguration axisConfiguration,
                               SynapseConfiguration synapseConfiguration, String qualifiedName) {
        SynapseImport synapseImport = synapseConfiguration.getSynapseImports().get(qualifiedName);
        MediationPersistenceManager mediationPersistenceManager = ServiceBusUtils.
                getMediationPersistenceManager(axisConfiguration);
        mediationPersistenceManager.saveItem(synapseImport.getName(), ITEM_TYPE_IMPORT);
    }
}
