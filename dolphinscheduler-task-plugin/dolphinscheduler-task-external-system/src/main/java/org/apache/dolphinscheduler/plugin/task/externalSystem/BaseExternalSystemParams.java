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

import java.util.List;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class BaseExternalSystemParams {

    private Integer id; // 系统id

    private String systemName; // 系统名称
    private String serviceAddress; // 服务地址


    private AuthConfig authConfig; // 认证配置

    private InterfaceConfig selectInterface; // 查询接口配置
    private InterfaceConfig submitInterface; // 提交接口配置
    private PollingInterfaceConfig pollStatusInterface; // 轮询状态接口配置
    private InterfaceConfig stopInterface; // 停止接口配置


    @Data
    public static class AuthConfig {

        private AuthType authType; // 认证类型：BASIC, JWT, OAUTH2

        // === 基础认证（Basic Auth） ===
        private String basicUsername;
        private String basicPassword;

        // === JWT 认证 ===
        private String jwtToken; // JWT令牌

        // === OAuth2 认证 ===
        private String oauth2TokenUrl;
        private String oauth2ClientId;
        private String oauth2ClientSecret;
        private String oauth2GrantType; // e.g., "client_credentials", "password"
        private String oauth2Username; // 仅限 password 模式
        private String oauth2Password; // 仅限 password 模式

        // === 动态映射配置（如请求头/参数映射） ===
        private AuthMapping[] authMappings;
    }

    public enum AuthType {
        BASIC_AUTH, JWT, OAUTH2
    }

    @Data
    public static class InterfaceConfig {

        private String url;
        private HttpMethod method; // 请求方式 GET/POST
        private String body;
        private List<RequestParameter> parameters; // 参数列表
        private List<ResponseParameter> responseParameters; // 参数列表

    }

    @Data
    public static class PollingInterfaceConfig extends InterfaceConfig {

        private PollingSuccessConfig pollingSuccessConfig; // 轮询成功配置
        private PollingFailureConfig pollingFailureConfig; // 轮询失败配置
    }

    @Data
    public static class RequestParameter {

        private String paramName; // 参数名
        private String paramValue; // 参数值（可以是固定值或占位符）
        private ParamLocation location; // 参数所在位置（header,param,body）
    }

    @Data
    public static class ResponseParameter {
        private String key;
        private String jsonPath;
    }

    @Data
    public static class PollingSuccessConfig {

        private String successField; // 成功判断字段名
        private String successValue; // 成功字段对应的值
    }

    @Data
    public static class PollingFailureConfig {

        private String failureField; // 失败判断字段名
        private String failureValue; // 失败字段对应的值
    }

    // 枚举：字段类型
    public enum FieldType {
        STRING, INTEGER, BOOLEAN, DATE, JSON_OBJECT, CUSTOM
    }

    // 枚举：HTTP 方法
    public enum HttpMethod {
        GET, POST, PUT
    }

    // 枚举：参数位置
    public enum ParamLocation {
        Header, Query
    }

    @Data
    public static class AuthMapping {

        private String key;
        private String value;
    }
    public String getTokenPrefix(AuthType authType) {
        switch (authType) {
            case BASIC_AUTH:
                return "Basic ";
            case JWT:
                return "Bearer ";
            case OAUTH2:
                return "Bearer ";
            default:
                log.warn("Unsupported auth type: " + authType);
                return "";
        }
    }
    public String getCompleteUrl(String url) {
        if (url == null || !url.startsWith("http")) {
            if (serviceAddress == null) {
                log.warn("Service address is not set.");
                return url; // 或者抛出异常，根据业务需求决定
            }
            return serviceAddress + url;
        }
        return url;
    }

}
