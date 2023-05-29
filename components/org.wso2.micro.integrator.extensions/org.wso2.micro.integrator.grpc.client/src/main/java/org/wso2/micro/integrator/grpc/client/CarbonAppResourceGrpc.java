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
import java.util.stream.Collectors;

import static org.wso2.micro.integrator.grpc.client.Constants.BAD_REQUEST;
import static org.wso2.micro.integrator.grpc.client.Constants.NOT_FOUND;
import static org.wso2.micro.integrator.grpc.client.Constants.SEARCH_KEY;

public class CarbonAppResourceGrpc {

    private static final Log log = LogFactory.getLog(CarbonAppResourceGrpc.class);
    private static final String MULTIPART_FORMDATA_DATA_TYPE = "multipart/form-data";
    private static final String CAPP_NAME = "name";
    private static final String CAPP_FILE_NAME = "cAppFileName";

    public void populateSearchResults(String searchKey) {

        List<CarbonApplication> appList
                = CappDeployer.getCarbonApps().stream().filter(capp -> capp.getAppName().toLowerCase().contains(searchKey))
                .collect(Collectors.toList());
        List<CarbonApplication> faultyAppList = CappDeployer.getFaultyCAppObjects().stream()
                .filter(capp -> capp.getAppName().toLowerCase().contains(searchKey)).collect(Collectors.toList());
        setGrpcResponseBody(appList, faultyAppList);
    }

    private void setGrpcResponseBody(Collection<CarbonApplication> appList, Collection<CarbonApplication> faultyAppList) {

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

    public void populateCarbonAppList() {

        List<CarbonApplication> appList
                = CappDeployer.getCarbonApps();

        List<CarbonApplication> faultyAppList = CappDeployer.getFaultyCAppObjects();
        setGrpcResponseBody(appList, faultyAppList);
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