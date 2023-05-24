package org.wso2.micro.integrator.grpc.client;

import com.google.gson.JsonObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.commons.json.JsonUtil;
import org.apache.synapse.transport.passthru.config.PassThroughCorrelationConfigDataHolder;
import org.json.JSONObject;

import java.io.IOException;

public class ConfigsResourceGrpc {

    private static final Log LOG = LogFactory.getLog(ConfigsResourceGrpc.class);

    private static final String CONFIG_NAME = "configName";
    private static final String CONFIGS = "configs";
    private static final String CORRELATION = "correlation";
    private static final String ENABLED = "enabled";

    private org.wso2.micro.integrator.grpc.proto.Error handlePut(org.apache.axis2.context.MessageContext axis2MessageContext)
            throws ConfigNotFoundException, IOException {
        if (!JsonUtil.hasAJsonPayload(axis2MessageContext)) {
            return org.wso2.micro.integrator.grpc.proto.Error.newBuilder()
                    .setMessage("Request payload is missing").build();
        }
        JsonObject payload = Utils.getJsonPayload(axis2MessageContext);
        String configName;
        if (!payload.has(CONFIG_NAME)) {
            throw new ConfigNotFoundException("Missing Required Field: " + CONFIG_NAME);
        }
        configName = payload.get(CONFIG_NAME).getAsString();
        switch (configName) {
            case CORRELATION: {
                JsonObject configs;
                if (!payload.has(CONFIGS)) {
                    throw new ConfigNotFoundException("Missing Required Field: " + CONFIGS);
                }
                configs = payload.get(CONFIGS).getAsJsonObject();
                if (!configs.has(ENABLED)) {
                    throw new ConfigNotFoundException("Missing Required Field: " + ENABLED);
                }
                boolean enabled = Boolean.parseBoolean(configs.get(ENABLED).getAsString());
                PassThroughCorrelationConfigDataHolder.setEnable(enabled);
                JSONObject response = new JSONObject();
                response.put(Constants.MESSAGE, "Successfully Updated Correlation Logs Status");
                return response;
            }
            default: {
                throw new ConfigNotFoundException(configName + " configName not found");
            }
        }
    }

    private JSONObject handleGet(MessageContext messageContext) throws ConfigNotFoundException {
        String configName = GrpcUtils.getQueryParameter(messageContext, CONFIG_NAME);
        if (configName == null) {
            throw new ConfigNotFoundException("Missing Required Query Parameter : configName");
        }
        JSONObject response;
        switch (configName) {
            case CORRELATION: {
                JSONObject configs = new JSONObject();
                Boolean correlationEnabled = PassThroughCorrelationConfigDataHolder.isEnable();
                configs.put(ENABLED, correlationEnabled);
                response = new JSONObject();
                response.put(CONFIG_NAME, configName);
                response.put(CONFIGS, configs);
                break;
            }
            default: {
                throw new ConfigNotFoundException(configName + " configName not found");
            }
        }
        return response;
    }
}
