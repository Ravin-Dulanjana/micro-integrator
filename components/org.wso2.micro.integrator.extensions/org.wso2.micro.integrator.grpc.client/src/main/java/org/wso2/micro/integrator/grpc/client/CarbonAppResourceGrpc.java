package org.wso2.micro.integrator.grpc.client;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.soap.SOAPBody;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.json.JSONObject;
import org.wso2.micro.application.deployer.CarbonApplication;
import org.wso2.micro.application.deployer.config.Artifact;
import org.wso2.micro.integrator.initializer.deployment.application.deployer.CappDeployer;
import org.wso2.micro.core.util.AuditLogger;

import javax.xml.namespace.QName;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.wso2.micro.integrator.grpc.client.Constants.BAD_REQUEST;
import static org.wso2.micro.integrator.grpc.client.Constants.NOT_FOUND;
import static org.wso2.micro.integrator.grpc.client.Constants.SEARCH_KEY;

public class CarbonAppResourceGrpc {

    private static final Log log = LogFactory.getLog(CarbonAppResourceGrpc.class);
    private static final String MULTIPART_FORMDATA_DATA_TYPE = "multipart/form-data";
    private static final String CAPP_NAME = "name";
    private static final String CAPP_FILE_NAME = "cAppFileName";
    public void setGrpcResponseBody(Collection<CarbonApplication> appList, Collection<CarbonApplication> faultyAppList,
                                    MessageContext messageContext) {
        org.apache.axis2.context.MessageContext axis2MessageContext =
                ((Axis2MessageContext) messageContext).getAxis2MessageContext();
        if (appList == null) {
            org.wso2.micro.integrator.grpc.proto.Error error = org.wso2.micro.integrator.grpc.proto.Error.newBuilder().setMessage("Error while getting the Carbon Application List").build();
        } else if (faultyAppList == null) {
            org.wso2.micro.integrator.grpc.proto.Error error = org.wso2.micro.integrator.grpc.proto.Error.newBuilder().setMessage("Error while getting the Carbon Faulty Application List").build();
        } else {
            org.wso2.micro.integrator.grpc.proto.CarbonAppList.Builder carbonAppListBuilder =
                    org.wso2.micro.integrator.grpc.proto.CarbonAppList.newBuilder()
                            .setActiveCount(appList.size()).setFaultyCount(faultyAppList.size()).setTotalCount(appList.size() + faultyAppList.size());
            for (CarbonApplication app : appList) {
                org.wso2.micro.integrator.grpc.proto.CarbonApp appProtoBuff = convertCarbonAppToProtoBuff(app);
                carbonAppListBuilder.addActiveList(appProtoBuff);
            }
            for (CarbonApplication faultyApp : faultyAppList) {
                org.wso2.micro.integrator.grpc.proto.CarbonApp appProtoBuff = convertCarbonAppToProtoBuff(faultyApp);
                carbonAppListBuilder.addFaultyList(appProtoBuff);
            }
        }
        //Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
    }

    public void handlePost(String performedBy, org.apache.axis2.context.MessageContext axisMsgCtx) {
        String contentType = axisMsgCtx.getProperty(Constants.CONTENT_TYPE).toString();
        if (!contentType.contains(MULTIPART_FORMDATA_DATA_TYPE)) {
            org.wso2.micro.integrator.grpc.proto.Error response = GrpcUtils.createProtoError("Error when deploying the Carbon Application. " +
                    "Supports only for the Content-Type : " + MULTIPART_FORMDATA_DATA_TYPE, axisMsgCtx, BAD_REQUEST);
            //Utils.setJsonPayLoad(axisMsgCtx, response);
            return;
        }
        SOAPBody soapBody = axisMsgCtx.getEnvelope().getBody();
        if (null != soapBody) {
            OMElement messageBody = soapBody.getFirstElement();
            if (null != messageBody) {
                Iterator iterator = messageBody.getChildElements();
                if (iterator.hasNext()) {
                    OMElement fileElement = (OMElement) iterator.next();
                    if (!iterator.hasNext()) {
                        String fileName = fileElement.getAttributeValue(new QName("filename"));
                        if (fileName != null && fileName.endsWith(".car")) {
                            byte[] bytes = Base64.getDecoder().decode(fileElement.getText());
                            Path cAppDirectoryPath = Paths.get(GrpcUtils.getCarbonHome(), "repository", "deployment",
                                    "server", "carbonapps", fileName);
                            try {
                                Files.write(cAppDirectoryPath, bytes);
                                log.info("Successfully added Carbon Application : " + fileName);
                                org.wso2.micro.integrator.grpc.proto.Message message = org.wso2.micro.integrator.grpc.proto.Message.newBuilder().setMessage("Successfully added Carbon Application : " + fileName).build();
                                //Utils.setJsonPayLoad(axisMsgCtx, jsonResponse);
                                JSONObject info = new JSONObject();
                                info.put(CAPP_FILE_NAME, fileName);
                                AuditLogger.logAuditMessage(performedBy, Constants.AUDIT_LOG_TYPE_CARBON_APPLICATION,
                                        Constants.AUDIT_LOG_ACTION_CREATED, info);
                            } catch (IOException e) {
                                String errorMessage = "Error when deploying the Carbon Application ";
                                log.error(errorMessage + fileName, e);
                                //Utils.setJsonPayLoad(axisMsgCtx, Utils.createJsonErrorObject(errorMessage));
                            }
                        } else {
                            org.wso2.micro.integrator.grpc.proto.Error error = GrpcUtils.createProtoError("Error when deploying the Carbon Application. " +
                                    "Only files with the extension .car is supported. ", axisMsgCtx, BAD_REQUEST);
                            //Utils.setJsonPayLoad(axisMsgCtx, jsonResponse);
                        }
                    } else {
                        org.wso2.micro.integrator.grpc.proto.Error error = GrpcUtils.createProtoError("Error when deploying the Carbon Application. " +
                                "Uploading Multiple files in one request is not supported. ", axisMsgCtx, BAD_REQUEST);
                        //Utils.setJsonPayLoad(axisMsgCtx, jsonResponse);
                    }
                } else {
                    org.wso2.micro.integrator.grpc.proto.Error error = GrpcUtils.createProtoError("Error when deploying the Carbon Application. " +
                            "No file exist to be uploaded. ", axisMsgCtx, BAD_REQUEST);
                    //Utils.setJsonPayLoad(axisMsgCtx, jsonResponse);
                }
            } else {
                org.wso2.micro.integrator.grpc.proto.Error error = GrpcUtils.createProtoError("Error when deploying the Carbon Application. " +
                        "No valid element found. ", axisMsgCtx, BAD_REQUEST);
                //Utils.setJsonPayLoad(axisMsgCtx, jsonResponse);
            }
        } else {
            org.wso2.micro.integrator.grpc.proto.Error error = GrpcUtils.createProtoError("Error when deploying the Carbon Application. " +
                    "No valid message body found. ", axisMsgCtx, BAD_REQUEST);
            //Utils.setJsonPayLoad(axisMsgCtx, jsonResponse);
        }
    }

    public void handleDelete(String performedBy, MessageContext messageContext, org.apache.axis2.context.MessageContext axisMsgCtx) {
        String cAppName = GrpcUtils.getPathParameter(messageContext, CAPP_NAME);
        JSONObject jsonResponse = new JSONObject();
        if (!Objects.isNull(cAppName)) {
            try {
                String cAppsDirectoryPath = Paths.get(
                        GrpcUtils.getCarbonHome(), "repository", "deployment", "server", "carbonapps").toString();

                // List deployed CApp which has downloaded CApp name
                File carbonAppsDirectory = new File(cAppsDirectoryPath);
                File[] existingCApps = carbonAppsDirectory.listFiles(new FilenameFilter() {
                    public boolean accept(File dir, String name) {
                        return name.equals(cAppName + ".car");
                    }
                });

                // Remove deployed CApp which has downloaded CApp name
                if (existingCApps != null && existingCApps.length != 0) {
                    //there should be only one capp entry
                    File cApp = existingCApps[0];
                    Files.delete(cApp.toPath());
                    log.info(cApp.getName() + " file deleted from " + cAppsDirectoryPath + " directory");
                    jsonResponse.put(Constants.MESSAGE_JSON_ATTRIBUTE, "Successfully removed Carbon Application " +
                            "named " + cAppName);
                    JSONObject info = new JSONObject();
                    info.put(CAPP_FILE_NAME, cAppName);
                    AuditLogger.logAuditMessage(performedBy, Constants.AUDIT_LOG_TYPE_CARBON_APPLICATION,
                            Constants.AUDIT_LOG_ACTION_DELETED, info);
                } else {
                    org.wso2.micro.integrator.grpc.proto.Error error = GrpcUtils.createProtoError("Cannot remove the Carbon Application." +
                            cAppName + " does not exist", axisMsgCtx, NOT_FOUND);
                }
                //Utils.setJsonPayLoad(axisMsgCtx, jsonResponse);
            } catch (IOException e) {
                String message = "Error when removing the Carbon Application " + cAppName + ".car";
                log.error(message, e);
                //Utils.setJsonPayLoad(axisMsgCtx, Utils.createJsonErrorObject(message));
            }
        } else {
            org.wso2.micro.integrator.grpc.proto.Error error = GrpcUtils.createProtoError("Cannot remove the Carbon Application. Missing required " +
                    CAPP_NAME + " parameter in the path", axisMsgCtx, BAD_REQUEST);
            //Utils.setJsonPayLoad(axisMsgCtx, jsonResponse);
        }
    }

    public void populateGrpcCarbonAppData(MessageContext messageContext, String carbonAppName) {

        org.apache.axis2.context.MessageContext axis2MessageContext =
                ((Axis2MessageContext) messageContext).getAxis2MessageContext();

        org.wso2.micro.integrator.grpc.proto.CarbonApp carbonAppProto = getGrpcCarbonAppByName(carbonAppName);

        if (Objects.nonNull(carbonAppProto)) {
            //Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
        } else {
            axis2MessageContext.setProperty(Constants.HTTP_STATUS_CODE, Constants.NOT_FOUND);
        }
    }

    private org.wso2.micro.integrator.grpc.proto.CarbonApp getGrpcCarbonAppByName(String carbonAppName) {

        List<CarbonApplication> appList
                = CappDeployer.getCarbonApps();

        for (CarbonApplication app : appList) {
            if (app.getAppName().equals(carbonAppName)) {
                return convertCarbonAppToProtoBuff(app);
            }
        }
        return null;
    }

    private org.wso2.micro.integrator.grpc.proto.CarbonApp convertCarbonAppToProtoBuff(CarbonApplication carbonApp) {

        if (Objects.isNull(carbonApp)) {
            return null;
        }

        org.wso2.micro.integrator.grpc.proto.CarbonApp.Builder carbonAppBuilder =
                org.wso2.micro.integrator.grpc.proto.CarbonApp.newBuilder();

        carbonAppBuilder.setName(carbonApp.getAppName());
        carbonAppBuilder.setVersion(carbonApp.getAppVersion());

        List<Artifact.Dependency> dependencies = carbonApp.getAppConfig().
                getApplicationArtifact().getDependencies();

        for (Artifact.Dependency dependency : dependencies) {

            Artifact artifact = dependency.getArtifact();

            String type = artifact.getType().split("/")[1];
            String artifactName = artifact.getName();

            // if the artifactName is null, artifact deployment has failed..
            if (Objects.isNull(artifactName)) {
                continue;
            }

            org.wso2.micro.integrator.grpc.proto.Artifact.Builder artifactBuilder =
                    org.wso2.micro.integrator.grpc.proto.Artifact.newBuilder();

            artifactBuilder.setName(artifactName);
            artifactBuilder.setType(type);

            carbonAppBuilder.addArtifacts(artifactBuilder.build());
        }
        return carbonAppBuilder.build();
    }
}