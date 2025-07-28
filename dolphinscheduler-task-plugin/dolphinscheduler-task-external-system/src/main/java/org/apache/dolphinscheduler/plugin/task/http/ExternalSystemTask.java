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

import org.apache.dolphinscheduler.common.model.OkHttpRequestHeaderContentType;
import org.apache.dolphinscheduler.common.model.OkHttpRequestHeaders;
import org.apache.dolphinscheduler.common.model.OkHttpResponse;
import org.apache.dolphinscheduler.common.utils.JSONUtils;
import org.apache.dolphinscheduler.common.utils.OkHttpUtils;
import org.apache.dolphinscheduler.plugin.task.api.AbstractTask;
import org.apache.dolphinscheduler.plugin.task.api.TaskCallBack;
import org.apache.dolphinscheduler.plugin.task.api.TaskConstants;
import org.apache.dolphinscheduler.plugin.task.api.TaskException;
import org.apache.dolphinscheduler.plugin.task.api.TaskExecutionContext;
import org.apache.dolphinscheduler.plugin.task.api.model.Property;
import org.apache.dolphinscheduler.plugin.task.api.parameters.AbstractParameters;
import org.apache.dolphinscheduler.plugin.task.api.utils.ParameterUtils;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import com.jayway.jsonpath.JsonPath;

@Slf4j
public class ExternalSystemTask extends AbstractTask {

    // private final HashSet<String> waitingStateSet = Sets.newHashSet("RUNNING");
    private static final String EXTERNAL_TASK_ID = "externalTaskId";
    private static final String INTERNAL_TASK_INSTANCE_ID = "taskInstanceId";
    private Boolean traceEnabled = true;

    private ExternalSystemParameters externalSystemParameters;
    private BaseExternalSystemParams baseExternalSystemParams;
    private TaskExecutionContext taskExecutionContext;
    private String externalTaskInstanceId;
    private String accessToken;
    private Map<String, String> parameterMap = new HashMap<>();
    private Set<String> successStatusCache = new HashSet<>();
    private Set<String> failureStatusCache = new HashSet<>();

    /**
     * 构造函数：初始化任务执行上下文。
     *
     * @param taskExecutionContext 任务执行上下文
     */
    public ExternalSystemTask(TaskExecutionContext taskExecutionContext) {
        super(taskExecutionContext);
        this.taskExecutionContext = taskExecutionContext;
        this.externalSystemParameters =
                JSONUtils.parseObject(taskExecutionContext.getTaskParams(), ExternalSystemParameters.class);
        baseExternalSystemParams =
                externalSystemParameters.generateExtendedContext(taskExecutionContext.getResourceParametersHelper());
    }

    @Override
    public void init() {
        externalSystemParameters = JSONUtils.parseObject(
                taskExecutionContext.getTaskParams(),
                ExternalSystemParameters.class);
        log.info("Initialize external system task params {}",
                JSONUtils.toPrettyJsonString(externalSystemParameters));

        if (externalSystemParameters == null || !externalSystemParameters.checkParameters()) {
            throw new RuntimeException("external system task params is not valid");
        }

        // 初始化参数映射
        initParameterMap();
        initStatusCache();

    }

    @Override
    public void handle(TaskCallBack taskCallBack) throws TaskException {
        try {

            // BaseExternalSystemParams = (BaseExternalSystemParams) DataSourceUtils.buildConnectionParams(dbType,
            // sqlTaskExecutionContext.getConnectionParams());
            // 1. 认证获取token
            accessToken = AuthenticationUtils.authenticateAndGetToken(baseExternalSystemParams.getAuthConfig());

            // 2. 提交任务
            submitExternalTask();
            TimeUnit.SECONDS.sleep(10);
            // 3. 跟踪任务状态
            trackExternalTaskStatus();

        } catch (Exception e) {
            log.error("external system task error", e);
            setExitStatusCode(TaskConstants.EXIT_CODE_FAILURE);
            throw new TaskException("Execute external system task failed", e);
        }
    }

    @Override
    public void cancel() throws TaskException {
        try {
            cancelTaskInstance();
        } catch (Exception e) {
            throw new TaskException("cancel external system task error", e);
        } finally {
            setExitStatusCode(TaskConstants.EXIT_CODE_KILL);
        }
    }

    /**
     * 提交任务到外部系统
     */
    private void submitExternalTask() throws TaskException {
        try {
            BaseExternalSystemParams.InterfaceConfig submitConfig = baseExternalSystemParams.getSubmitInterface();

            // 替换参数占位符
            String url = replaceParameterPlaceholders(submitConfig.getUrl());

            OkHttpRequestHeaders headers = new OkHttpRequestHeaders();
            headers.setOkHttpRequestHeaderContentType(OkHttpRequestHeaderContentType.APPLICATION_JSON);

            Map<String, String> headeMap = new HashMap<>();
            Map<String, Object> requestBody = new HashMap<>();
            Map<String, Object> requestParams = new HashMap<>();
            headeMap.put("Authorization", this.accessToken);

            // 处理参数
            for (BaseExternalSystemParams.RequestParameter param : submitConfig.getParameters()) {
                String value = replaceParameterPlaceholders(param.getParamValue());
                ParameterUtils.convertParameterPlaceholders(value, parameterMap);// todo 可以替换内置参数
                switch (param.getLocation().name()) {
                    case "HEADER":
                        headeMap.put(param.getParamName(), value);
                        break;
                    case "BODY":
                        requestBody = JSONUtils.parseObject(replaceParameterPlaceholders(submitConfig.getHttpBody()),
                                Map.class);
                        break;
                    case "PARAM":
                        requestParams.put(param.getParamName(), value);
                        break;
                }
            }
            if (!headeMap.isEmpty()) {
                headers.setHeaders(headeMap);
            }
            // headers.setOkHttpRequestHeaderContentType("application/json");

            OkHttpResponse response;
            if (BaseExternalSystemParams.HttpMethod.POST.equals(submitConfig.getMethod())) {
                response = OkHttpUtils.post(url, headers, requestParams, requestBody, 120000, 120000, 120000);
            } else if (BaseExternalSystemParams.HttpMethod.PUT.equals(submitConfig.getMethod())) {
                response = OkHttpUtils.put(url, headers, requestBody, 120000, 120000, 120000);
            } else {
                response = OkHttpUtils.get(url, headers, requestParams, 120000, 120000, 120000);
            }

            if (response.getStatusCode() != 200) {
                throw new TaskException("Submit task failed: " + response.getBody());
            }

            // 解析响应获取taskInstanceId
            parseSubmitResponse(response.getBody());

            log.info("Task submitted successfully, external task instance id: {}", externalTaskInstanceId);

        } catch (Exception e) {
            log.error("Submit task failed", e);
            throw new TaskException("Submit task failed", e);
        }
    }

    /**
     * 跟踪任务状态
     */
    /**
     * 跟踪任务状态
     */
    private void trackExternalTaskStatus() throws TaskException {
        try {
            String status;
            do {
                status = pollTaskStatus();

                if (successStatusCache.contains(status)) {
                    setExitStatusCode(TaskConstants.EXIT_CODE_SUCCESS);
                    log.info("External task completed successfully with status: {}", status);
                    return;
                } else if (failureStatusCache.contains(status)) {
                    setExitStatusCode(TaskConstants.EXIT_CODE_FAILURE);
                    log.error("External task failed with status: {}", status);
                    return;
                }

                // 等待10秒后再次检查
                TimeUnit.SECONDS.sleep(10);

            } while (traceEnabled); // 无限循环，直到成功或失败

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TaskException("Task status tracking interrupted", e);
        } catch (Exception e) {
            log.error("Track task status failed", e);
            setExitStatusCode(TaskConstants.EXIT_CODE_FAILURE);
            throw new TaskException("Track task status failed", e);
        }
    }

    /**
     * 轮询任务状态
     */
    public String pollTaskStatus() throws TaskException {
        try {
            BaseExternalSystemParams.PollingInterfaceConfig pollConfig =
                    baseExternalSystemParams.getPollStatusInterface();

            String url = replaceParameterPlaceholders(pollConfig.getUrl());

            OkHttpRequestHeaders headers = new OkHttpRequestHeaders();
            Map<String, String> headeMap = new HashMap<>();
            Map<String, Object> requestBody = new HashMap<>();
            Map<String, Object> requestParams = new HashMap<>();
            headers.setOkHttpRequestHeaderContentType(OkHttpRequestHeaderContentType.APPLICATION_JSON);
            headeMap.put("Authorization", this.accessToken);
            // 处理参数
            for (BaseExternalSystemParams.RequestParameter param : pollConfig.getParameters()) {
                String value = replaceParameterPlaceholders(param.getParamValue());

                switch (param.getLocation().name()) {
                    case "HEADER":
                        headeMap.put(param.getParamName(), value);
                        break;
                    case "BODY":
                        requestBody = JSONUtils.parseObject(replaceParameterPlaceholders(pollConfig.getHttpBody()),
                                Map.class);
                        break;
                    case "PARAM":
                        requestParams.put(param.getParamName(), value);
                        break;
                }
            }
            if (!headeMap.isEmpty()) {
                headers.setHeaders(headeMap);
            }

            OkHttpResponse response;
            if (BaseExternalSystemParams.HttpMethod.POST.equals(pollConfig.getMethod())) {
                response = OkHttpUtils.post(url, headers, requestParams, requestBody, 30000, 30000, 30000);
            } else if (BaseExternalSystemParams.HttpMethod.PUT.equals(pollConfig.getMethod())) {
                response = OkHttpUtils.put(url, headers, requestBody, 30000, 30000, 30000);
            } else {
                response = OkHttpUtils.get(url, headers, requestParams, 30000, 30000, 30000);
            }

            if (response.getStatusCode() != 200) {
                throw new TaskException("polling task failed: " + response.getBody());
            }

            // 使用JsonPath解析状态
            String statusPath = pollConfig.getPollingSuccessConfig().getSuccessField();// todo 有单个path
            Object statusObj = JsonPath.read(response.getBody(), statusPath);
            log.info("PollTaskStatus successfully, external task instance status: {}", statusObj.toString());

            return statusObj.toString().replace("\"", "");

        } catch (Exception e) {
            log.error("Poll task status failed", e);
            throw new TaskException("Poll task status failed", e);
        }
    }

    private void cancelTaskInstance() throws TaskException {
        try {
            traceEnabled = false;
            BaseExternalSystemParams.InterfaceConfig stopConfig = baseExternalSystemParams.getStopInterface();
            log.info("start cancel External System TaskInstance");
            String url = replaceParameterPlaceholders(stopConfig.getUrl());

            OkHttpRequestHeaders headers = new OkHttpRequestHeaders();
            Map<String, String> headeMap = new HashMap<>();
            Map<String, Object> requestBody = new HashMap<>();
            Map<String, Object> requestParams = new HashMap<>();
            headers.setOkHttpRequestHeaderContentType(OkHttpRequestHeaderContentType.APPLICATION_JSON);
            headeMap.put("Authorization", this.accessToken);

            // 处理参数
            for (BaseExternalSystemParams.RequestParameter param : stopConfig.getParameters()) {
                String value = replaceParameterPlaceholders(param.getParamValue());

                switch (param.getLocation().name()) {
                    case "HEADER":
                        headeMap.put(param.getParamName(), value);
                        break;
                    case "BODY":
                        requestBody = JSONUtils.parseObject(replaceParameterPlaceholders(stopConfig.getHttpBody()),
                                Map.class);
                        break;
                    case "PARAM":
                        requestParams.put(param.getParamName(), value);
                        break;
                }
            }
            if (!headeMap.isEmpty()) {
                headers.setHeaders(headeMap);
            }

            OkHttpResponse response;
            switch (stopConfig.getMethod()) {
                case POST:
                    response = OkHttpUtils.post(url, headers, requestParams, requestBody, 30000, 30000, 30000);
                    break;
                case PUT:
                    response = OkHttpUtils.put(url, headers, requestBody, 30000, 30000, 30000);
                    break;
                case GET:
                    response = OkHttpUtils.get(url, headers, requestParams, 30000, 30000, 30000);
                    break;
                default:
                    throw new TaskException("Unsupported HTTP method: " + stopConfig.getMethod());
            }

            if (response.getStatusCode() != 200) {
                throw new TaskException("polling task failed: " + response.getBody());
            }
            log.info("Cancel task result: {}", response.getBody());

        } catch (Exception e) {
            log.error("Cancel task failed", e);
            throw new TaskException("Cancel task failed", e);
        }
    }

    /**
     * 替换参数占位符
     */
    public String replaceParameterPlaceholders(String template) {
        if (StringUtils.isEmpty(template)) {
            return template;
        }

        StringBuilder result = new StringBuilder(template);
        for (Map.Entry<String, String> entry : parameterMap.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            int index;
            while ((index = result.indexOf(placeholder)) != -1) {
                result.replace(index, index + placeholder.length(), entry.getValue());
            }
        }

        return result.toString();
    }

    /**
     * 解析提交响应
     */
    private void parseSubmitResponse(String responseBody) throws TaskException {
        try {
            // 查找fieldMappings中的taskInstanceId映射
            for (BaseExternalSystemParams.FieldMapping mapping : baseExternalSystemParams.getFieldMappings()) {
                if (INTERNAL_TASK_INSTANCE_ID.equals(mapping.getInternalField())) {
                    Object value = JsonPath.read(responseBody, mapping.getExternalField());
                    externalTaskInstanceId = String.valueOf(value);
                    parameterMap.put(INTERNAL_TASK_INSTANCE_ID, externalTaskInstanceId);
                    break;
                }
            }

            if (StringUtils.isEmpty(externalTaskInstanceId)) {
                throw new TaskException("Failed to extract taskInstanceId from submit response");
            }

        } catch (Exception e) {
            log.error("submit responseBody:{},Parse response failed:{}", responseBody, e);
            throw new TaskException("Parse submit response failed", e);
        }
    }

    @Override
    public AbstractParameters getParameters() {
        return this.externalSystemParameters;
    }

    /**
     * 初始化参数映射
     */
    private void initParameterMap() {
        Map<String, Property> prepareParamsMap = taskExecutionContext.getPrepareParamsMap();
        if (prepareParamsMap != null) {
            for (Map.Entry<String, Property> entry : prepareParamsMap.entrySet()) {
                parameterMap.put(entry.getKey(), entry.getValue().getValue());
            }
        }
        if (externalSystemParameters.getExternalTaskId() != null) {

            parameterMap.put(EXTERNAL_TASK_ID, externalSystemParameters.getExternalTaskId());
        }
    }

    private void initStatusCache() {
        BaseExternalSystemParams.PollingSuccessConfig successConfig =
                baseExternalSystemParams.getPollStatusInterface().getPollingSuccessConfig();

        if (successConfig != null && successConfig.getSuccessValue() != null) {
            try {
                String successValueString = successConfig.getSuccessValue();
                String[] successValues = successValueString.split(",");
                for (String successValue : successValues) {
                    successStatusCache.add(successValue);
                }
                log.info("trackExternalTaskStatus successValues is :{}", successStatusCache);

            } catch (NullPointerException e) {
                log.error("Error: successValue is null");
            }
        }
        BaseExternalSystemParams.PollingFailureConfig failureConfig =
                baseExternalSystemParams.getPollStatusInterface().getPollingFailureConfig();
        if (failureConfig != null && failureConfig.getFailureField() != null) {
            try {
                String failureValueString = failureConfig.getFailureValue();
                String[] failureValues = failureValueString.split(",");
                for (String failureValue : failureValues) {
                    failureStatusCache.add(failureValue);
                }
                log.info("trackExternalTaskStatus failureValues is :{}", failureStatusCache);
            } catch (NullPointerException e) {
                log.error("Error: failureStatus is null");
            }
        }
    }

}
