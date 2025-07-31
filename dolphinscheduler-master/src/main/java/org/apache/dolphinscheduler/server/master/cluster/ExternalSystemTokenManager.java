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

package org.apache.dolphinscheduler.server.master.cluster;

import org.apache.dolphinscheduler.common.utils.JSONUtils;
import org.apache.dolphinscheduler.dao.entity.ExternalSystem;
import org.apache.dolphinscheduler.dao.mapper.ExternalSystemMapper;
import org.apache.dolphinscheduler.plugin.task.api.TaskException;
import org.apache.dolphinscheduler.plugin.task.externalSystem.AuthenticationUtils;
import org.apache.dolphinscheduler.plugin.task.externalSystem.BaseExternalSystemParams;
import org.apache.dolphinscheduler.plugin.task.externalSystem.TokenInfo;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ExternalSystemTokenManager {

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private final Map<String, TokenInfo> tokenCache = new ConcurrentHashMap<>();
    private final Map<String, Object> lockMap = new ConcurrentHashMap<>();
    @Autowired
    private ExternalSystemMapper externalSystemMapper;

    public static String getToken(BaseExternalSystemParams params) {
        try {
            return AuthenticationUtils.authenticateAndGetToken(params);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get token", e);
        }
    }

    /**
     * 手动清除指定系统的 Token
     */
    public void invalidateToken(String externalSystemId) {
        tokenCache.remove(externalSystemId);
        lockMap.remove(externalSystemId);
        log.info("Invalidated token for external system: {}", externalSystemId);
    }

    /**
     * 获取最新的 Token 用于任务分发
     * 此方法会主动触发刷新以确保 Worker 获得最新的 token
     */
    public String getLatestTokenForTaskDispatch(ExternalSystem externalSystem) throws TaskException {
        String systemId = externalSystem.getId().toString();
        if (StringUtils.isEmpty(systemId)) {
            throw new TaskException("External system ID cannot be empty");
        }

        Object lock = lockMap.computeIfAbsent(systemId, k -> new Object());
        synchronized (lock) {
            refreshToken(externalSystem);
            return tokenCache.get(systemId).getToken();
        }
    }

    @Scheduled(fixedRate = 1200000) // 每5分钟检查一次
    public void checkForUpdates() {
        log.info("Checking for updates...");
        try {
            // 通过 mapper 获取所有外部系统
            List<ExternalSystem> allExternalSystems = externalSystemMapper.selectList(null);

            for (ExternalSystem system : allExternalSystems) {
                refreshToken(system);
            }

            // 清理已删除的外部系统缓存
            cleanupDeletedSystems(allExternalSystems);

        } catch (Exception e) {
            log.error("Failed to check external system updates", e);
        }
    }

    private void refreshToken(ExternalSystem system) {
        String systemId = String.valueOf(system.getId());
        Long currentDbUpdateTime = system.getUpdateTime().getTime();

        // 检查缓存中是否已经有该外部系统的 token
        TokenInfo cachedTokenInfo = tokenCache.get(systemId);

        boolean shouldUpdate = false;

        if (cachedTokenInfo == null) {
            // 缓存中没有该系统的 token，需要更新
            shouldUpdate = true;
            log.info("New external system found: {}, generating token", systemId);
        } else if (currentDbUpdateTime > cachedTokenInfo.getDbUpdateTime()) {
            // 数据库中的更新时间比缓存中的更新时间新，需要更新
            shouldUpdate = true;
            log.info("External system {} configuration updated, refreshing token", systemId);
        } else if (cachedTokenInfo.isExpired()) {
            // token 已过期，需要更新
            shouldUpdate = true;
            log.info("Token for external system {} has expired, refreshing", systemId);
        } else if (cachedTokenInfo.isExpiringSoon()) {
            // token 即将在5分钟内过期，需要更新
            shouldUpdate = true;
            log.info("Token for external system {} is expiring soon, refreshing", systemId);
        }

        if (shouldUpdate) {
            try {
                BaseExternalSystemParams baseExternalSystemParam =
                        JSONUtils.parseObject(system.getConnectionParams(), BaseExternalSystemParams.class);
                // 根据认证类型生成新的 token
                TokenInfo newTokenInfo =
                        generateTokenForSystem(baseExternalSystemParam, systemId, currentDbUpdateTime);

                // 如果是 OAuth2 认证，检查 token 是否在5分钟内过期
                if (newTokenInfo.isExpiringSoon()) {
                    log.warn("Generated token for system {} will expire soon, regenerating", systemId);
                    newTokenInfo =
                            generateTokenForSystem(baseExternalSystemParam, systemId, currentDbUpdateTime);
                }

                // 保存到缓存中
                tokenCache.put(systemId, newTokenInfo);
                log.info("Successfully updated token cache for external system: {}", systemId);

            } catch (Exception e) {
                log.error("Failed to generate token for external system: {}", systemId, e);
            }
        }
    }

    /**
     * 清理已删除的外部系统缓存
     */
    private void cleanupDeletedSystems(List<ExternalSystem> currentSystems) {
        Set<String> currentSystemIds = currentSystems.stream()
                .map(system -> String.valueOf(system.getId()))
                .collect(Collectors.toSet());

        // 找出缓存中存在但数据库中已删除的系统
        Set<String> cachedSystemIds = new HashSet<>(tokenCache.keySet());
        cachedSystemIds.removeAll(currentSystemIds);

        // 清理已删除系统的缓存
        for (String deletedSystemId : cachedSystemIds) {
            invalidateToken(deletedSystemId);
            log.info("Cleaned up cache for deleted external system: {}", deletedSystemId);
        }
    }

    /**
     * 为指定外部系统生成 token
     */
    private TokenInfo generateTokenForSystem(BaseExternalSystemParams baseParams, String externalSystemId,
                                             long dbUpdateTime) throws TaskException {
        try {

            BaseExternalSystemParams.AuthConfig authConfig = baseParams.getAuthConfig();

            String token;
            long expirationTime;

            switch (authConfig.getAuthType()) {
                case BASIC_AUTH:
                    token = AuthenticationUtils.authenticateAndGetToken(baseParams);
                    // Basic Auth 通常不过期，设置为24小时后
                    expirationTime = System.currentTimeMillis() + 24 * 60 * 60 * 1000;
                    break;

                case JWT:
                    token = AuthenticationUtils.authenticateAndGetToken(baseParams);
                    // JWT 根据配置的过期时间
                    // todo expirationTime = parseJwtExpiration(authConfig.getJwtSecretOrPublicKey());
                    expirationTime = System.currentTimeMillis() + 24 * 60 * 60 * 1000;

                    break;

                case OAUTH2:
                    token = AuthenticationUtils.authenticateAndGetToken(baseParams);
                    // 解析 OAuth2 token 的过期时间
                    // todo jwt可解析
                    expirationTime = parseOAuth2TokenExpiration(token);
                    break;

                default:
                    throw new TaskException("Unsupported auth type: " + authConfig.getAuthType());
            }

            return new TokenInfo(token, expirationTime, externalSystemId, 5 * 60 * 1000, dbUpdateTime);

        } catch (Exception e) {
            log.error("Failed to generate token for external system: {}", externalSystemId, e);
            throw new TaskException("Failed to generate token", e);
        }
    }

    /**
     * 解析 OAuth2 token 的过期时间
     */
    private long parseOAuth2TokenExpiration(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length >= 2) {
                String payload = new String(Base64.decodeBase64(parts[1]), StandardCharsets.UTF_8);
                long exp = Integer.parseInt(payload.split(",\"exp\":")[1].split(",")[0]);
                // 转换为可读的日期时间格式
                Instant instant = Instant.ofEpochSecond(exp);
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        .withZone(ZoneId.systemDefault());
                String formattedDateTime = instant.atZone(ZoneId.systemDefault()).format(formatter);
                log.info("Expiration time: " + formattedDateTime);
                return exp * 1000;

            } else {
                log.warn("OAuth2 token is not JWT, cannot to parse OAuth2 token expiration");
            }
        } catch (Exception e) {
            log.warn("Failed to parse OAuth2 token expiration, using default", e);
        }
        // 默认1小时后过期
        return System.currentTimeMillis() + 3600 * 1000;
    }

}
