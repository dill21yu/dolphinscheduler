/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.dolphinscheduler.plugin.task.externalSystem;

import org.apache.dolphinscheduler.common.utils.JSONUtils;
import org.apache.dolphinscheduler.plugin.task.api.enums.ResourceType;
import org.apache.dolphinscheduler.plugin.task.api.parameters.AbstractParameters;
import org.apache.dolphinscheduler.plugin.task.api.parameters.resource.ExternalSystemResourceParameters;
import org.apache.dolphinscheduler.plugin.task.api.parameters.resource.ResourceParametersHelper;

import lombok.Data;

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
        BaseExternalSystemParams BaseExternalSystemParams = new BaseExternalSystemParams();
        parametersHelper.getResourceParameters(ResourceType.EXTERNAL_SYSTEM, externalSystemId);
        ExternalSystemResourceParameters externalSystemResourceParameters =
                (ExternalSystemResourceParameters) parametersHelper.getResourceParameters(ResourceType.EXTERNAL_SYSTEM,
                        externalSystemId);
        BaseExternalSystemParams = JSONUtils.parseObject(externalSystemResourceParameters.getConnectionParams(),
                BaseExternalSystemParams.class);
        return BaseExternalSystemParams;
    }

}
