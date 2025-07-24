package org.apache.dolphinscheduler.plugin.task.externalSystem;

import okhttp3.FormBody;
import okhttp3.RequestBody;
import org.apache.dolphinscheduler.common.model.OkHttpRequestHeaderContentType;
import org.apache.dolphinscheduler.common.model.OkHttpRequestHeaders;
import org.apache.dolphinscheduler.common.model.OkHttpResponse;
import org.apache.dolphinscheduler.common.utils.JSONUtils;
import org.apache.dolphinscheduler.common.utils.OkHttpUtils;
import org.apache.dolphinscheduler.plugin.task.api.TaskException;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class AuthenticationUtils {

    /**
     * 认证并获取Token
     *
     * @param authConfig 认证配置
     * @return 认证后的Token
     * @throws Exception
     */
    public static String authenticateAndGetToken(BaseExternalSystemParams.AuthConfig authConfig) throws Exception {
        if (authConfig == null) {
            throw new RuntimeException("AuthConfig is not provided");
        }

        switch (authConfig.getAuthType()) {
            case BASIC:
                // 基础认证
                String auth = authConfig.getBasicUsername() + ":" + authConfig.getBasicPassword();
                String encoding = java.util.Base64.getEncoder().encodeToString(auth.getBytes());
                return "Basic " + encoding;
            case JWT:
                // JWT认证
                return "Bearer " + authConfig.getJwtToken();
            case OAUTH2:
                // OAuth2认证
                return "Bearer " + getOAuth2Token(authConfig);
            default:
                throw new RuntimeException("Unsupported auth type: " + authConfig.getAuthType());
        }
    }

    /**
     * 获取OAuth2 Token
     *
     * @param authConfig 认证配置
     * @return OAuth2 Token
     * @throws Exception
     */
    private static String getOAuth2Token(BaseExternalSystemParams.AuthConfig authConfig) throws Exception {
        try {
            OkHttpRequestHeaders headers = new OkHttpRequestHeaders();
            headers.setHeaders(new HashMap<>());
            headers.setOkHttpRequestHeaderContentType(OkHttpRequestHeaderContentType.APPLICATION_FORM_URLENCODED);

            RequestBody formBody = new FormBody.Builder()
                    .add("client_id", authConfig.getOauth2ClientId())
                    .add("client_secret", authConfig.getOauth2ClientSecret())
                    .add("username", authConfig.getOauth2Username())
                    .add("password", authConfig.getOauth2Password())
                    .add("grant_type", authConfig.getOauth2GrantType())
                    .build();

            OkHttpResponse response = OkHttpUtils.postFormBody(
                    authConfig.getOauth2TokenUrl(),
                    headers,
                    null,
                    formBody,
                    30000, 30000, 30000);

            if (response.getStatusCode() != 200) {
                throw new TaskException("Authentication failed: " + response.getBody());
            }

            JsonNode authResult = JSONUtils.parseObject(response.getBody(), JsonNode.class);
            if (authResult.has("access_token")) {
                log.info("Authentication successful, token obtained");
                return authResult.get("access_token").asText();
            } else {
                throw new TaskException("Failed to get access token from response");
            }

        } catch (Exception e) {
            log.error("Authentication failed", e);
            throw new TaskException("Authentication failed", e);
        }
    }
}
