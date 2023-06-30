package org.wso2.micro.integrator.initializer.dashboard.grpcClient;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMNamespace;
import org.apache.axiom.om.OMText;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axiom.soap.SOAPFactory;
import org.apache.axis2.AxisFault;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.config.SynapseConfigUtils;
import org.apache.synapse.transport.passthru.util.RelayConstants;
import org.apache.synapse.transport.passthru.util.StreamingOnRequestDataSource;
import org.json.JSONObject;
import org.wso2.micro.core.util.AuditLogger;
import org.wso2.micro.integrator.registry.MicroIntegratorRegistry;

import javax.activation.DataHandler;
import javax.mail.util.ByteArrayDataSource;
import java.io.*;
import java.util.Objects;

import static org.wso2.carbon.inbound.endpoint.common.Constants.SUPER_TENANT_DOMAIN_NAME;
import static org.wso2.micro.integrator.initializer.dashboard.grpcClient.GrpcUtils.*;

public class RegistryContentResourceGrpc {

    private static final Log LOG = LogFactory.getLog(RegistryContentResourceGrpc.class);
    private void handleGet(String registryPath, String validatedPath) {

        if (!isRegistryExist(validatedPath)) {
            GrpcUtils.createProtoError("Can not find the registry: ");
        } else {
            populateRegistryContent(validatedPath);
        }
    }

    private void populateRegistryContent(String pathParameter) {

        DataHandler dataHandler = downloadRegistryFiles(pathParameter);
        if (dataHandler != null) {
            try {
                InputStream fileInput = dataHandler.getInputStream();
                SOAPFactory factory = OMAbstractFactory.getSOAP12Factory();
                SOAPEnvelope env = factory.getDefaultEnvelope();
                OMNamespace ns = factory.createOMNamespace(RelayConstants.BINARY_CONTENT_QNAME.getNamespaceURI(), "ns");
                OMElement omEle = factory.createOMElement(RelayConstants.BINARY_CONTENT_QNAME.getLocalPart(), ns);
                StreamingOnRequestDataSource ds = new StreamingOnRequestDataSource(fileInput);
                dataHandler = new DataHandler(ds);
                OMText textData = factory.createOMText(dataHandler, true);
                omEle.addChild(textData);
                env.getBody().addChild(omEle);
//                synCtx.setEnvelope(env);
//                axis2MessageContext.removeProperty(NO_ENTITY_BODY);
//                axis2MessageContext.setProperty(Constants.MESSAGE_TYPE, "application/octet-stream");
//                axis2MessageContext.setProperty(Constants.CONTENT_TYPE, "application/txt");
            } catch (AxisFault e) {
                LOG.error("Error occurred while creating the response", e);
                GrpcUtils.createProtoError("Error occurred while creating the response");
            } catch (IOException e) {
                LOG.error("Error occurred while reading the input stream", e);
                GrpcUtils.createProtoError("Error occurred while reading the input stream");
            }
        } else {
            GrpcUtils.createProtoError("Error occurred while reading the requested file");
        }
    }

    private DataHandler downloadRegistryFiles(String path) {

        ByteArrayDataSource bytArrayDS;
        MicroIntegratorRegistry microIntegratorRegistry =
                (MicroIntegratorRegistry) SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME).getRegistry();
        String regRoot = microIntegratorRegistry.getRegRoot();
        String registryPath = formatPath(regRoot + File.separator + path);
        File file = new File(registryPath);
        if (file.exists() && !file.isDirectory()) {
            try (InputStream is = new BufferedInputStream(new FileInputStream(registryPath))) {
                bytArrayDS = new ByteArrayDataSource(is, "text/xml");
                return new DataHandler(bytArrayDS);
            } catch (FileNotFoundException e) {
                LOG.error("Could not find the requested file : " + path + " in : " + registryPath, e);
                return null;
            } catch (IOException e) {
                LOG.error("Error occurred while reading the file : " + path + " in : " + registryPath, e);
                return null;
            }
        } else {
            LOG.error("Could not find the requested file : " + path + " in : " + registryPath);
            return null;
        }
    }

    private void handlePost(String registryPath, String validatedPath) {

        JSONObject jsonBody;
        String pathWithPrefix = getRegistryPathPrefix(validatedPath);
        if (isRegistryExist(validatedPath)) {
            GrpcUtils.createProtoError("Registry already exists. Can not POST an existing registry");
        } else if (Objects.nonNull(pathWithPrefix)) {
            jsonBody = postRegistryArtifact(pathWithPrefix);
        } else {
            GrpcUtils.createProtoError("Invalid registry path: " + registryPath);
        }

    }

    private JSONObject postRegistryArtifact(String registryPath) {

        String contentType = axis2MessageContext.getProperty(CONTENT_TYPE).toString();
        String mediaType = Utils.getQueryParameter(messageContext, MEDIA_TYPE_KEY);
        String performedBy = ANONYMOUS_USER;
        JSONObject info = new JSONObject();

        if (Objects.isNull(mediaType)) {
            mediaType = DEFAULT_MEDIA_TYPE;
        }
        if (messageContext.getProperty(USERNAME_PROPERTY) !=  null) {
            performedBy = messageContext.getProperty(USERNAME_PROPERTY).toString();
        }
        String name = getResourceName(registryPath);
        info.put(REGISTRY_RESOURCE_NAME, name);

        JSONObject jsonBody = new JSONObject();
        MicroIntegratorRegistry microIntegratorRegistry =
                (MicroIntegratorRegistry) messageContext.getConfiguration().getRegistry();
        if (Objects.nonNull(contentType) && contentType.contains(CONTENT_TYPE_MULTIPART_FORM_DATA)) {
            byte[] fileContent = getPayloadFromMultipart(messageContext);
            if (Objects.nonNull(fileContent)) {
                microIntegratorRegistry.addMultipartResource(registryPath, mediaType, fileContent);
                jsonBody.put("message", "Successfully added the registry resource");
                AuditLogger.logAuditMessage(performedBy, AUDIT_LOG_TYPE_REGISTRY_RESOURCE,
                        AUDIT_LOG_ACTION_CREATED, info);
            } else {
                jsonBody = Utils.createJsonError("Error while fetching file content from payload", axis2MessageContext,
                        BAD_REQUEST);
            }
        } else if (Objects.nonNull(contentType)) {
            String fileContent = getPayload(axis2MessageContext);
            if (Objects.nonNull(fileContent)) {
                microIntegratorRegistry.newNonEmptyResource(registryPath, false, mediaType, fileContent, "");
                jsonBody.put("message", "Successfully added the registry resource");
                AuditLogger.logAuditMessage(performedBy, AUDIT_LOG_TYPE_REGISTRY_RESOURCE,
                        AUDIT_LOG_ACTION_CREATED, info);
            } else {
                jsonBody = Utils.createJsonError("Error while fetching file content from payload", axis2MessageContext,
                        BAD_REQUEST);
            }
        } else {
            jsonBody = Utils.createJsonError("Error while fetching Content-Type from request", axis2MessageContext,
                    BAD_REQUEST);
        }
        return jsonBody;
    }

    private void handlePut(MessageContext messageContext, org.apache.axis2.context.MessageContext axis2MessageContext,
                           String registryPath, String validatedPath) {

        JSONObject jsonBody;
        String pathWithPrefix = getRegistryPathPrefix(validatedPath);
        if (!isRegistryExist(validatedPath, messageContext)) {
            jsonBody = Utils.createJsonError("Registry does not exists in the path: " + registryPath,
                    axis2MessageContext, BAD_REQUEST);
        } else if (Objects.nonNull(pathWithPrefix)) {
            jsonBody = putRegistryArtifact(axis2MessageContext, messageContext, pathWithPrefix);
        } else {
            jsonBody = Utils.createJsonError("Invalid registry path: " + registryPath, axis2MessageContext,
                    BAD_REQUEST);
        }
        Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
    }

    private JSONObject putRegistryArtifact(org.apache.axis2.context.MessageContext axis2MessageContext,
                                           MessageContext messageContext, String registryPath) {

        String contentType = axis2MessageContext.getProperty(CONTENT_TYPE).toString();
        String performedBy = ANONYMOUS_USER;
        JSONObject info = new JSONObject();

        if (messageContext.getProperty(USERNAME_PROPERTY) !=  null) {
            performedBy = messageContext.getProperty(USERNAME_PROPERTY).toString();
        }
        String name = getResourceName(registryPath);
        info.put(REGISTRY_RESOURCE_NAME, name);

        JSONObject jsonBody = new JSONObject();
        MicroIntegratorRegistry microIntegratorRegistry =
                (MicroIntegratorRegistry) messageContext.getConfiguration().getRegistry();
        if (Objects.nonNull(contentType) && contentType.contains(CONTENT_TYPE_MULTIPART_FORM_DATA)) {
            byte[] fileContent = getPayloadFromMultipart(messageContext);
            if (Objects.nonNull(fileContent)) {
                microIntegratorRegistry.addMultipartResource(registryPath, null, fileContent);
                jsonBody.put("message", "Successfully modified the registry resource");
                AuditLogger.logAuditMessage(performedBy, AUDIT_LOG_TYPE_REGISTRY_RESOURCE,
                        AUDIT_LOG_ACTION_UPDATED, info);
            } else {
                jsonBody = Utils.createJsonError("Error while fetching file content from payload", axis2MessageContext,
                        BAD_REQUEST);
            }
        } else if (Objects.nonNull(contentType)) {
            String fileContent = getPayload(axis2MessageContext);
            if (Objects.nonNull(fileContent)) {
                microIntegratorRegistry.updateResource(registryPath, fileContent);
                jsonBody.put("message", "Successfully modified the registry resource");
                AuditLogger.logAuditMessage(performedBy, AUDIT_LOG_TYPE_REGISTRY_RESOURCE,
                        AUDIT_LOG_ACTION_UPDATED, info);
            } else {
                jsonBody = Utils.createJsonError("Error while fetching file content from payload", axis2MessageContext,
                        BAD_REQUEST);
            }
        } else {
            jsonBody = Utils.createJsonError("Error while fetching Content-Type from request", axis2MessageContext,
                    BAD_REQUEST);
        }
        return jsonBody;
    }


    private void handleDelete(MessageContext messageContext,
                              org.apache.axis2.context.MessageContext axis2MessageContext, String registryPath, String validatedPath) {

        JSONObject jsonBody;
        String pathWithPrefix = getRegistryPathPrefix(validatedPath);
        if (!isRegistryExist(validatedPath, messageContext)) {
            jsonBody = Utils.createJsonError("Registry does not exists in the path: " + registryPath,
                    axis2MessageContext, BAD_REQUEST);
        } else if (Objects.nonNull(pathWithPrefix)) {
            jsonBody = deleteRegistryArtifact(messageContext, axis2MessageContext, pathWithPrefix, validatedPath);
        } else {
            jsonBody = Utils.createJsonError("Invalid registry path: " + registryPath, axis2MessageContext,
                    BAD_REQUEST);
        }
        Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
    }

    private JSONObject deleteRegistryArtifact(MessageContext messageContext,
                                              org.apache.axis2.context.MessageContext axis2MessageContext, String registryPath, String validatedPath) {

        String performedBy = ANONYMOUS_USER;
        JSONObject info = new JSONObject();

        if (messageContext.getProperty(USERNAME_PROPERTY) !=  null) {
            performedBy = messageContext.getProperty(USERNAME_PROPERTY).toString();
        }
        String name = getResourceName(registryPath);
        info.put(REGISTRY_RESOURCE_NAME, name);
        MicroIntegratorRegistry microIntegratorRegistry =
                (MicroIntegratorRegistry) messageContext.getConfiguration().getRegistry();

        for (int i = 0; i < MAXIMUM_RETRY_COUNT; i++) {
            microIntegratorRegistry.delete(registryPath);
            if (!isRegistryExist(validatedPath, messageContext)) {
                JSONObject jsonBody = new JSONObject();
                jsonBody.put("message", "Successfully deleted the registry resource");
                AuditLogger.logAuditMessage(performedBy, AUDIT_LOG_TYPE_REGISTRY_RESOURCE,
                        AUDIT_LOG_ACTION_DELETED, info);
                return jsonBody;
            }
        }
        return GrpcUtils.createProtoError("Seems that the file still exists but unable to delete the resource");
    }

}
