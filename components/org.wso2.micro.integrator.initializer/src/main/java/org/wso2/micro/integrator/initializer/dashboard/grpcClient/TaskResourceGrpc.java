package org.wso2.micro.integrator.initializer.dashboard.grpcClient;

import org.apache.axiom.om.OMAttribute;
import org.apache.axiom.om.OMElement;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.synapse.Startup;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.config.SynapseConfigUtils;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.task.TaskDescription;
import org.apache.synapse.task.TaskDescriptionSerializer;

import javax.xml.namespace.QName;
import java.util.*;
import java.util.stream.Collectors;

import static org.wso2.carbon.inbound.endpoint.common.Constants.SUPER_TENANT_DOMAIN_NAME;

public class TaskResourceGrpc {

    private static List<Startup> getSearchResults(String searchKey) {
        SynapseConfiguration configuration = SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME);
        return configuration.getStartups().stream()
                .filter(artifact -> artifact.getName().toLowerCase().contains(searchKey))
                .collect(Collectors.toList());
    }
    public static org.wso2.micro.integrator.grpc.proto.TaskList populateSearchResults( String searchKey) {

        List<Startup> searchResultList = getSearchResults(searchKey);
        return setResponseBody(searchResultList);
    }

    private static org.wso2.micro.integrator.grpc.proto.TaskList setResponseBody(Collection<Startup> tasks) {

        org.wso2.micro.integrator.grpc.proto.TaskList.Builder taskListBuilder = org.wso2.micro.integrator.grpc.proto.TaskList.newBuilder().setCount(tasks.size());
        for (Startup task : tasks) {
            taskListBuilder.addName(task.getName());
        }
        return taskListBuilder.build();
    }

    public static org.wso2.micro.integrator.grpc.proto.TaskList populateTasksList() {

        SynapseConfiguration configuration = SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME);
        Collection<Startup> tasks = configuration.getStartups();
        return setResponseBody(tasks);
    }

/*    public static org.wso2.micro.integrator.grpc.proto.Task populateTaskData(String taskName) {


        SynapseConfiguration configuration = SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME);
        Startup task = configuration.getStartup(taskName);

        if (Objects.nonNull(task)) {
            SynapseEnvironment synapseEnvironment =
                    getSynapseEnvironment(axis2MessageContext.getConfigurationContext().getAxisConfiguration());
            TaskDescription description =
                    synapseEnvironment.getTaskManager().getTaskDescriptionRepository().getTaskDescription(task.getName());
            JSONObject jsonBody = getTaskAsJson(description);
            //Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
        }
    }*/

    private Map getProperties(Set xmlProperties) {
        Map<String, String> properties = new HashMap<>();
        Iterator<OMElement> propertiesItr = xmlProperties.iterator();

        while (propertiesItr.hasNext()) {
            OMElement propertyElem = propertiesItr.next();
            String propertyName = propertyElem.getAttributeValue(new QName("name"));
            OMAttribute valueAttr = propertyElem.getAttribute(new QName("value"));

            String value;
            if (valueAttr != null) {
                value = valueAttr.getAttributeValue();
            } else {
                value = propertyElem.getFirstElement().toString();
            }
            properties.put(propertyName, value);
        }
        return properties;
    }

    private SynapseEnvironment getSynapseEnvironment(AxisConfiguration axisCfg) {



        return (SynapseEnvironment) axisCfg.getParameter(SynapseConstants.SYNAPSE_ENV).getValue();
    }

    private org.wso2.micro.integrator.grpc.proto.Task getTaskAsJson(TaskDescription task) {

        org.wso2.micro.integrator.grpc.proto.Task.Builder taskBuilder = org.wso2.micro.integrator.grpc.proto.Task.newBuilder();
        taskBuilder.setName(task.getName());
        taskBuilder.setTaskGroup(task.getTaskGroup());
        taskBuilder.setTaskImplementation(task.getTaskImplClassName());

        String triggerType = "simple";

        if (task.getCronExpression() != null) {
            triggerType = "cron";
            taskBuilder.setCronExpression(task.getCronExpression());
        } else {
            taskBuilder.setTriggerCount(task.getCount());
            taskBuilder.setTriggerInterval(task.getInterval());
        }
        taskBuilder.setTriggerType(triggerType);
        taskBuilder.putAllProperties(getProperties(task.getXmlProperties()));
        taskBuilder.setConfiguration(TaskDescriptionSerializer.serializeTaskDescription(null, task).toString());


        return taskBuilder.build();
    }
}
