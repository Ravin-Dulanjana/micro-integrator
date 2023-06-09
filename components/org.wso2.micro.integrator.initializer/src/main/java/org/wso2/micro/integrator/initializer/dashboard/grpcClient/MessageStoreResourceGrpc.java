package org.wso2.micro.integrator.initializer.dashboard.grpcClient;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.config.SynapseConfigUtils;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.config.xml.MessageStoreSerializer;
import org.apache.synapse.message.store.MessageStore;
import org.apache.synapse.message.store.impl.jdbc.JDBCMessageStore;
import org.apache.synapse.message.store.impl.jms.JmsStore;
import org.apache.synapse.message.store.impl.memory.InMemoryStore;
import org.apache.synapse.message.store.impl.rabbitmq.RabbitMQStore;
import org.apache.synapse.message.store.impl.resequencer.ResequenceMessageStore;

import java.util.*;
import java.util.stream.Collectors;

import static org.wso2.carbon.inbound.endpoint.common.Constants.SUPER_TENANT_DOMAIN_NAME;

public class MessageStoreResourceGrpc {

    private static final Log LOG = LogFactory.getLog(MessageStoreResourceGrpc.class);
    private static final String JDBCMESSAGESTORE_TYPE = "jdbc-message-store";
    private static final String JMSSTORE_TYPE = "jms-message-store";
    private static final String INMEMORYSTORE_TYPE = "in-memory-message-store";
    private static final String RABBITMQSTORE_TYPE = "rabbitmq-message-store";
    private static final String RESEQUENCEMESSAGESTORE_TYPE = "resequence-message-store";
    private static final String CUSTOMSTORE_TYPE = "custom-message-store";

    private List<MessageStore> getSearchResults(String searchKey) {
        SynapseConfiguration configuration = SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME);
        return configuration.getMessageStores().values().stream()
                .filter(artifact -> artifact.getName().toLowerCase().contains(searchKey))
                .collect(Collectors.toList());
    }

    private void populateSearchResults(String searchKey) {

        List<MessageStore> searchResultList = getSearchResults(searchKey);
        setGrpcResponseBody(searchResultList);
    }

    private void setGrpcResponseBody(Collection<MessageStore> storeList) {

        org.wso2.micro.integrator.grpc.proto.MessageStoreList.Builder messageStoreListBuilder = org.wso2.micro.integrator.grpc.proto.MessageStoreList.newBuilder().setCount(storeList.size());

        for (MessageStore store : storeList) {
            messageStoreListBuilder.addMessageStoreSummaries(getProtoMessageStore(store));
        }
        //Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
    }

    private void populateMessageStoreList(SynapseConfiguration synapseConfiguration) {
        Map<String, MessageStore> storeMap = synapseConfiguration.getMessageStores();
        Collection<MessageStore> storeList = storeMap.values();
        setGrpcResponseBody(storeList);
    }

    private void populateMessageStoreData(SynapseConfiguration synapseConfiguration, String messageStoreName) {
        MessageStore messageStore = synapseConfiguration.getMessageStore(messageStoreName);
        if (Objects.nonNull(messageStore)) {
            org.wso2.micro.integrator.grpc.proto.MessageStore messageStorePB = getMessageStoreAsProtoBuf(messageStore);
            //send message processor data
        } else {
            LOG.warn("Message store " + messageStoreName + " does not exist");
            GrpcUtils.createProtoError("Specified message store ('" + messageStoreName + "') not found");
        }
    }

    private org.wso2.micro.integrator.grpc.proto.MessageStoreSummary getProtoMessageStore(MessageStore messageStore) {
        org.wso2.micro.integrator.grpc.proto.MessageStoreSummary.Builder messageStoreSummaryBuilder = org.wso2.micro.integrator.grpc.proto.MessageStoreSummary.newBuilder();
        messageStoreSummaryBuilder.setName(messageStore.getName());
        messageStoreSummaryBuilder.setType(getStoreType(messageStore));
        messageStoreSummaryBuilder.setSize(messageStore.size());
        return messageStoreSummaryBuilder.build();
    }

    private String getStoreType(MessageStore messageStore) {

        if (messageStore instanceof ResequenceMessageStore) {
            return RESEQUENCEMESSAGESTORE_TYPE;
        } else if (messageStore instanceof JDBCMessageStore) {
            return JDBCMESSAGESTORE_TYPE;
        } else if (messageStore instanceof JmsStore) {
            return JMSSTORE_TYPE;
        } else if (messageStore instanceof InMemoryStore) {
            return INMEMORYSTORE_TYPE;
        } else if (messageStore instanceof RabbitMQStore) {
            return RABBITMQSTORE_TYPE;
        } else {
            return CUSTOMSTORE_TYPE;
        }
    }

    private org.wso2.micro.integrator.grpc.proto.MessageStore getMessageStoreAsProtoBuf(MessageStore messageStore) {
        org.wso2.micro.integrator.grpc.proto.MessageStore.Builder messageStoreBuilder = org.wso2.micro.integrator.grpc.proto.MessageStore.newBuilder();
        messageStoreBuilder.setName(messageStore.getName());
        messageStoreBuilder.setContainer(messageStore.getArtifactContainerName());
        messageStoreBuilder.setFileName(messageStore.getFileName());
        messageStoreBuilder.setStoreSize(messageStore.size());
        messageStoreBuilder.setConfiguration(MessageStoreSerializer.serializeMessageStore(null, messageStore, true).toString());
        //parameters

        return messageStoreBuilder.build();
    }
}
