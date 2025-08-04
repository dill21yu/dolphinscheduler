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

    private static final String EXTERNAL_TASK_ID = "externalTaskId";// todo
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

    public ExternalSystemTask(TaskExecutionContext taskExecutionContext) {
        super(taskExecutionContext);
        this.taskExecutionContext = taskExecutionContext;
        this.externalSystemParameters =
                JSONUtils.parseObject(taskExecutionContext.getTaskParams(), ExternalSystemParameters.class);
        baseExternalSystemParams =
                externalSystemParameters.generateExtendedContext(taskExecutionContext.getResourceParametersHelper());
        accessToken = baseExternalSystemParams.getTokenPrefix(baseExternalSystemParams.getAuthConfig().getAuthType())
                + externalSystemParameters.getToken(taskExecutionContext.getResourceParametersHelper());
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
            submitExternalTask();
            TimeUnit.SECONDS.sleep(10);
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

    private void submitExternalTask() throws TaskException {
        try {
            BaseExternalSystemParams.InterfaceConfig submitConfig = baseExternalSystemParams.getSubmitInterface();
            String url = replaceParameterPlaceholders(baseExternalSystemParams.getCompleteUrl(submitConfig.getUrl()));
            Map<String, String> headers = buildHeaders(submitConfig);
            buildAuthHeader(accessToken, headers);
            Map<String, Object> requestBody = buildRequestBody(submitConfig);
            Map<String, Object> requestParams = buildRequestParams(submitConfig);

            OkHttpResponse response = executeRequest(submitConfig.getMethod(), url, headers, requestParams, requestBody,
                    120000, 120000, 120000);

            if (response.getStatusCode() != 200) {
                throw new TaskException("Submit task failed: " + response.getBody());
            }

            parseSubmitResponse(response.getBody());
            log.info("Task submitted successfully, external task instance id: {}", externalTaskInstanceId);
        } catch (Exception e) {
            log.error("Submit task failed", e);
            throw new TaskException("Submit task failed", e);
        }
    }

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

                TimeUnit.SECONDS.sleep(10);
            } while (traceEnabled);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TaskException("Task status tracking interrupted", e);
        } catch (Exception e) {
            log.error("Track task status failed", e);
            setExitStatusCode(TaskConstants.EXIT_CODE_FAILURE);
            throw new TaskException("Track task status failed", e);
        }
    }

    private String pollTaskStatus() throws TaskException {
        try {
            BaseExternalSystemParams.PollingInterfaceConfig pollConfig =
                    baseExternalSystemParams.getPollStatusInterface();
            String url = replaceParameterPlaceholders(baseExternalSystemParams.getCompleteUrl(pollConfig.getUrl()));
            Map<String, String> headers = buildHeaders(pollConfig);
            buildAuthHeader(accessToken, headers);
            Map<String, Object> requestBody = buildRequestBody(pollConfig);
            Map<String, Object> requestParams = buildRequestParams(pollConfig);

            OkHttpResponse response = executeRequest(pollConfig.getMethod(), url, headers, requestParams, requestBody,
                    30000, 30000, 30000);

            if (response.getStatusCode() != 200) {
                throw new TaskException("polling task failed: " + response.getBody());
            }

            String statusPath = pollConfig.getPollingSuccessConfig().getSuccessField();
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
            String url = replaceParameterPlaceholders(baseExternalSystemParams.getCompleteUrl(stopConfig.getUrl()));
            Map<String, String> headers = buildHeaders(stopConfig);
            buildAuthHeader(accessToken, headers);
            Map<String, Object> requestBody = buildRequestBody(stopConfig);
            Map<String, Object> requestParams = buildRequestParams(stopConfig);

            OkHttpResponse response = executeRequest(stopConfig.getMethod(), url, headers, requestParams, requestBody,
                    30000, 30000, 30000);

            if (response.getStatusCode() != 200) {
                throw new TaskException("polling task failed: " + response.getBody());
            }
            log.info("Cancel task result: {}", response.getBody());
        } catch (Exception e) {
            log.error("Cancel task failed", e);
            throw new TaskException("Cancel task failed", e);
        }
    }

    private OkHttpResponse executeRequest(BaseExternalSystemParams.HttpMethod method, String url,
                                          Map<String, String> headers, Map<String, Object> requestParams,
                                          Map<String, Object> requestBody, int connectTimeout, int readTimeout,
                                          int writeTimeout) throws TaskException {
        int maxRetries = 3;
        int retryCount = 0;
        while (retryCount < maxRetries) {
            OkHttpRequestHeaders okHttpRequestHeaders = new OkHttpRequestHeaders();
            okHttpRequestHeaders.setHeaders(headers);
            okHttpRequestHeaders.setOkHttpRequestHeaderContentType(OkHttpRequestHeaderContentType.APPLICATION_JSON);
            try {
                switch (method) {
                    case POST:
                        return OkHttpUtils.post(url, okHttpRequestHeaders, requestParams, requestBody, connectTimeout,
                                readTimeout, writeTimeout);
                    case PUT:
                        return OkHttpUtils.put(url, okHttpRequestHeaders, requestBody, connectTimeout, readTimeout,
                                writeTimeout);
                    case GET:
                        return OkHttpUtils.get(url, okHttpRequestHeaders, requestParams, connectTimeout, readTimeout,
                                writeTimeout);
                    default:
                        throw new TaskException("Unsupported HTTP method: " + method);
                }
            } catch (Exception e) {
                retryCount++;
                log.warn("Request failed, retrying... (attempt {}/{})", retryCount, maxRetries, e);
                if (retryCount >= maxRetries) {
                    throw new TaskException("Request failed after " + maxRetries + " retries", e);
                }
                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new TaskException("Request retry interrupted", ie);
                }
            }
        }
        return null; // This line should never be reached
    }

    private void buildAuthHeader(String accessToken, Map<String, String> headers) {
        headers.put("Authorization", accessToken);
    }
    private Map<String, String> buildHeaders(BaseExternalSystemParams.InterfaceConfig config) {
        Map<String, String> requestParams = new HashMap<>();
        for (BaseExternalSystemParams.RequestParameter param : config.getParameters()) {
            if (param.getLocation().equals(BaseExternalSystemParams.ParamLocation.HEADER)) {
                requestParams.put(param.getParamName(), replaceParameterPlaceholders(param.getParamValue()));
            }
        }
        return requestParams;
    }
    private Map<String, Object> buildRequestBody(BaseExternalSystemParams.InterfaceConfig config) {
        Map<String, Object> requestBody = new HashMap<>();
        if (config.getBody() != null) {
            requestBody = JSONUtils.parseObject(replaceParameterPlaceholders(config.getBody()), Map.class);
        }
        return requestBody;
    }

    private Map<String, Object> buildRequestParams(BaseExternalSystemParams.InterfaceConfig config) {
        Map<String, Object> requestParams = new HashMap<>();
        for (BaseExternalSystemParams.RequestParameter param : config.getParameters()) {
            if (param.getLocation().equals(BaseExternalSystemParams.ParamLocation.PARAM)) {
                requestParams.put(param.getParamName(), replaceParameterPlaceholders(param.getParamValue()));
            }
        }
        return requestParams;
    }

    private String replaceParameterPlaceholders(String template) {
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

    private void parseSubmitResponse(String responseBody) throws TaskException {
        try {
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
