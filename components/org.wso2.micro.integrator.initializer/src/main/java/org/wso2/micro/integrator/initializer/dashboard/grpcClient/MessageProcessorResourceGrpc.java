package org.wso2.micro.integrator.initializer.dashboard.grpcClient;

import org.apache.synapse.config.SynapseConfigUtils;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.config.xml.MessageProcessorSerializer;
import org.apache.synapse.message.processor.MessageProcessor;
import org.apache.synapse.message.processor.impl.failover.FailoverScheduledMessageForwardingProcessor;
import org.apache.synapse.message.processor.impl.forwarder.ScheduledMessageForwardingProcessor;
import org.apache.synapse.message.processor.impl.sampler.SamplingProcessor;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.wso2.carbon.inbound.endpoint.common.Constants.SUPER_TENANT_DOMAIN_NAME;
import static org.wso2.micro.integrator.initializer.dashboard.grpcClient.Constants.ACTIVE_STATUS;
import static org.wso2.micro.integrator.initializer.dashboard.grpcClient.Constants.INACTIVE_STATUS;

public class MessageProcessorResourceGrpc {
    private static final String SAMPLING_PROCESSOR_TYPE = "Sampling-processor";
    private static final String SCHEDULED_MESSAGE_FORWARDING_TYPE = "Scheduled-message-forwarding-processor";
    private static final String FAILOVER_SCHEDULED_MESSAGE_FORWARDING_TYPE = "Failover-scheduled-message-forwarding-processor";
    private static final String CUSTOM_PROCESSOR_TYPE = "Custom-message-processor";

    private static List<MessageProcessor> getSearchResults(String searchKey) {
        SynapseConfiguration configuration = SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME);
        return configuration.getMessageProcessors().values().stream()
                .filter(artifact -> artifact.getName().toLowerCase().contains(searchKey))
                .collect(Collectors.toList());
    }


    public static org.wso2.micro.integrator.grpc.proto.MessageProcessorsList populateSearchResults(String searchKey) {

        List<MessageProcessor> searchResultList = getSearchResults(searchKey);
        return setGrpcResponseBody(searchResultList);
    }

    private static org.wso2.micro.integrator.grpc.proto.MessageProcessorsList setGrpcResponseBody(Collection<MessageProcessor> processorList) {

        org.wso2.micro.integrator.grpc.proto.MessageProcessorsList.Builder messageProcessorsListBuilder = org.wso2.micro.integrator.grpc.proto.MessageProcessorsList.newBuilder().setCount(processorList.size());

        for (MessageProcessor processor : processorList) {
            messageProcessorsListBuilder.addMessageProcessorSummaries(getProtoMessageProcessor(processor));
        }
        return messageProcessorsListBuilder.build();
    }

    public static org.wso2.micro.integrator.grpc.proto.MessageProcessorsList populateMessageProcessorList() {
        SynapseConfiguration synapseConfiguration = SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME);
        Map<String, MessageProcessor> processorMap = synapseConfiguration.getMessageProcessors();
        Collection<MessageProcessor> processorList = processorMap.values();
        return setGrpcResponseBody(processorList);
    }

    public static org.wso2.micro.integrator.grpc.proto.MessageProcessor populateMessageProcessorData(String name) {
        SynapseConfiguration synapseConfiguration = SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME);
        MessageProcessor messageProcessor = synapseConfiguration.getMessageProcessors().get(name);
        return getMessageProcessorAsProtoBuf(messageProcessor);

        /*
        Have not handled the case where messageProcessor is null. Need to handle it.
        if (Objects.nonNull(messageProcessor)) {
            //send message processor data
        } else {
            GrpcUtils.createProtoError("Specified message processor ('" + name + "') not found");
        }
        */
    }

    private static org.wso2.micro.integrator.grpc.proto.MessageProcessorSummary getProtoMessageProcessor(MessageProcessor messageProcessor) {
        org.wso2.micro.integrator.grpc.proto.MessageProcessorSummary.Builder messageProcessorSummaryBuilder = org.wso2.micro.integrator.grpc.proto.MessageProcessorSummary.newBuilder();
        messageProcessorSummaryBuilder.setName(messageProcessor.getName());
        messageProcessorSummaryBuilder.setType(getMessageProcessorType(messageProcessor));
        messageProcessorSummaryBuilder.setStatus(getProcessorState(messageProcessor.isDeactivated()));
        return messageProcessorSummaryBuilder.build();
    }

    private static org.wso2.micro.integrator.grpc.proto.MessageProcessor getMessageProcessorAsProtoBuf(MessageProcessor messageProcessor) {
        org.wso2.micro.integrator.grpc.proto.MessageProcessor.Builder messageProcessorBuilder = org.wso2.micro.integrator.grpc.proto.MessageProcessor.newBuilder();
        messageProcessorBuilder.setName(messageProcessor.getName());
        messageProcessorBuilder.setType(getMessageProcessorType(messageProcessor));
        messageProcessorBuilder.setContainer(messageProcessor.getArtifactContainerName());
        messageProcessorBuilder.setFileName(messageProcessor.getFileName());
        //messageProcessorBuilder.setParameters(messageProcessor.getParameters());
        messageProcessorBuilder.setMessageStore(messageProcessor.getMessageStoreName());
        messageProcessorBuilder.setStatus(getProcessorState(messageProcessor.isDeactivated()));
        messageProcessorBuilder.setConfiguration(MessageProcessorSerializer.serializeMessageProcessor(null, messageProcessor).toString());

        return messageProcessorBuilder.build();
    }

    private static String getMessageProcessorType(MessageProcessor messageProcessor) {
        if (messageProcessor instanceof ScheduledMessageForwardingProcessor) {
            return SCHEDULED_MESSAGE_FORWARDING_TYPE;
        } else if (messageProcessor instanceof FailoverScheduledMessageForwardingProcessor) {
            return FAILOVER_SCHEDULED_MESSAGE_FORWARDING_TYPE;
        } else if (messageProcessor instanceof SamplingProcessor) {
            return SAMPLING_PROCESSOR_TYPE;
        } else {
            return CUSTOM_PROCESSOR_TYPE;
        }
    }

    /*private void changeProcessorStatus(JsonObject jsonObject) {

        SynapseConfiguration synapseConfiguration = SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME);
        String processorName = jsonObject.get(NAME).getAsString();
        String status = jsonObject.get(STATUS).getAsString();

        MessageProcessor messageProcessor = synapseConfiguration.getMessageProcessors().get(processorName);
        if (Objects.nonNull(messageProcessor)) {
            JSONObject jsonResponse = new JSONObject();
            String performedBy = Constants.ANONYMOUS_USER;
            if (messageContext.getProperty(Constants.USERNAME_PROPERTY) !=  null) {
                performedBy = messageContext.getProperty(Constants.USERNAME_PROPERTY).toString();
            }
            JSONObject info = new JSONObject();
            info.put(MESSAGE_PROCESSOR_NAME, processorName);
            if (INACTIVE_STATUS.equalsIgnoreCase(status)) {
                messageProcessor.deactivate();
                jsonResponse.put(Constants.MESSAGE_JSON_ATTRIBUTE, processorName + " : is deactivated");
                AuditLogger.logAuditMessage(performedBy, Constants.AUDIT_LOG_TYPE_MESSAGE_PROCESSOR,
                        Constants.AUDIT_LOG_ACTION_DISABLED, info);
            } else if (ACTIVE_STATUS.equalsIgnoreCase(status)) {
                messageProcessor.activate();
                jsonResponse.put(Constants.MESSAGE_JSON_ATTRIBUTE, processorName + " : is activated");
                AuditLogger.logAuditMessage(performedBy, Constants.AUDIT_LOG_TYPE_MESSAGE_PROCESSOR,
                        Constants.AUDIT_LOG_ACTION_ENABLE, info);
            } else {
                jsonResponse = Utils.createJsonError("Provided state is not valid", axis2MessageContext, Constants.BAD_REQUEST);
            }
            Utils.setJsonPayLoad(axis2MessageContext, jsonResponse);
        } else {
            Utils.setJsonPayLoad(axis2MessageContext, Utils.createJsonError("Message processor does not exist",
                    axis2MessageContext, Constants.NOT_FOUND));
        }

    }*/

    private static String getProcessorState(Boolean isDeactivated) {
        if (isDeactivated) {
            return INACTIVE_STATUS;
        }
        return ACTIVE_STATUS;
    }
}
