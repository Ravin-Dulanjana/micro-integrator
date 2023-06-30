package org.wso2.micro.integrator.initializer.dashboard.grpcClient;

import org.apache.synapse.config.SynapseConfigUtils;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.config.xml.TemplateMediatorSerializer;
import org.apache.synapse.config.xml.endpoints.TemplateSerializer;
import org.apache.synapse.endpoints.Template;
import org.apache.synapse.mediators.template.TemplateMediator;
import org.apache.synapse.mediators.template.TemplateParam;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.wso2.carbon.inbound.endpoint.common.Constants.SUPER_TENANT_DOMAIN_NAME;

public class TemplateResourceGrpc {

    /* Possible values for TEMPLATE_TYPE_PARAM  */
    private static final String ENDPOINT_TEMPLATE_TYPE = "endpoint";
    private static final String SEQUENCE_TEMPLATE_TYPE = "sequence";
    /* Name of the template parameter list */
    private static final String SEQUENCE_NAME = "sequenceName";
    private static final String SEQUENCE_TYPE = "sequenceType";
    private void handleTracing(String performedBy, String seqTempName, String traceState) {

        SynapseConfiguration configuration = SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME);
        TemplateMediator sequenceTemplate = configuration.getSequenceTemplate(seqTempName);
        if (sequenceTemplate != null) {
            JSONObject info = new JSONObject();
            info.put(SEQUENCE_NAME, seqTempName);
            info.put(SEQUENCE_TYPE, SEQUENCE_TEMPLATE_TYPE);
            GrpcUtils.handleTracing(performedBy, Constants.AUDIT_LOG_TYPE_SEQUENCE_TEMPLATE_TRACE,
                    Constants.SEQUENCE_TEMPLATE, info, sequenceTemplate.getAspectConfiguration(),
                    seqTempName, traceState);
        } else {
            GrpcUtils.createProtoError("Specified sequence template ('" + seqTempName + "') not found");
        }
        //return GRPCUtils.handleTracing() or return GRPCUtils.createProtoError()
    }

    public static org.wso2.micro.integrator.grpc.proto.TemplateList populateSearchResults(String searchKey) {

        SynapseConfiguration configuration = SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME);
        List<Template> epSearchResultList = configuration.getEndpointTemplates().values().stream()
                .filter(artifact -> artifact.getName().toLowerCase().contains(searchKey))
                .collect(Collectors.toList());
        List<TemplateMediator> seqSearchResultList = configuration.getSequenceTemplates().values().stream()
                .filter(artifact -> artifact.getName().toLowerCase().contains(searchKey))
                .collect(Collectors.toList());
        return setResponseBody(epSearchResultList, seqSearchResultList);
    }

    private static org.wso2.micro.integrator.grpc.proto.TemplateList setResponseBody(List<Template> epList, List<TemplateMediator> seqList) {

        int listLength = epList.size() + seqList.size();
        org.wso2.micro.integrator.grpc.proto.TemplateList.Builder templateListBuilder = org.wso2.micro.integrator.grpc.proto.TemplateList.newBuilder().setCount(listLength);

        for (Template epTemplate: epList) {
            org.wso2.micro.integrator.grpc.proto.TemplateSummary templateSummary = org.wso2.micro.integrator.grpc.proto.TemplateSummary.newBuilder().setEndpointTemplateSummary(getEndpointTemplateAsProtobuf(epTemplate)).build();
            templateListBuilder.addTemplateSummaries(templateSummary);
        }

        for (TemplateMediator seqTemplate: seqList) {
            org.wso2.micro.integrator.grpc.proto.TemplateSummary templateSummary = org.wso2.micro.integrator.grpc.proto.TemplateSummary.newBuilder().setSequenceTemplateSummary(getSequenceTemplateAsProtobuf(seqTemplate)).build();
            templateListBuilder.addTemplateSummaries(templateSummary);
        }

        return templateListBuilder.build();
    }

    public static org.wso2.micro.integrator.grpc.proto.TemplateList populateFullTemplateList() {

        SynapseConfiguration synapseConfiguration = SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME);
        Map<String, Template> endpointTemplateMap = synapseConfiguration.getEndpointTemplates();
        Map<String, TemplateMediator> sequenceTemplateMap = synapseConfiguration.getSequenceTemplates();
        return setResponseBody(endpointTemplateMap.values().stream().collect(Collectors.toList()), sequenceTemplateMap.values().stream().collect(Collectors.toList()));
    }

    private org.wso2.micro.integrator.grpc.proto.TemplateTypeList populateTemplateListByType(String templateType) {

        SynapseConfiguration synapseConfiguration = SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME);
        org.wso2.micro.integrator.grpc.proto.TemplateTypeList.Builder templateTypeListBuilder = org.wso2.micro.integrator.grpc.proto.TemplateTypeList.newBuilder();
        if (ENDPOINT_TEMPLATE_TYPE.equalsIgnoreCase(templateType)) {
            Map<String, Template> endpointTemplateMap = synapseConfiguration.getEndpointTemplates();
            templateTypeListBuilder.setCount(endpointTemplateMap.size());
            endpointTemplateMap.forEach((key, value) ->
                    addToTemplateTypeList(templateTypeListBuilder, value.getName()));

        } else if (SEQUENCE_TEMPLATE_TYPE.equalsIgnoreCase(templateType)) {
            Map<String, TemplateMediator> sequenceTemplateMap = synapseConfiguration.getSequenceTemplates();
            templateTypeListBuilder.setCount(sequenceTemplateMap.size());
            sequenceTemplateMap.forEach((key, value) ->
                    addToTemplateTypeList(templateTypeListBuilder, value.getName()));

        }
        return templateTypeListBuilder.build();
    }

    private void addToTemplateTypeList(org.wso2.micro.integrator.grpc.proto.TemplateTypeList.Builder templateListBuilder, String name) {

        org.wso2.micro.integrator.grpc.proto.TemplateName templateName = org.wso2.micro.integrator.grpc.proto.TemplateName.newBuilder().setName(name).build();
        templateListBuilder.addTemplateNames(templateName);
    }

    private org.wso2.micro.integrator.grpc.proto.TemplateSummary populateTemplateData(String templateName, String templateType) {
        SynapseConfiguration synapseConfiguration = SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME);
        org.wso2.micro.integrator.grpc.proto.TemplateSummary templateObject = null;
        if (ENDPOINT_TEMPLATE_TYPE.equalsIgnoreCase(templateType)) {
            Template endpointTemplate = synapseConfiguration.getEndpointTemplate(templateName);
            if (Objects.nonNull(endpointTemplate)) {
                templateObject = org.wso2.micro.integrator.grpc.proto.TemplateSummary.newBuilder().setEndpointTemplateSummary(getEndpointTemplateAsProtobuf(endpointTemplate)).build();
            }
        } else if (SEQUENCE_TEMPLATE_TYPE.equalsIgnoreCase(templateType)) {
            TemplateMediator sequenceTemplate = synapseConfiguration.getSequenceTemplate(templateName);
            if (Objects.nonNull(sequenceTemplate)) {
                templateObject = org.wso2.micro.integrator.grpc.proto.TemplateSummary.newBuilder().setSequenceTemplateSummary(getSequenceTemplateAsProtobuf(sequenceTemplate)).build();
            }
        }
        return templateObject;
    }

    private static org.wso2.micro.integrator.grpc.proto.EndpointTemplateSummary getEndpointTemplateAsProtobuf(Template endpointTemplate) {
        org.wso2.micro.integrator.grpc.proto.EndpointTemplateSummary.Builder endpointTemplateSummary = org.wso2.micro.integrator.grpc.proto.EndpointTemplateSummary.newBuilder();
        endpointTemplateSummary.setName(endpointTemplate.getName());
        endpointTemplateSummary.addAllParameters(endpointTemplate.getParameters());
        endpointTemplateSummary.setConfiguration(new TemplateSerializer().
                serializeEndpointTemplate(endpointTemplate, null).toString());
        endpointTemplateSummary.setTemplateType(SEQUENCE_TEMPLATE_TYPE);
        return endpointTemplateSummary.build();
    }

    private static org.wso2.micro.integrator.grpc.proto.SequenceTemplateSummary getSequenceTemplateAsProtobuf(TemplateMediator sequenceTemplate) {
        org.wso2.micro.integrator.grpc.proto.SequenceTemplateSummary.Builder sequenceTemplateSummary = org.wso2.micro.integrator.grpc.proto.SequenceTemplateSummary.newBuilder();
        sequenceTemplateSummary.setName(sequenceTemplate.getName());
        for(TemplateParam templateParam : sequenceTemplate.getParameters()) {
            sequenceTemplateSummary.addParameters(getTemplateParam(templateParam));
        }
        sequenceTemplateSummary.setConfiguration(new TemplateMediatorSerializer().
                serializeMediator(null, sequenceTemplate).toString());
        sequenceTemplateSummary.setTemplateType(SEQUENCE_TEMPLATE_TYPE);
        return sequenceTemplateSummary.build();
    }

    private static org.wso2.micro.integrator.grpc.proto.TemplateParam getTemplateParam(TemplateParam templateParam) {
        org.wso2.micro.integrator.grpc.proto.TemplateParam.Builder templateParamBuilder = org.wso2.micro.integrator.grpc.proto.TemplateParam.newBuilder();
        templateParamBuilder.setName(templateParam.getName());
        templateParamBuilder.setDefaultValue((String) templateParam.getDefaultValue());
        templateParamBuilder.setIsMandatory(templateParam.isMandatory());
        return templateParamBuilder.build();
    }
}
