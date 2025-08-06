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

package org.apache.dolphinscheduler.api.service.impl;

import org.apache.dolphinscheduler.api.enums.Status;
import org.apache.dolphinscheduler.api.exceptions.ServiceException;
import org.apache.dolphinscheduler.api.service.ExternalSystemService;
import org.apache.dolphinscheduler.api.utils.PageInfo;
import org.apache.dolphinscheduler.common.constants.Constants;
import org.apache.dolphinscheduler.common.enums.AuthorizationType;
import org.apache.dolphinscheduler.common.enums.UserType;
import org.apache.dolphinscheduler.common.model.OkHttpRequestHeaderContentType;
import org.apache.dolphinscheduler.common.model.OkHttpRequestHeaders;
import org.apache.dolphinscheduler.common.model.OkHttpResponse;
import org.apache.dolphinscheduler.common.utils.JSONUtils;
import org.apache.dolphinscheduler.common.utils.OkHttpUtils;
import org.apache.dolphinscheduler.dao.entity.ExternalSystem;
import org.apache.dolphinscheduler.dao.entity.ExternalSystemTaskQuery;
import org.apache.dolphinscheduler.dao.entity.User;
import org.apache.dolphinscheduler.dao.mapper.ExternalSystemMapper;
import org.apache.dolphinscheduler.plugin.datasource.api.utils.PasswordUtils;
import org.apache.dolphinscheduler.plugin.task.api.TaskException;
import org.apache.dolphinscheduler.plugin.task.externalSystem.AuthenticationUtils;
import org.apache.dolphinscheduler.plugin.task.externalSystem.BaseExternalSystemParams;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jayway.jsonpath.JsonPath;

@Service
@Slf4j
public class ExternalSystemServiceImpl extends BaseServiceImpl implements ExternalSystemService {

    @Autowired
    private ExternalSystemMapper externalSystemMapper;
    private static final String EXTERNAL_TASK_ID = "id";
    private static final String EXTERNAL_TASK_NAME = "name";
    @Override
    public ExternalSystem createExternalSystem(User loginUser, BaseExternalSystemParams externalSystemParam) {
        // 检查名称是否已存在
        if (checkName(externalSystemParam.getSystemName())) {
            throw new ServiceException(Status.EXTERNAL_SYSTEM_NAME_EXIST);
        }
        checkExternalSystemParam(externalSystemParam);

        // 创建外部系统记录
        ExternalSystem externalSystem = new ExternalSystem();
        externalSystem.setName(externalSystemParam.getSystemName());
        externalSystem.setType("0");

        // 移除 id 信息
        externalSystemParam.setId(null);
        BaseExternalSystemParams.AuthConfig authConfig = externalSystemParam.getAuthConfig();
        encodePassword(authConfig);

        externalSystem.setConnectionParams(JSONUtils.toJsonString(externalSystemParam));
        externalSystem.setUserId(loginUser.getId());
        externalSystem.setCreateTime(new Date());
        externalSystem.setUpdateTime(new Date());

        externalSystemMapper.insert(externalSystem);
        return externalSystem;
    }
    private void encodePassword(BaseExternalSystemParams.AuthConfig authConfig) {
        if (null != authConfig.getOauth2ClientSecret() && !authConfig.getOauth2ClientSecret().isEmpty()) {
            authConfig.setOauth2ClientSecret(PasswordUtils.encodePassword(authConfig.getOauth2ClientSecret()));
        }
        if (null != authConfig.getOauth2Password() && !authConfig.getOauth2Password().isEmpty()) {
            authConfig.setOauth2Password(PasswordUtils.encodePassword(authConfig.getOauth2Password()));
        }
        if (null != authConfig.getJwtToken() && !authConfig.getJwtToken().isEmpty()) {
            authConfig.setJwtToken(PasswordUtils.encodePassword(authConfig.getJwtToken()));
        }
        if (null != authConfig.getBasicPassword() && !authConfig.getBasicPassword().isEmpty()) {
            authConfig.setBasicPassword(PasswordUtils.encodePassword(authConfig.getBasicPassword()));
        }
    }

    private void checkExternalSystemParam(BaseExternalSystemParams externalSystemParam) {
        // 检查系统名称
        if (externalSystemParam.getSystemName() == null || externalSystemParam.getSystemName().isEmpty()) {
            throw new ServiceException(Status.EXTERNAL_SYSTEM_NAME_EMPTY);
        }

        // 检查服务地址
        if (externalSystemParam.getServiceAddress() == null || externalSystemParam.getServiceAddress().isEmpty()) {
            throw new ServiceException(Status.EXTERNAL_SYSTEM_SERVICE_ADDRESS_EMPTY);
        }

        // 检查认证配置
        BaseExternalSystemParams.AuthConfig authConfig = externalSystemParam.getAuthConfig();
        if (authConfig == null) {
            throw new ServiceException(Status.EXTERNAL_SYSTEM_AUTH_CONFIG_EMPTY);
        }
        if (authConfig.getAuthType() == null) {
            throw new ServiceException(Status.EXTERNAL_SYSTEM_AUTH_CONFIG_TYPE_EMPTY);
        }

        // 根据认证类型进行具体校验
        switch (authConfig.getAuthType()) {
            case BASIC_AUTH:
                if (authConfig.getBasicUsername() == null || authConfig.getBasicUsername().isEmpty()) {
                    throw new ServiceException(Status.EXTERNAL_SYSTEM_BASIC_USERNAME_EMPTY);
                }
                if (authConfig.getBasicPassword() == null || authConfig.getBasicPassword().isEmpty()) {
                    throw new ServiceException(Status.EXTERNAL_SYSTEM_BASIC_PASSWORD_EMPTY);
                }
                break;
            case JWT:
                if (authConfig.getJwtToken() == null || authConfig.getJwtToken().isEmpty()) {
                    throw new ServiceException(Status.EXTERNAL_SYSTEM_JWT_TOKEN_EMPTY);
                }
                break;
            case OAUTH2:
                if (authConfig.getOauth2TokenUrl() == null || authConfig.getOauth2TokenUrl().isEmpty()) {
                    throw new ServiceException(Status.EXTERNAL_SYSTEM_OAUTH2_TOKEN_URL_EMPTY);
                }
                if (authConfig.getOauth2ClientId() == null || authConfig.getOauth2ClientId().isEmpty()) {
                    throw new ServiceException(Status.EXTERNAL_SYSTEM_OAUTH2_CLIENT_ID_EMPTY);
                }
                if (authConfig.getOauth2ClientSecret() == null || authConfig.getOauth2ClientSecret().isEmpty()) {
                    throw new ServiceException(Status.EXTERNAL_SYSTEM_OAUTH2_CLIENT_SECRET_EMPTY);
                }
                if (authConfig.getOauth2GrantType() == null || authConfig.getOauth2GrantType().isEmpty()) {
                    throw new ServiceException(Status.EXTERNAL_SYSTEM_OAUTH2_GRANT_TYPE_EMPTY);
                }
                if (authConfig.getOauth2GrantType().equals("password")) {
                    if (authConfig.getOauth2Username() == null || authConfig.getOauth2Username().isEmpty()) {
                        throw new ServiceException(Status.EXTERNAL_SYSTEM_OAUTH2_USERNAME_EMPTY);
                    }
                    if (authConfig.getOauth2Password() == null || authConfig.getOauth2Password().isEmpty()) {
                        throw new ServiceException(Status.EXTERNAL_SYSTEM_OAUTH2_PASSWORD_EMPTY);
                    }
                }
                break;
            default:
                throw new ServiceException(Status.EXTERNAL_SYSTEM_AUTH_TYPE_UNSUPPORTED);
        }

        // 检查接口配置
        if (externalSystemParam.getSelectInterface() == null) {
            throw new ServiceException(Status.EXTERNAL_SYSTEM_SELECT_INTERFACE_EMPTY);
        }
        if (externalSystemParam.getSubmitInterface() == null) {
            throw new ServiceException(Status.EXTERNAL_SYSTEM_SUBMIT_INTERFACE_EMPTY);
        }
        if (externalSystemParam.getPollStatusInterface() == null) {
            throw new ServiceException(Status.EXTERNAL_SYSTEM_POLL_STATUS_INTERFACE_EMPTY);
        }
        if (externalSystemParam.getStopInterface() == null) {
            throw new ServiceException(Status.EXTERNAL_SYSTEM_STOP_INTERFACE_EMPTY);
        }

        // 检查接口配置的 URL 和方法
        checkInterfaceConfig(externalSystemParam.getSelectInterface());
        checkInterfaceConfig(externalSystemParam.getSubmitInterface());
        checkInterfaceConfig(externalSystemParam.getPollStatusInterface());
        checkInterfaceConfig(externalSystemParam.getStopInterface());
    }

    private void checkInterfaceConfig(BaseExternalSystemParams.InterfaceConfig interfaceConfig) {
        if (interfaceConfig.getUrl() == null || interfaceConfig.getUrl().isEmpty()) {
            throw new ServiceException(Status.EXTERNAL_SYSTEM_INTERFACE_URL_EMPTY);
        }
        if (interfaceConfig.getMethod() == null) {
            throw new ServiceException(Status.EXTERNAL_SYSTEM_INTERFACE_METHOD_EMPTY);
        }
    }

    @Override
    public ExternalSystem updateExternalSystem(User loginUser, BaseExternalSystemParams updateExternalSystemParam) {
        checkExternalSystemParam(updateExternalSystemParam);
        // 检查外部系统是否存在
        ExternalSystem existingSystem = externalSystemMapper.selectById(updateExternalSystemParam.getId());
        if (existingSystem == null) {
            throw new ServiceException(Status.EXTERNAL_SYSTEM_NOT_EXIST);
        }

        // 检查名称是否被其他系统占用
        if (!updateExternalSystemParam.getSystemName().trim().equals(existingSystem.getName())
                && checkName(updateExternalSystemParam.getSystemName())) {
            throw new ServiceException(Status.EXTERNAL_SYSTEM_NAME_EXIST);
        }

        // 更新外部系统记录
        existingSystem.setName(updateExternalSystemParam.getSystemName());
        existingSystem.setType("0");
        existingSystem.setUpdateTime(new Date());
        // check password，if the password is not updated, set to the old password.
        BaseExternalSystemParams oldParams =
                JSONUtils.parseObject(existingSystem.getConnectionParams(), BaseExternalSystemParams.class);
        BaseExternalSystemParams.AuthConfig updateAuthConfig = updateExternalSystemParam.getAuthConfig();
        if (updateAuthConfig.getBasicPassword() != null
                && updateAuthConfig.getBasicPassword().equals(Constants.XXXXXX)) {
            updateAuthConfig.setBasicPassword(oldParams.getAuthConfig().getBasicPassword());
        } else if (null != updateAuthConfig.getBasicPassword() && !updateAuthConfig.getBasicPassword().isEmpty()) {
            updateAuthConfig.setBasicPassword(PasswordUtils.encodePassword(updateAuthConfig.getBasicPassword()));
        }
        if (updateAuthConfig.getOauth2ClientSecret() != null
                && updateAuthConfig.getOauth2ClientSecret().equals(Constants.XXXXXX)) {
            updateAuthConfig.setOauth2ClientSecret(oldParams.getAuthConfig().getOauth2ClientSecret());
        } else if (null != updateAuthConfig.getOauth2ClientSecret()
                && !updateAuthConfig.getOauth2ClientSecret().isEmpty()) {
            updateAuthConfig
                    .setOauth2ClientSecret(PasswordUtils.encodePassword(updateAuthConfig.getOauth2ClientSecret()));
        }

        if (updateAuthConfig.getOauth2Password() != null
                && updateAuthConfig.getOauth2Password().equals(Constants.XXXXXX)) {
            updateAuthConfig.setOauth2Password(oldParams.getAuthConfig().getOauth2Password());
        } else if (null != updateAuthConfig.getOauth2Password() && !updateAuthConfig.getOauth2Password().isEmpty()) {
            updateAuthConfig.setOauth2Password(PasswordUtils.encodePassword(updateAuthConfig.getOauth2Password()));
        }

        if (updateAuthConfig.getJwtToken() != null && updateAuthConfig.getJwtToken().equals(Constants.XXXXXX)) {
            updateAuthConfig.setJwtToken(oldParams.getAuthConfig().getJwtToken());
        } else if (null != updateAuthConfig.getJwtToken() && !updateAuthConfig.getJwtToken().isEmpty()) {
            updateAuthConfig.setJwtToken(PasswordUtils.encodePassword(updateAuthConfig.getJwtToken()));
        }

        updateExternalSystemParam.setAuthConfig(updateAuthConfig);
        existingSystem.setConnectionParams(JSONUtils.toJsonString(updateExternalSystemParam));
        externalSystemMapper.updateById(existingSystem);
        return existingSystem;
    }
    private boolean checkName(String name) {
        List<ExternalSystem> systemByName = externalSystemMapper.queryBySystemName(name.trim());
        return systemByName != null && !systemByName.isEmpty();
    }

    @Override
    public BaseExternalSystemParams queryExternalSystem(int id, User loginUser) {
        ExternalSystem externalSystem = externalSystemMapper.selectById(id);
        if (externalSystem == null) {
            throw new ServiceException(Status.EXTERNAL_SYSTEM_NOT_EXIST);
        }
        hideSensitiveInformation(externalSystem);
        BaseExternalSystemParams baseExternalSystemParam =
                JSONUtils.parseObject(externalSystem.getConnectionParams(), BaseExternalSystemParams.class);
        baseExternalSystemParam.setId(id);

        // 权限检查 (如果需要)
        // (loginUser, externalSystem);

        // BaseExternalSystemParamDTO dto = new BaseExternalSystemParamDTO();
        // dto.setId(externalSystem.getId());
        // dto.setSystemName(externalSystem.getName());
        // dto.setAuthConfig();
        // todo
        return baseExternalSystemParam;
    }

    @Override
    public boolean testExternalSystemConnection(User loginUser, BaseExternalSystemParams baseExternalSystemParam) {
        try {
            OkHttpResponse response = callSelectInterface(baseExternalSystemParam);
            if (response.getStatusCode() == 200) {
                return true;
            }
        } catch (Exception e) {
            log.error("connect error,e:{}", e.getMessage());
        }
        throw new ServiceException(Status.EXTERNAL_SYSTEM_CONNECT_FAILED);
    }

    private OkHttpResponse callSelectInterface(BaseExternalSystemParams baseExternalSystemParam) {
        try {
            BaseExternalSystemParams.InterfaceConfig selectConfig = baseExternalSystemParam.getSelectInterface();

            // 替换参数占位符
            String url = baseExternalSystemParam.getCompleteUrl(selectConfig.getUrl());

            OkHttpRequestHeaders headers = new OkHttpRequestHeaders();
            headers.setOkHttpRequestHeaderContentType(OkHttpRequestHeaderContentType.APPLICATION_JSON);

            Map<String, String> headeMap = new HashMap<>();
            Map<String, Object> requestBody = new HashMap<>();
            Map<String, Object> requestParams = new HashMap<>();
            String token;
            if (null != baseExternalSystemParam.getId()) {
                ExternalSystem existingSystem = externalSystemMapper.selectById(baseExternalSystemParam.getId());
                if (existingSystem == null) {
                    token = AuthenticationUtils.authenticateAndGetToken(baseExternalSystemParam);
                } else {
                    BaseExternalSystemParams oldParams =
                            JSONUtils.parseObject(existingSystem.getConnectionParams(), BaseExternalSystemParams.class);
                    BaseExternalSystemParams.AuthConfig authConfig = baseExternalSystemParam.getAuthConfig();
                    if (authConfig.getBasicPassword() != null
                            && authConfig.getBasicPassword().equals(Constants.XXXXXX)) {
                        authConfig.setBasicPassword(oldParams.getAuthConfig().getBasicPassword());
                    }
                    if (authConfig.getOauth2ClientSecret() != null
                            && authConfig.getOauth2ClientSecret().equals(Constants.XXXXXX)) {
                        authConfig.setOauth2ClientSecret(oldParams.getAuthConfig().getOauth2ClientSecret());
                    }
                    if (authConfig.getOauth2Password() != null
                            && authConfig.getOauth2Password().equals(Constants.XXXXXX)) {
                        authConfig.setOauth2Password(oldParams.getAuthConfig().getOauth2Password());
                    }
                    if (authConfig.getJwtToken() != null && authConfig.getJwtToken().equals(Constants.XXXXXX)) {
                        authConfig.setJwtToken(oldParams.getAuthConfig().getJwtToken());
                    }
                    decodePassword(authConfig);
                    baseExternalSystemParam.setAuthConfig(authConfig);
                    token = AuthenticationUtils.authenticateAndGetToken(baseExternalSystemParam);
                }

            } else {
                token = AuthenticationUtils.authenticateAndGetToken(baseExternalSystemParam);
            }

            headeMap.put("Authorization",
                    baseExternalSystemParam.getTokenPrefix(baseExternalSystemParam.getAuthConfig().getAuthType())
                            + token);
            // 处理参数
            for (BaseExternalSystemParams.RequestParameter param : selectConfig.getParameters()) {
                // todo String value = replaceParameterPlaceholders(param.getParamValue());
                String value = param.getParamValue();

                switch (param.getLocation().name()) {
                    case "HEADER":
                        headeMap.put(param.getParamName(), value);
                        break;
                    case "BODY":
                        if ("body".equals(param.getParamName())) {
                            requestBody = JSONUtils.parseObject(value, Map.class);
                        }

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
            if (BaseExternalSystemParams.HttpMethod.POST.equals(selectConfig.getMethod())) {
                response = OkHttpUtils.post(url, headers, requestParams, requestBody, 120000, 120000, 120000);
            } else if (BaseExternalSystemParams.HttpMethod.PUT.equals(selectConfig.getMethod())) {
                response = OkHttpUtils.put(url, headers, requestBody, 120000, 120000, 120000);
            } else {
                response = OkHttpUtils.get(url, headers, requestParams, 120000, 120000, 120000);
            }
            return response;

        } catch (Exception e) {
            log.error("select task failed", e);
            throw new TaskException("select task failed", e);
        }
    }

    private void decodePassword(BaseExternalSystemParams.AuthConfig authConfig) {
        if (null != authConfig.getOauth2ClientSecret() && !authConfig.getOauth2ClientSecret().isEmpty()) {
            authConfig.setOauth2ClientSecret(PasswordUtils.decodePassword(authConfig.getOauth2ClientSecret()));
        }
        if (null != authConfig.getOauth2Password() && !authConfig.getOauth2Password().isEmpty()) {
            authConfig.setOauth2Password(PasswordUtils.decodePassword(authConfig.getOauth2Password()));
        }
        if (null != authConfig.getJwtToken() && !authConfig.getJwtToken().isEmpty()) {
            authConfig.setJwtToken(PasswordUtils.decodePassword(authConfig.getJwtToken()));
        }
        if (null != authConfig.getBasicPassword() && !authConfig.getBasicPassword().isEmpty()) {
            authConfig.setBasicPassword(PasswordUtils.decodePassword(authConfig.getBasicPassword()));
        }
    }

    @Override
    public PageInfo<ExternalSystem> queryExternalSystemListPaging(User loginUser, String searchVal, Integer pageNo,
                                                                  Integer pageSize) {
        Page<ExternalSystem> page = new Page<>(pageNo, pageSize);
        IPage<ExternalSystem> externalSystemList;
        PageInfo<ExternalSystem> pageInfo = new PageInfo<>(pageNo, pageSize);
        if (loginUser.getUserType().equals(UserType.ADMIN_USER)) {
            externalSystemList = externalSystemMapper.selectPaging(page, searchVal, 0);
        } else {
            Set<Integer> ids = resourcePermissionCheckService
                    .userOwnedResourceIdsAcquisition(AuthorizationType.EXTERNALSYSTEM, loginUser.getId(), log);
            if (ids.isEmpty()) {
                return pageInfo;
            }
            externalSystemList = externalSystemMapper.selectPaging(page, searchVal, loginUser.getId());
        }

        List<ExternalSystem> externalSystems =
                externalSystemList != null ? externalSystemList.getRecords() : new ArrayList<>();
        for (ExternalSystem externalSystem : externalSystems) {
            hideSensitiveInformation(externalSystem);
        }
        pageInfo.setTotal((int) (externalSystemList != null ? externalSystemList.getTotal() : 0L));
        pageInfo.setTotalList(externalSystems);
        return pageInfo;
    }

    @Override
    public List<ExternalSystem> queryExternalSystemList(User loginUser) {
        List<ExternalSystem> externalSystemList;
        if (loginUser.getUserType().equals(UserType.ADMIN_USER)) {
            externalSystemList = externalSystemMapper.selectListByUserId(0);
        } else {
            Set<Integer> ids = resourcePermissionCheckService
                    .userOwnedResourceIdsAcquisition(AuthorizationType.EXTERNALSYSTEM, loginUser.getId(), log);
            if (ids.isEmpty()) {
                return Collections.emptyList();
            }
            externalSystemList = externalSystemMapper.selectBatchIds(ids).stream().collect(Collectors.toList());
            for (ExternalSystem externalSystem : externalSystemList) {
                hideSensitiveInformation(externalSystem);
            }
        }
        return externalSystemList;
    }

    /**
     * handle externalSystem connection password for safety
     */

    public void hideSensitiveInformation(ExternalSystem externalSystem) {
        BaseExternalSystemParams baseExternalSystemParams =
                JSONUtils.parseObject(externalSystem.getConnectionParams(), BaseExternalSystemParams.class);

        BaseExternalSystemParams.AuthConfig authConfig = baseExternalSystemParams.getAuthConfig();
        if (authConfig.getBasicPassword() != null && !authConfig.getBasicPassword().isEmpty()) {
            authConfig.setBasicPassword(getHiddenPassword());
        }
        if (authConfig.getOauth2ClientSecret() != null && !authConfig.getOauth2ClientSecret().isEmpty()) {
            authConfig.setOauth2ClientSecret(getHiddenPassword());
        }
        if (authConfig.getOauth2Password() != null && !authConfig.getOauth2Password().isEmpty()) {
            authConfig.setOauth2Password(getHiddenPassword());
        }
        if (authConfig.getJwtToken() != null && !authConfig.getJwtToken().isEmpty()) {
            authConfig.setJwtToken(getHiddenPassword());
        }
        externalSystem.setConnectionParams(JSONUtils.toJsonString(baseExternalSystemParams));
    }

    /**
     * get hidden password (resolve the security hotspot)
     *
     * @return hidden password
     */
    private String getHiddenPassword() {
        return Constants.XXXXXX;
    }

    @Override
    public void deleteExternalSystem(User loginUser, int id) {
        ExternalSystem externalSystem = externalSystemMapper.selectById(id);
        if (externalSystem == null) {
            throw new ServiceException(Status.EXTERNAL_SYSTEM_NOT_EXIST);
        }
        // 权限检查 (如果需要)
        // checkPermission(loginUser, externalSystem);

        externalSystemMapper.deleteById(id);
    }

    @Override
    public List<ExternalSystemTaskQuery> queryExternalSystemTasks(User loginUser, int externalSystemId) {

        // OkHttpUtils.post();
        ExternalSystem externalSystem = externalSystemMapper.selectById(externalSystemId);
        BaseExternalSystemParams baseExternalSystemParam =
                JSONUtils.parseObject(externalSystem.getConnectionParams(), BaseExternalSystemParams.class);

        // 校验查询必要
        String taskIdExpression = "";
        String taskNameExpression = "";
        for (BaseExternalSystemParams.ResponseParameter param : baseExternalSystemParam.getSelectInterface().getResponseParameters()) {
            if (EXTERNAL_TASK_ID.equals(param.getKey())) {
                taskIdExpression = param.getJsonPath();
            }
            if (EXTERNAL_TASK_NAME.equals(param.getKey())) {
                taskNameExpression = param.getJsonPath();
            }
        }
        if (taskIdExpression.isEmpty() || taskNameExpression.isEmpty()) {
            throw new IllegalStateException("External field mapping for 'id' and 'name' not found");
        }

        OkHttpResponse selectResponse = callSelectInterface(baseExternalSystemParam);
        if (selectResponse.getStatusCode() != 200) {
            throw new TaskException("Select task failed: " + selectResponse.getBody());
        }
        // 解析响应获取id name
        return parseSelectResponse(selectResponse.getBody(), taskIdExpression, taskNameExpression);

    }

    private List<ExternalSystemTaskQuery> parseSelectResponse(String responseBody, String taskIdExpression,
                                                              String taskNameExpression) throws TaskException {
        List<ExternalSystemTaskQuery> resultList = new ArrayList<>();

        try {

            List<String> idValues = JsonPath.read(responseBody, taskIdExpression);
            List<String> nameValues = JsonPath.read(responseBody, taskNameExpression);

            if (idValues.size() != nameValues.size()) {
                throw new TaskException("ID and name lists have different sizes");
            }

            // Create tasks
            for (int i = 0; i < idValues.size(); i++) {
                ExternalSystemTaskQuery task = new ExternalSystemTaskQuery();
                task.setId(idValues.get(i));
                task.setName(nameValues.get(i));
                resultList.add(task);
            }

        } catch (Exception e) {
            log.error("Parse select response failed", e);
            throw new TaskException("Parse select response failed", e);
        }
        return resultList;
    }

    /**
     * unauthorized externalSystem
     *
     * @param loginUser login user
     * @param userId    user id
     * @return unauthed data source result code
     */
    @Override
    public List<ExternalSystem> unAuthExternalSystem(User loginUser, Integer userId) {
        List<ExternalSystem> externalSystemList;
        if (canOperatorPermissions(loginUser, null, AuthorizationType.EXTERNALSYSTEM, null)) {
            // admin gets all data sources except userId
            externalSystemList = externalSystemMapper.queryExternalSystemExceptUserId(userId);
        } else {
            // non-admins users get their own data sources
            externalSystemList = externalSystemMapper.queryUserOwnExternalSystem(loginUser.getId());
        }
        List<ExternalSystem> resultList = new ArrayList<>();
        Set<ExternalSystem> externalSystemSet;
        if (externalSystemList != null && !externalSystemList.isEmpty()) {
            externalSystemSet = new HashSet<>(externalSystemList);

            List<ExternalSystem> authedExternalSystemList = externalSystemMapper.queryAuthedExternalSystem(userId);

            Set<ExternalSystem> authedExternalSystemSet;
            if (authedExternalSystemList != null && !authedExternalSystemList.isEmpty()) {
                authedExternalSystemSet = new HashSet<>(authedExternalSystemList);
                externalSystemSet.removeAll(authedExternalSystemSet);
            }
            resultList = new ArrayList<>(externalSystemSet);
        }
        return resultList;
    }

    /**
     * authorized externalSystem
     *
     * @param loginUser login user
     * @param userId    user id
     * @return authorized result code
     */
    @Override
    public List<ExternalSystem> authedExternalSystem(User loginUser, Integer userId) {
        List<ExternalSystem> authedExternalSystemList = externalSystemMapper.queryAuthedExternalSystem(userId);
        return authedExternalSystemList;
    }

}
