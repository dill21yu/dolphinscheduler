package org.apache.dolphinscheduler.server.master.cluster;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.snowflake.client.jdbc.internal.apache.commons.codec.binary.Base64;
import org.apache.dolphinscheduler.common.utils.JSONUtils;
import org.apache.dolphinscheduler.dao.entity.ExternalSystem;
import org.apache.dolphinscheduler.plugin.task.externalSystem.BaseExternalSystemParams;
import org.apache.dolphinscheduler.dao.entity.ExternalSystem;
import org.apache.dolphinscheduler.plugin.task.externalSystem.BaseExternalSystemParams;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import java.io.IOException;

public class ExternalSystemTokenManager {
    private static final Map<Integer, String> tokenCache = new HashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static BaseExternalSystemParams parseConnectionParams(ExternalSystem externalSystem) {
        try {
            return objectMapper.readValue(externalSystem.getConnectionParams(), BaseExternalSystemParams.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse connectionParams", e);
        }
    }

    public static String getToken(BaseExternalSystemParams params) {
        try {
            return org.apache.dolphinscheduler.plugin.task.externalSystem.AuthenticationUtils.authenticateAndGetToken(params.getAuthConfig());
        } catch (Exception e) {
            throw new RuntimeException("Failed to get token", e);
        }
    }


    public static void initTokenCache(List<ExternalSystem> externalSystems) {
        for (ExternalSystem system : externalSystems) {
            BaseExternalSystemParams params = parseConnectionParams(system);
            String token = getToken(params);
            tokenCache.put(system.getId(), token);
        }

        // 每五分钟检查一次 token
        scheduler.scheduleAtFixedRate(() -> refreshTokens(externalSystems), 0, 5, TimeUnit.MINUTES);
    }
    private static void refreshTokens(List<ExternalSystem> externalSystems) {
        for (ExternalSystem system : externalSystems) {
            BaseExternalSystemParams params = parseConnectionParams(system);
            String token = tokenCache.get(system.getId());

            if (params.getAuthConfig().getAuthType() == BaseExternalSystemParams.AuthType.OAUTH2) {
                if (isTokenExpiringSoon(token)) {
                    String newToken = getToken(params);
                    tokenCache.put(system.getId(), newToken);
                }
            }
        }
    }


    private static boolean isTokenExpiringSoon(String token) {
        // 解析 token 的过期时间
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new RuntimeException("Invalid token format");
        }

        String payload = new String(Base64.decodeBase64(parts[1]), StandardCharsets.UTF_8);
        int exp = Integer.parseInt(payload.split(",\"exp\":")[1].split(",")[0]);

        // 当前时间 + 5分钟
        long currentTimePlus5Minutes = System.currentTimeMillis() / 1000 + 5 * 60;

        return exp <= currentTimePlus5Minutes;
    }


        public static void main(String[] args) {
            // 假设从数据库中获取所有外部系统
            List<ExternalSystem> externalSystems = getExternalSystemsFromDatabase();

            // 初始化 token 缓存
            ExternalSystemTokenManager.initTokenCache(externalSystems);

            // 其他业务逻辑
        }

        private static List<ExternalSystem> getExternalSystemsFromDatabase() {
            // 模拟从数据库中获取外部系统
            // 这里可以使用 MyBatis 或其他 ORM 框架
            return List.of(
                    new ExternalSystem(1, "System1", "HTTP", "{\"id\":1,\"systemName\":\"System1\",\"serviceAddress\":\"http://example.com\",\"fieldMappings\":[],\"authConfig\":{\"authType\":\"OAUTH2\",\"oauth2TokenUrl\":\"http://example.com/oauth2/token\",\"oauth2ClientId\":\"client1\",\"oauth2ClientSecret\":\"secret1\",\"oauth2GrantType\":\"client_credentials\"}}", 1, new Date(), new Date())
            );
        }

}
