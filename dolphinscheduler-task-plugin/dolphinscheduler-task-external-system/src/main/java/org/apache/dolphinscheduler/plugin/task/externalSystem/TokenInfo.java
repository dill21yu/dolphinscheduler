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

public class TokenInfo {

    private String token;
    private long expirationTime;
    private long refreshThreshold;
    private String externalSystemId;
    private long createTime;
    private long dbUpdateTime;

    public TokenInfo(String token, long expirationTime, String externalSystemId, long refreshThreshold,
                     long dbUpdateTime) {
        this.token = token;
        this.expirationTime = expirationTime;
        this.externalSystemId = externalSystemId;
        this.refreshThreshold = refreshThreshold;
        this.createTime = System.currentTimeMillis();
        this.dbUpdateTime = dbUpdateTime;
    }

    /**
     * 检查 token 是否即将过期（需要刷新）
     */
    public boolean isExpiringSoon() {
        return System.currentTimeMillis() > (expirationTime - refreshThreshold);
    }

    /**
     * 检查 token 是否已过期
     */
    public boolean isExpired() {
        return System.currentTimeMillis() > expirationTime;
    }

    /**
     * 获取剩余有效时间（毫秒）
     */
    public long getRemainingTime() {
        return Math.max(0, expirationTime - System.currentTimeMillis());
    }

    // Getters and Setters
    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public long getExpirationTime() {
        return expirationTime;
    }

    public void setExpirationTime(long expirationTime) {
        this.expirationTime = expirationTime;
    }

    public String getExternalSystemId() {
        return externalSystemId;
    }

    public void setExternalSystemId(String externalSystemId) {
        this.externalSystemId = externalSystemId;
    }

    public long getRefreshThreshold() {
        return refreshThreshold;
    }

    public void setRefreshThreshold(long refreshThreshold) {
        this.refreshThreshold = refreshThreshold;
    }

    public long getCreateTime() {
        return createTime;
    }

    public long getDbUpdateTime() {
        return dbUpdateTime;
    }

    public void setDbUpdateTime(long dbUpdateTime) {
        this.dbUpdateTime = dbUpdateTime;
    }

    @Override
    public String toString() {
        return "TokenInfo{" +
                "externalSystemId='" + externalSystemId + '\'' +
                ", expirationTime=" + expirationTime +
                ", refreshThreshold=" + refreshThreshold +
                ", createTime=" + createTime +
                ", isExpired=" + isExpired() +
                ", isExpiringSoon=" + isExpiringSoon() +
                '}';
    }

}
