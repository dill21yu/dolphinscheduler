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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;

@Service
@Slf4j
public class ExternalSystemServiceImpl extends BaseServiceImpl implements ExternalSystemService {

    @Autowired
    private ExternalSystemMapper externalSystemMapper;

    @Override
    public ExternalSystem createExternalSystem(User loginUser, BaseExternalSystemParams externalSystemParam) {
        // 检查名称是否已存在
        ExternalSystem existSystem = externalSystemMapper.queryBySystemName(externalSystemParam.getSystemName());
        if (existSystem != null) {
            throw new ServiceException(Status.EXTERNAL_SYSTEM_NAME_EXIST);
        }

        // 验证连接参数
        // ExternalSystemUtils.checkExternalSystemParam(externalSystemParam);

        // 创建外部系统记录
        ExternalSystem externalSystem = new ExternalSystem();
        externalSystem.setName(externalSystemParam.getSystemName());
        externalSystem.setType("0");

        // 移除 id 信息
        externalSystemParam.setId(null);
        BaseExternalSystemParams.AuthConfig authConfig = externalSystemParam.getAuthConfig();
        if (null != authConfig.getOauth2Password() && !authConfig.getOauth2Password().isEmpty()) {
            PasswordUtils.encodePassword(authConfig.getOauth2Password());
        }
        if (null != authConfig.getJwtToken() && !authConfig.getJwtToken().isEmpty()) {
            PasswordUtils.encodePassword(authConfig.getJwtToken());
        }
        if (null != authConfig.getBasicPassword() && !authConfig.getBasicPassword().isEmpty()) {
            PasswordUtils.encodePassword(authConfig.getBasicPassword());
        }

        externalSystem.setConnectionParams(JSONUtils.toJsonString(externalSystemParam));
        externalSystem.setUserId(loginUser.getId());
        externalSystem.setCreateTime(new Date());
        externalSystem.setUpdateTime(new Date());

        externalSystemMapper.insert(externalSystem);
        return externalSystem;
    }

    @Override
    public ExternalSystem updateExternalSystem(User loginUser, BaseExternalSystemParams externalSystemParam) {
        // 检查外部系统是否存在
        ExternalSystem existingSystem = externalSystemMapper.selectById(externalSystemParam.getId());
        if (existingSystem == null) {
            throw new ServiceException(Status.EXTERNAL_SYSTEM_NOT_EXIST);
        }

        // 检查名称是否被其他系统占用
        ExternalSystem systemByName = externalSystemMapper.queryBySystemName(externalSystemParam.getSystemName());
        if (systemByName != null && !systemByName.getId().equals(externalSystemParam.getId())) {
            throw new ServiceException(Status.EXTERNAL_SYSTEM_NAME_EXIST);
        }

        // 验证连接参数
        // ExternalSystemUtils.checkExternalSystemParam(externalSystemParam);

        // 更新外部系统记录
        existingSystem.setName(externalSystemParam.getSystemName());
        existingSystem.setType("0");
        existingSystem.setConnectionParams(JSONUtils.toJsonString(externalSystemParam));
        existingSystem.setUpdateTime(new Date());

        externalSystemMapper.updateById(existingSystem);
        return existingSystem;
    }

    @Override
    public BaseExternalSystemParams queryExternalSystem(int id, User loginUser) {
        ExternalSystem externalSystem = externalSystemMapper.selectById(id);
        if (externalSystem == null) {
            throw new ServiceException(Status.EXTERNAL_SYSTEM_NOT_EXIST);
        }
        BaseExternalSystemParams baseExternalSystemParam =
                JSONUtils.parseObject(externalSystem.getConnectionParams(), BaseExternalSystemParams.class);

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
    public boolean testExternalSystemConnection(User loginUser, BaseExternalSystemParams externalSystemParam) {
        // 验证参数格式
        // ExternalSystemUtils.checkExternalSystemParam(externalSystemParam);
        // 解析 connectionParams 为 BaseExternalSystemParamDTO
        // BaseExternalSystemParamDTO registration = JSONUtils.parseObject(externalSystemParam.getConnectionParams(),
        // BaseExternalSystemParamDTO.class);
        // if (registration == null) {
        // throw new ServiceException(Status.EXTERNAL_SYSTEM_CONNECT_FAILED, "Invalid connection parameters format.");
        // }
        // 调用具体处理器的测试连接方法
        // return ExternalSystemUtils.getExternalSystemProcessor(externalSystemParam.getType())
        // .testConnection(registration);
        return true;
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
        hideSensitiveInformation(externalSystems);
        pageInfo.setTotal((int) (externalSystemList != null ? externalSystemList.getTotal() : 0L));
        pageInfo.setTotalList(externalSystems);
        return pageInfo;
    }

    @Override
    public List<ExternalSystem> queryDataSourceList(User loginUser) {
        List<ExternalSystem> externalSystemList;
        if (loginUser.getUserType().equals(UserType.ADMIN_USER)) {
            externalSystemList = externalSystemMapper.selectList(0);
        } else {
            Set<Integer> ids = resourcePermissionCheckService
                    .userOwnedResourceIdsAcquisition(AuthorizationType.EXTERNALSYSTEM, loginUser.getId(), log);
            if (ids.isEmpty()) {
                return Collections.emptyList();
            }
            externalSystemList = externalSystemMapper.selectBatchIds(ids).stream().collect(Collectors.toList());
        }
        return externalSystemList;
    }

    /**
     * handle datasource connection password for safety
     */
    public void hideSensitiveInformation(List<ExternalSystem> externalSystems) {
        for (ExternalSystem externalSystem : externalSystems) {
            String connectionParams = externalSystem.getConnectionParams();
            ObjectNode object = JSONUtils.parseObject(connectionParams);
            // 隐藏 password
            JsonNode authConfigNode = object.path("authConfig");
            if (authConfigNode.isObject()) {
                ObjectNode authConfig = (ObjectNode) authConfigNode;
                if (authConfig.has(Constants.PASSWORD)) {
                    authConfig.put(Constants.PASSWORD, getHiddenPassword());
                }
            }

            // 隐藏 jwtSecretOrPublicKey
            if (authConfigNode.has(Constants.JWTSECRETORPUBLICKEY)) {
                ((ObjectNode) authConfigNode).put(Constants.JWTSECRETORPUBLICKEY, getHiddenPassword());
            }
            // todo SECRET

            externalSystem.setConnectionParams(object.toString());
        }
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
        for (BaseExternalSystemParams.FieldMapping mapping : baseExternalSystemParam.getFieldMappings()) {
            if ("id".equals(mapping.getInternalField())) {
                taskIdExpression = mapping.getExternalField();
            }
            if ("name".equals(mapping.getInternalField())) {
                taskNameExpression = mapping.getExternalField();
            }
        }
        if (taskIdExpression.isEmpty() || taskNameExpression.isEmpty()) {
            throw new IllegalStateException("External field mapping for 'id' and 'name' not found");
        }

        try {
            BaseExternalSystemParams.InterfaceConfig selectConfig = baseExternalSystemParam.getSelectInterface();

            // 替换参数占位符
            String url = selectConfig.getUrl();

            OkHttpRequestHeaders headers = new OkHttpRequestHeaders();
            headers.setOkHttpRequestHeaderContentType(OkHttpRequestHeaderContentType.APPLICATION_JSON);

            Map<String, String> headeMap = new HashMap<>();
            Map<String, Object> requestBody = new HashMap<>();
            Map<String, Object> requestParams = new HashMap<>();
            String token = AuthenticationUtils.authenticateAndGetToken(baseExternalSystemParam.getAuthConfig());

            headeMap.put("Authorization", token);
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

            if (response.getStatusCode() != 200) {
                throw new TaskException("Select task failed: " + response.getBody());
            }

            // 解析响应获取id name
            return parseSelectResponse(response.getBody(), taskIdExpression, taskNameExpression);

        } catch (Exception e) {
            log.error("select task failed", e);
            throw new TaskException("select task failed", e);
        }
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

}
