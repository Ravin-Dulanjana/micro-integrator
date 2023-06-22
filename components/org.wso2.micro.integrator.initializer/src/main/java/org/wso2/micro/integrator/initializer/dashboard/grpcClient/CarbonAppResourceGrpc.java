package org.wso2.micro.integrator.initializer.dashboard.grpcClient;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.wso2.micro.application.deployer.CarbonApplication;
import org.wso2.micro.application.deployer.config.Artifact;
import org.wso2.micro.integrator.initializer.deployment.application.deployer.CappDeployer;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class CarbonAppResourceGrpc {

    public static org.wso2.micro.integrator.grpc.proto.CarbonAppList populateSearchResults(String searchKey) {

        List<CarbonApplication> appList
                = CappDeployer.getCarbonApps().stream().filter(capp -> capp.getAppName().toLowerCase().contains(searchKey))
                .collect(Collectors.toList());
        List<CarbonApplication> faultyAppList = CappDeployer.getFaultyCAppObjects().stream()
                .filter(capp -> capp.getAppName().toLowerCase().contains(searchKey)).collect(Collectors.toList());
        return setGrpcResponseBody(appList, faultyAppList);
    }

    private static org.wso2.micro.integrator.grpc.proto.CarbonAppList setGrpcResponseBody(Collection<CarbonApplication> appList, Collection<CarbonApplication> faultyAppList) {

        org.wso2.micro.integrator.grpc.proto.CarbonAppList.Builder carbonAppListBuilder =
                org.wso2.micro.integrator.grpc.proto.CarbonAppList.newBuilder();
        // attributes of carbonAppListBuilder becomes null if appList or faultyAppList becomes null. Must handle this in a better way.
        if (appList == null) {
            //org.wso2.micro.integrator.grpc.proto.Error error = org.wso2.micro.integrator.grpc.proto.Error.newBuilder().setMessage("Error while getting the Carbon Application List").build();
        } else if (faultyAppList == null) {
           // org.wso2.micro.integrator.grpc.proto.Error error = org.wso2.micro.integrator.grpc.proto.Error.newBuilder().setMessage("Error while getting the Carbon Faulty Application List").build();
        } else {
            carbonAppListBuilder.setActiveCount(appList.size()).setFaultyCount(faultyAppList.size()).setTotalCount(appList.size() + faultyAppList.size());
            for (CarbonApplication app : appList) {
                org.wso2.micro.integrator.grpc.proto.CarbonApp appProtoBuff = convertCarbonAppToProtoBuff(app);
                carbonAppListBuilder.addActiveList(appProtoBuff);
            }
            for (CarbonApplication faultyApp : faultyAppList) {
                org.wso2.micro.integrator.grpc.proto.CarbonApp appProtoBuff = convertCarbonAppToProtoBuff(faultyApp);
                carbonAppListBuilder.addFaultyList(appProtoBuff);
            }
        }
        return carbonAppListBuilder.build();
        //Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
    }

    public static org.wso2.micro.integrator.grpc.proto.CarbonAppList populateCarbonAppList() {

        List<CarbonApplication> appList = CappDeployer.getCarbonApps();
        List<CarbonApplication> faultyAppList = CappDeployer.getFaultyCAppObjects();
        return setGrpcResponseBody(appList, faultyAppList);
    }
    public static org.wso2.micro.integrator.grpc.proto.CarbonApp populateGrpcCarbonAppData(String carbonAppName) {

        org.wso2.micro.integrator.grpc.proto.CarbonApp carbonAppProto = getGrpcCarbonAppByName(carbonAppName);
         return carbonAppProto;
        /*
        Have not handled the case where carbonAppProto is null. Need to handle it.
        if (Objects.nonNull(carbonAppProto)) {
            Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
        } else {
            axis2MessageContext.setProperty(Constants.HTTP_STATUS_CODE, Constants.NOT_FOUND);
        }
        */
    }

    private static org.wso2.micro.integrator.grpc.proto.CarbonApp getGrpcCarbonAppByName(String carbonAppName) {

        List<CarbonApplication> appList
                = CappDeployer.getCarbonApps();

        for (CarbonApplication app : appList) {
            if (app.getAppName().equals(carbonAppName)) {
                return convertCarbonAppToProtoBuff(app);
            }
        }
        return null;
    }

    private static org.wso2.micro.integrator.grpc.proto.CarbonApp convertCarbonAppToProtoBuff(CarbonApplication carbonApp) {

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