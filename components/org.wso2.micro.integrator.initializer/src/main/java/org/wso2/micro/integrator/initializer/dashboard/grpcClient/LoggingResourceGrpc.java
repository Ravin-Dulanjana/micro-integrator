package org.wso2.micro.integrator.initializer.dashboard.grpcClient;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.PropertiesConfigurationLayout;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;
import org.wso2.carbon.utils.ServerConstants;
import org.wso2.micro.core.util.AuditLogger;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.wso2.micro.integrator.initializer.dashboard.grpcClient.Constants.ROOT_LOGGER;

public class LoggingResourceGrpc {

    private static PropertiesConfiguration config;
    private static PropertiesConfigurationLayout layout;
    private static final Log log = LogFactory.getLog(LoggingResourceGrpc.class);
    private static final String FILE_PATH = System.getProperty(ServerConstants.CARBON_CONFIG_DIR_PATH) + File.separator
            + "log4j2.properties";
    private static final File LOG_PROP_FILE = new File(FILE_PATH);
    private static final String LOGGER_PREFIX = "logger.";
    private static final String LOGGER_LEVEL_SUFFIX = ".level";
    private static final String LOGGER_NAME_SUFFIX = ".name";
    private static final String LOGGERS_PROPERTY = "loggers";

    private static JSONObject createInfoJSON(String loggerName, String logLevel){
        JSONObject info = new JSONObject();
        info.put(Constants.LOGGER_NAME, loggerName);
        info.put(Constants.LOGGING_LEVEL, logLevel);
        return info;
    }
    public static org.wso2.micro.integrator.grpc.proto.Message updateLoggerData(String loggerName, String loggerClass, String logLevel) {

        org.wso2.micro.integrator.grpc.proto.Message.Builder messageBuilder = org.wso2.micro.integrator.grpc.proto.Message.newBuilder();

        try {
            String performedBy = Constants.ANONYMOUS_USER;
            loadConfigs();
            String modifiedLogger = getLoggers().concat(", ").concat(loggerName);
            config.setProperty(LOGGERS_PROPERTY, modifiedLogger);
            config.setProperty(LOGGER_PREFIX + loggerName + LOGGER_NAME_SUFFIX, loggerClass);
            config.setProperty(LOGGER_PREFIX + loggerName + LOGGER_LEVEL_SUFFIX, logLevel);
            applyConfigs();
            org.wso2.micro.integrator.grpc.proto.Message message = org.wso2.micro.integrator.grpc.proto.Message.newBuilder()
                    .setMessage(getSuccessMsg(loggerClass, loggerName, logLevel)).build();
            JSONObject info = createInfoJSON(loggerName, logLevel);
            AuditLogger.logAuditMessage(performedBy, Constants.AUDIT_LOG_TYPE_LOG_LEVEL, Constants.AUDIT_LOG_ACTION_CREATED, info);
            messageBuilder.setMessage("Successfully added logger for ("+ loggerName +") with level "+logLevel);
        } catch (ConfigurationException | IOException exception) {
            createProtoError("Exception while updating logger data ", exception);
        }
        return messageBuilder.build();
    }

    public static org.wso2.micro.integrator.grpc.proto.Message updateLoggerData(String loggerName, String logLevel) {

        org.wso2.micro.integrator.grpc.proto.Message.Builder messageBuilder = org.wso2.micro.integrator.grpc.proto.Message.newBuilder();
        try {
            String performedBy = Constants.ANONYMOUS_USER;
            loadConfigs();
            if (loggerName.equals(ROOT_LOGGER)) {
                config.setProperty(loggerName + LOGGER_LEVEL_SUFFIX, logLevel);
                applyConfigs();
                messageBuilder.setMessage(getSuccessMsg("", loggerName, logLevel));
                JSONObject info = createInfoJSON(loggerName, logLevel);
                AuditLogger.logAuditMessage(performedBy, Constants.AUDIT_LOG_TYPE_ROOT_LOG_LEVEL, Constants.AUDIT_LOG_ACTION_UPDATED, info);

            } else {
                if (isLoggerExist(loggerName)) {
                    config.setProperty(LOGGER_PREFIX + loggerName + LOGGER_LEVEL_SUFFIX, logLevel);
                    JSONObject info = createInfoJSON(loggerName, logLevel);
                    AuditLogger.logAuditMessage(performedBy, Constants.AUDIT_LOG_TYPE_LOG_LEVEL, Constants.AUDIT_LOG_ACTION_UPDATED, info);
                    applyConfigs();
                    messageBuilder.setMessage(getSuccessMsg("", loggerName, logLevel));
                } else {
                    createProtoError("Specified logger ('" + loggerName + "') not found", "");
                }
            }
        } catch (ConfigurationException | IOException exception) {
            createProtoError("Exception while updating logger data ", exception);
        }
        return messageBuilder.build();
    }

    private static String getSuccessMsg(String loggerClass, String loggerName, String logLevel) {

        return "Successfully added logger for ('" + loggerName + "') with level " + logLevel + (loggerClass.isEmpty() ?
                "" :
                " for class " + loggerClass);
    }

    private static void loadConfigs() throws FileNotFoundException, ConfigurationException {
        config = new PropertiesConfiguration();
        layout = new PropertiesConfigurationLayout(config);
        layout.load(new InputStreamReader(new FileInputStream(LOG_PROP_FILE)));
    }

    private static boolean isLoggerExist(String loggerName) throws IOException {
        String logger = getLoggers();
        String[] loggers = logger.split(",");
        return Arrays.stream(loggers).anyMatch(loggerValue -> loggerValue.trim().equals(loggerName));
    }

    private static String getLoggers() {
        return GrpcUtils.getProperty(LOG_PROP_FILE, LOGGERS_PROPERTY);
    }

    private static org.wso2.micro.integrator.grpc.proto.LogConfig getLoggerData(String loggerName) {

        String logLevel = null;
        String componentName = "";
        if (loggerName.equals(ROOT_LOGGER)) {
            logLevel = GrpcUtils.getProperty(LOG_PROP_FILE, loggerName + LOGGER_LEVEL_SUFFIX);
        } else {
            componentName = GrpcUtils.getProperty(LOG_PROP_FILE, LOGGER_PREFIX + loggerName + LOGGER_NAME_SUFFIX);
            logLevel = GrpcUtils.getProperty(LOG_PROP_FILE, LOGGER_PREFIX + loggerName + LOGGER_LEVEL_SUFFIX);
        }

        org.wso2.micro.integrator.grpc.proto.LogConfig logConfig = org.wso2.micro.integrator.grpc.proto.LogConfig.newBuilder()
                .setLoggerName(loggerName)
                .setComponentName(componentName)
                .setLevel(logLevel)
                .build();

        return logConfig;
    }

    private static String[] getAllLoggers(){
        //along with root logger
        String[] loggers = getLoggers().split(",");
        // add root logger
        int fullLength = loggers.length + 1;
        String[] allLoggers = new String[fullLength];
        allLoggers[0] = ROOT_LOGGER;
        for (int i = 1; i < fullLength; i++) {
            allLoggers[i] = loggers[i - 1];
        }
        return allLoggers;
    }

    public static org.wso2.micro.integrator.grpc.proto.LogConfigList getAllLoggerDetails() {
        String[] loggers = getAllLoggers();
        return setGrpcResponseBody(Arrays.asList(loggers));
    }

    private static List<String> getSearchResults(String searchKey) {
        String[] allLoggers = getAllLoggers();
        List<String> filteredLoggers = new ArrayList<>();

        for (String logger : allLoggers) {
            if (logger.toLowerCase().contains(searchKey)) {
                filteredLoggers.add(logger);
            }
        }
        return filteredLoggers;
    }

    public static org.wso2.micro.integrator.grpc.proto.LogConfigList getAllLoggerDetails(String searchKey) {
        List<String> resultsList = getSearchResults(searchKey);
        return setGrpcResponseBody(resultsList);
    }


    private static org.wso2.micro.integrator.grpc.proto.LogConfigList setGrpcResponseBody(List<String> logConfigsList) {

        org.wso2.micro.integrator.grpc.proto.LogConfigList.Builder logConfigListBuilder =
                org.wso2.micro.integrator.grpc.proto.LogConfigList.newBuilder().setCount(logConfigsList.size());

        for (String logger : logConfigsList) {
            org.wso2.micro.integrator.grpc.proto.LogConfig data  = getLoggerData(logger.trim());
            logConfigListBuilder.addLogConfigs(data);
        }
        return logConfigListBuilder.build();
//        Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
//        axis2MessageContext.removeProperty(NO_ENTITY_BODY);
    }

    private static void createProtoError(String message, Object exception) {
        log.error(message + exception);
        GrpcUtils.createProtoError(message);
    }

    private static void applyConfigs() throws IOException, ConfigurationException {
        layout.save(new FileWriter(FILE_PATH, false));
        GrpcUtils.updateLoggingConfiguration();
    }
}
