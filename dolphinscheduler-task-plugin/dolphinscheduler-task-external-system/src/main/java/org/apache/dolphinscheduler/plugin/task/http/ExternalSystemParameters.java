package org.apache.dolphinscheduler.plugin.task.externalSystem;

import org.apache.dolphinscheduler.common.utils.JSONUtils;
import org.apache.dolphinscheduler.plugin.task.api.enums.ResourceType;
import org.apache.dolphinscheduler.plugin.task.api.parameters.AbstractParameters;

import java.util.List;

import lombok.Data;
import org.apache.dolphinscheduler.plugin.task.api.parameters.resource.ExternalSystemResourceParameters;
import org.apache.dolphinscheduler.plugin.task.api.parameters.resource.ResourceParametersHelper;

@Data
public class ExternalSystemParameters extends AbstractParameters {

    /**
     * externalSystem id
     */
    private int externalSystemId;

    private String connParams;

    private String externalTaskId;




    @Override
    public ResourceParametersHelper getResources() {
        ResourceParametersHelper resources = super.getResources();
        resources.put(ResourceType.EXTERNAL_SYSTEM, externalSystemId);
        return resources;
    }

    public boolean checkParameters() {
        // Add validation logic here
        return true;
    }

    public BaseExternalSystemParams generateExtendedContext(ResourceParametersHelper parametersHelper) {
        BaseExternalSystemParams BaseExternalSystemParams  = new BaseExternalSystemParams();
        parametersHelper.getResourceParameters(ResourceType.EXTERNAL_SYSTEM, externalSystemId);
        ExternalSystemResourceParameters externalSystemResourceParameters =
                (ExternalSystemResourceParameters) parametersHelper.getResourceParameters(ResourceType.EXTERNAL_SYSTEM, externalSystemId);
        BaseExternalSystemParams =  JSONUtils.parseObject(externalSystemResourceParameters.getConnectionParams(), BaseExternalSystemParams.class);
        return BaseExternalSystemParams;
    }


}
