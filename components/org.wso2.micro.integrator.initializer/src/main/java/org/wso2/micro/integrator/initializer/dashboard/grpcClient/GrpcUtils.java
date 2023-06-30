package org.wso2.micro.integrator.initializer.dashboard.grpcClient;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.aspects.AspectConfiguration;
import org.apache.synapse.config.SynapseConfigUtils;
import org.apache.synapse.rest.RESTConstants;
import org.json.JSONObject;
import org.ops4j.pax.logging.PaxLoggingConstants;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.wso2.micro.core.util.AuditLogger;
import org.wso2.micro.integrator.initializer.dashboard.ArtifactUpdateListener;
import org.wso2.micro.integrator.initializer.utils.ConfigurationHolder;
import org.wso2.micro.integrator.registry.MicroIntegratorRegistry;
import org.wso2.micro.integrator.security.MicroIntegratorSecurityUtils;
import org.wso2.micro.integrator.security.user.api.UserStoreException;
import org.wso2.micro.integrator.security.user.api.UserStoreManager;
import org.wso2.micro.integrator.security.user.core.UserCoreConstants;
import org.wso2.micro.integrator.security.user.core.common.AbstractUserStoreManager;
import org.wso2.micro.service.mgt.ServiceAdmin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;

import static org.wso2.carbon.inbound.endpoint.common.Constants.SUPER_TENANT_DOMAIN_NAME;
import static org.wso2.micro.integrator.initializer.dashboard.grpcClient.Constants.*;

public class GrpcUtils {

    private static final Log LOG = LogFactory.getLog(GrpcUtils.class);

    static org.wso2.micro.integrator.grpc.proto.Error createProtoError(String message) {
        LOG.error(message);
        return createResponse(message);
    }

    private static org.wso2.micro.integrator.grpc.proto.Error createResponse(String message) {
        org.wso2.micro.integrator.grpc.proto.Error error = org.wso2.micro.integrator.grpc.proto.Error.newBuilder()
                .setMessage(message).build();
        //axis2MessageContext.setProperty(Constants.HTTP_STATUS_CODE, statusCode);
        return error;
    }

    public static String getPathParameter(MessageContext messageContext, String key){
        if (Objects.nonNull(messageContext.getProperty(RESTConstants.REST_URI_VARIABLE_PREFIX + key))) {
            return messageContext.getProperty(RESTConstants.REST_URI_VARIABLE_PREFIX + key).toString();
        }
        return null;
    }

    public static String getCarbonHome() {

        String carbonHome = System.getProperty("carbon.home");
        if (carbonHome == null) {
            carbonHome = System.getenv("CARBON_HOME");
            System.setProperty("carbon.home", carbonHome);
        }
        return carbonHome;
    }

    public static ServiceAdmin getServiceAdmin() {

        ServiceAdmin serviceAdmin = ServiceAdmin.getInstance();
        if (!serviceAdmin.isInitailized()) {
            serviceAdmin.init(SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME).getAxisConfiguration());
        }
        return serviceAdmin;
    }

    static org.wso2.micro.integrator.grpc.proto.Message handleTracing(String performedBy, String type, String artifactType, JSONObject info,
                                    AspectConfiguration config, String artifactName, String traceState) {
        String msg = null;
        org.wso2.micro.integrator.grpc.proto.Message.Builder responseBuilder = org.wso2.micro.integrator.grpc.proto.Message.newBuilder();
        if (traceState != null && !traceState.isEmpty()) {
            if (Constants.ENABLE.equalsIgnoreCase(traceState)) {
                config.enableTracing();
                msg = "Enabled tracing for ('" + artifactName + "')";
                responseBuilder.setMessage(msg);
                AuditLogger.logAuditMessage(performedBy, type, Constants.AUDIT_LOG_ACTION_ENABLE, info);
                ArtifactUpdateListener.addToUpdatedArtifactsQueue(artifactType, artifactName);
            } else if (Constants.DISABLE.equalsIgnoreCase(traceState)) {
                config.disableTracing();
                msg = "Disabled tracing for ('" + artifactName + "')";
                responseBuilder.setMessage(msg);
                AuditLogger.logAuditMessage(performedBy, type, Constants.AUDIT_LOG_ACTION_DISABLED, info);
                ArtifactUpdateListener.addToUpdatedArtifactsQueue(artifactType, artifactName);
            }
        }
        LOG.info(msg);
        return responseBuilder.build();
    }

    public static String getCarbonLogsPath() {

        String carbonLogsPath = System.getProperty("carbon.logs.path");
        if (carbonLogsPath == null) {
            carbonLogsPath = System.getenv("CARBON_LOGS");
            if (carbonLogsPath == null) {
                return getCarbonHome() + File.separator + "repository" + File.separator + "logs";
            }
        }
        return carbonLogsPath;
    }

    public static List<LogFileInfo> getLogFileInfoList() {

        String folderPath = GrpcUtils.getCarbonLogsPath();
        List<LogFileInfo> logFilesList = new ArrayList<>();
        LogFileInfo logFileInfo;

        File folder = new File(folderPath);
        File[] listOfFiles = folder.listFiles();

        if (listOfFiles == null || listOfFiles.length == 0) {
            // folder.listFiles can return a null, in that case return a default log info
            if (LOG.isDebugEnabled()) {
                LOG.debug("Could not find any log file in " + folderPath);
            }
            return getDefaultLogInfoList();
        }
        for (File file : listOfFiles) {
            String filename = file.getName();
            if (!filename.endsWith(".lck")) {
                String filePath = GrpcUtils.getCarbonLogsPath() + File.separator + filename;
                File logfile = new File(filePath);
                logFileInfo = new LogFileInfo(filename, getFileSize(logfile));
                logFilesList.add(logFileInfo);
            }
        }
        return logFilesList;
    }

    private static String getFileSize(File file) {

        long bytes = file.length();
        int unit = 1024;
        if (bytes < unit) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    private static List<LogFileInfo> getDefaultLogInfoList() {

        List<LogFileInfo> defaultLogFileInfoList = new ArrayList<>();
        defaultLogFileInfoList.add(new LogFileInfo("NO_LOG_FILES", "---"));
        return defaultLogFileInfoList;
    }

    public static String getProperty(File srcFile, String key) {

        String value;
        try (FileInputStream fis = new FileInputStream(srcFile)) {
            Properties properties = new Properties();
            properties.load(fis);
            value = properties.getProperty(key);
        } catch (IOException e) {
            try {
                throw new IOException("Error occurred while reading the input stream");
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
        return value;
    }

    public static void updateLoggingConfiguration() throws IOException {

        ConfigurationAdmin configurationAdmin = ConfigurationHolder.getInstance().getConfigAdminService();
        Configuration configuration =
                configurationAdmin.getConfiguration(Constants.PAX_LOGGING_CONFIGURATION_PID, "?");
        Dictionary properties = new Hashtable<>();
        properties.put(Constants.SERVICE_PID, PaxLoggingConstants.LOGGING_CONFIGURATION_PID);
        Hashtable paxLoggingProperties = getPaxLoggingProperties();
        paxLoggingProperties.forEach(properties::put);
        configuration.update(properties);
    }

    private static Hashtable getPaxLoggingProperties() throws IOException {
        String paxPropertiesFileLocation = System.getProperty("org.ops4j.pax.logging.property.file");
        if (StringUtils.isNotEmpty(paxPropertiesFileLocation)) {
            File file = new File(paxPropertiesFileLocation);
            if (file.exists()) {
                Properties properties = new Properties();
                properties.load(new FileInputStream(file));
                return properties;
            }
        }
        return new Hashtable();
    }

    public static boolean isRegistryExist(String registryPath) {
        MicroIntegratorRegistry microIntegratorRegistry =
                (MicroIntegratorRegistry) SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME).getRegistry();
        String regRoot = microIntegratorRegistry.getRegRoot();
        String resolvedPath = formatPath(regRoot + File.separator + registryPath + File.separator);
        try {
            File file = new File(resolvedPath);
            return file.exists();
        } catch (Exception e) {
            LOG.error("Error occurred while checking the existence of the registry", e);
            return false;
        }
    }

    public static String formatPath(String path) {
        // removing white spaces
        String pathFormatted = path.replaceAll("\\b\\s+\\b", "%20");
        try {
            pathFormatted = java.net.URLDecoder.decode(pathFormatted, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            LOG.error("Unsupported Encoding in the path :" + pathFormatted);
        }
        // replacing all "\" with "/"
        return pathFormatted.replace('\\', '/');
    }

    public static String getRegistryPathPrefix(String path) {

        String pathWithPrefix;
        if (path.startsWith(CONFIGURATION_REGISTRY_PATH)) {
            pathWithPrefix = path.replace(CONFIGURATION_REGISTRY_PATH, CONFIGURATION_REGISTRY_PREFIX);
        } else if (path.startsWith(GOVERNANCE_REGISTRY_PATH)) {
            pathWithPrefix = path.replace(GOVERNANCE_REGISTRY_PATH, GOVERNANCE_REGISTRY_PREFIX);
        } else if (path.startsWith(LOCAL_REGISTRY_PATH)) {
            pathWithPrefix = path.replace(LOCAL_REGISTRY_PATH, LOCAL_REGISTRY_PREFIX);
        } else {
            return null;
        }
        return pathWithPrefix;
    }
    protected static UserStoreManager getUserStore(String domain) throws UserStoreException {
        UserStoreManager userStoreManager = MicroIntegratorSecurityUtils.getUserStoreManager();
        if (!StringUtils.isEmpty(domain) && userStoreManager instanceof AbstractUserStoreManager &&
                !UserCoreConstants.PRIMARY_DEFAULT_DOMAIN_NAME.equalsIgnoreCase(domain)) {
            userStoreManager = ((AbstractUserStoreManager) userStoreManager).getSecondaryUserStoreManager(domain);
            if (userStoreManager == null) {
                throw new UserStoreException("Could not find a user-store for the given domain " + domain);
            }
        }
        return userStoreManager;
    }
}
