package org.wso2.micro.integrator.initializer.dashboard.grpcClient;

import org.apache.synapse.Mediator;
import org.apache.synapse.MessageContext;
import org.apache.synapse.config.SynapseConfigUtils;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.config.xml.SequenceMediatorSerializer;
import org.apache.synapse.mediators.base.SequenceMediator;

import java.util.*;
import java.util.stream.Collectors;

import static org.wso2.carbon.inbound.endpoint.common.Constants.SUPER_TENANT_DOMAIN_NAME;

public class SequenceResourceGrpc {

    private static List<SequenceMediator> getSearchResults(String searchKey) {
        SynapseConfiguration configuration = SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME);
        return configuration.getDefinedSequences().values().stream()
                .filter(artifact -> artifact.getName().toLowerCase().contains(searchKey))
                .collect(Collectors.toList());
    }

    public static org.wso2.micro.integrator.grpc.proto.SequenceList populateSearchResults(String searchKey) {
        List<SequenceMediator> searchResultList = getSearchResults(searchKey);
        return setResponseBody(searchResultList);
    }

    private static org.wso2.micro.integrator.grpc.proto.SequenceList setResponseBody(Collection<SequenceMediator> sequenceMediatorCollection) {

        org.wso2.micro.integrator.grpc.proto.SequenceList.Builder sequenceListBuilder = org.wso2.micro.integrator.grpc.proto.SequenceList.newBuilder().setCount(sequenceMediatorCollection.size());

        for (SequenceMediator sequence: sequenceMediatorCollection) {
            org.wso2.micro.integrator.grpc.proto.SequenceSummary.Builder sequenceSummaryBuilder = org.wso2.micro.integrator.grpc.proto.SequenceSummary.newBuilder();
            sequenceSummaryBuilder.setName(sequence.getName());
            sequenceSummaryBuilder.setContainer(sequence.getArtifactContainerName());
            String statisticState = sequence.getAspectConfiguration().isStatisticsEnable() ? Constants.ENABLED : Constants.DISABLED;
            sequenceSummaryBuilder.setStatisticState(statisticState);
            String tracingState = sequence.getAspectConfiguration().isTracingEnabled() ? Constants.ENABLED : Constants.DISABLED;
            sequenceSummaryBuilder.setTracingState(tracingState);
            sequenceListBuilder.addSequenceSummaries(sequenceSummaryBuilder.build());
        }

        return sequenceListBuilder.build();
    }

    public static org.wso2.micro.integrator.grpc.proto.SequenceList populateSequenceList() {

        SynapseConfiguration configuration = SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME);

        Map<String, SequenceMediator> sequenceMediatorMap = configuration.getDefinedSequences();
        Collection<SequenceMediator> sequenceCollection = sequenceMediatorMap.values();
        return setResponseBody(sequenceCollection);
    }

    public static org.wso2.micro.integrator.grpc.proto.Sequence populateSequenceData(String sequenceName) {

        org.wso2.micro.integrator.grpc.proto.Sequence sequenceProtoBuf = getSequenceByName(sequenceName);
        return sequenceProtoBuf;

        /*if (Objects.nonNull(jsonBody)) {
            Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
        } else {
            axis2MessageContext.setProperty(Constants.HTTP_STATUS_CODE, Constants.NOT_FOUND);
        }*/
    }

    private static org.wso2.micro.integrator.grpc.proto.Sequence getSequenceByName(String sequenceName) {

        SynapseConfiguration configuration = SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME);
        SequenceMediator sequence = configuration.getDefinedSequences().get(sequenceName);
        return convertSequenceToProtoBuf(sequence);
    }

    private static org.wso2.micro.integrator.grpc.proto.Sequence convertSequenceToProtoBuf(SequenceMediator sequenceMediator) {

        if (Objects.isNull(sequenceMediator)) {
            return null;
        }

        org.wso2.micro.integrator.grpc.proto.Sequence.Builder sequenceBuilder = org.wso2.micro.integrator.grpc.proto.Sequence.newBuilder();
        sequenceBuilder.setName(sequenceMediator.getName());
        sequenceBuilder.setContainer(sequenceMediator.getArtifactContainerName());

        String statisticState = sequenceMediator.getAspectConfiguration().isStatisticsEnable() ? Constants.ENABLED : Constants.DISABLED;
        sequenceBuilder.setStatisticsState(statisticState);

        String tracingState = sequenceMediator.getAspectConfiguration().isTracingEnabled() ? Constants.ENABLED : Constants.DISABLED;
        sequenceBuilder.setTracingState(tracingState);

        List<Mediator> mediators = sequenceMediator.getList();
        String []mediatorTypes = new String[mediators.size()];
        for (int i = 0; i < mediators.size(); i++) {
            mediatorTypes[i] = mediators.get(i).getType();
        }
        sequenceBuilder.addAllMethods(Arrays.asList(mediatorTypes));
        sequenceBuilder.setConfiguration(new SequenceMediatorSerializer().serializeSpecificMediator(sequenceMediator).toString());

        return sequenceBuilder.build();
    }
}
