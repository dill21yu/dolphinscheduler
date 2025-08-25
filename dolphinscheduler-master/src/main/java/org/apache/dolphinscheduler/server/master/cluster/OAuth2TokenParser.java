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

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

public class OAuth2TokenParser {

    // 解析OAuth2 Token响应
    public TokenInfo parseTokenResponse(Map<String, Object> tokenResponse,
                                        String signingKey) {
        String accessToken = (String) tokenResponse.get("access_token");
        String tokenType = (String) tokenResponse.get("token_type");
        Integer expiresIn = (Integer) tokenResponse.get("expires_in");
        String scope = (String) tokenResponse.get("scope");

        Date issuedAt = new Date(); // 默认使用当前时间
        Date expiryFromExpiresIn =
                expiresIn != null ? new Date(issuedAt.getTime() + TimeUnit.SECONDS.toMillis(expiresIn)) : null;

        // 尝试解析JWT
        JwtInfo jwtInfo = null;
        if (isJwt(accessToken)) {
            try {
                jwtInfo = parseJwt(accessToken, signingKey);
                issuedAt = jwtInfo.getIssuedAt(); // 使用JWT中的准确颁发时间
            } catch (Exception e) {
                // JWT解析失败，回退到opaque token处理
            }
        }

        return new TokenInfo(
                accessToken,
                tokenType,
                issuedAt,
                expiryFromExpiresIn,
                jwtInfo != null ? jwtInfo.getExpiry() : null,
                scope);
    }

    // 判断是否是JWT
    private boolean isJwt(String token) {
        if (token == null)
            return false;
        return token.split("\\.").length == 3; // JWT由三部分组成
    }

    // 解析JWT
    private JwtInfo parseJwt(String jwt, String signingKey) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(signingKey.getBytes()))
                .build()
                .parseClaimsJws(jwt)
                .getBody();

        return new JwtInfo(
                claims.getIssuedAt(),
                claims.getExpiration());
    }

    // 内部JWT信息类
    private static class JwtInfo {

        private final Date issuedAt;
        private final Date expiry;

        public JwtInfo(Date issuedAt, Date expiry) {
            this.issuedAt = issuedAt;
            this.expiry = expiry;
        }

        public Date getIssuedAt() {
            return issuedAt;
        }
        public Date getExpiry() {
            return expiry;
        }
    }

    // 最终返回的Token信息
    public static class TokenInfo {

        private final String accessToken;
        private final String tokenType;
        private final Date issuedAt;
        private final Date expiryFromExpiresIn;
        private final Date expiryFromJwt;
        private final String scope;

        public TokenInfo(String accessToken, String tokenType,
                         Date issuedAt, Date expiryFromExpiresIn,
                         Date expiryFromJwt, String scope) {
            this.accessToken = accessToken;
            this.tokenType = tokenType;
            this.issuedAt = issuedAt;
            this.expiryFromExpiresIn = expiryFromExpiresIn;
            this.expiryFromJwt = expiryFromJwt;
            this.scope = scope;
        }

        // 获取最准确的过期时间（优先使用JWT内嵌时间）
        public Date getBestEffortExpiry() {
            return expiryFromJwt != null ? expiryFromJwt : expiryFromExpiresIn;
        }

        // 检查是否过期
        public boolean isExpired() {
            Date expiry = getBestEffortExpiry();
            return expiry != null && expiry.before(new Date());
        }

        // 获取剩余有效时间（秒）
        public Long getRemainingSeconds() {
            Date expiry = getBestEffortExpiry();
            if (expiry == null)
                return null;
            return TimeUnit.MILLISECONDS.toSeconds(
                    expiry.getTime() - System.currentTimeMillis());
        }

        // getters...
    }
    // 示例使用
    public static void main(String[] args) {
        // 模拟OAuth2响应
        Map<String, Object> tokenResponse = new HashMap<>();
        tokenResponse.put("access_token",
                // "7bf2bd1f-1bdb-4f4b-8be1-9cf838ed32f7");
                "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6ImZsb3d0YXNrLWtpZCJ9.eyJ0ZW5hbnRfaWQiOjEsInN1YiI6ImFkbWluIiwidXNlcl9uYW1lIjoiYWRtaW4iLCJpc3MiOiJodHRwOi8vZmxvd3Rhc2sucG93ZXIuY246NTAwMzEiLCJub25jZSI6IiIsImF1dGhvcml0aWVzIjpbInRlbmFudCJdLCJjbGllbnRfaWQiOiJtYWluX2NsaWVudCIsImF1ZCI6WyJmbG93dGFzayIsIm1haW5fY2xpZW50Il0sImF6cCI6Im1haW5fY2xpZW50Iiwic2NvcGUiOlsiZW1haWwiLCJvZmZsaW5lX2FjY2VzcyIsIm9wZW5pZCJdLCJleHAiOjE3NTM4MTQxMzgsImlhdCI6MTc1Mzc3MDkzOCwianRpIjoiYmQzNDhiODItNTM4MC00Zjc4LWEzMzktOWJmMzY3YzMxZmIxIn0.TbwypyQ5vt4FJqjnT80lqMZCfzKy9VZ3u1vWZkUbn6dVN-ET2Pgt0nsZ8TG-L_tCoCM7ihCToa713_bYb3yJC9DIyzoEg_jxKsIpdzc5dZ8fxHstfZKOIkv3uD6REWNOQezGg4blkVJVnXNjh83S3SihOO7ZaWmD40c8C44U8SbkCqM_X2JcmOgnz13nFvJpzORmVvQAlKO427sFIQGyeB1nQpNjPBx3k0NGUn30P1u_NuGi77P28WmgZ8b7hd49pMahncrrtgzPbRO-ybwa76yT6J1da5TLbS5JxOC6drplJnGLdAUws2GX2xgoYulnLszXcq8Zjzw9sQI3dWUjbg"); // JWT或普通token
        tokenResponse.put("token_type", "bearer");
        tokenResponse.put("expires_in", 3600);
        tokenResponse.put("scope", "read write");

        // 创建解析器
        OAuth2TokenParser parser = new OAuth2TokenParser();
        String signingKey = "your-secret-key"; // 验证JWT的密钥

        // 解析token
        OAuth2TokenParser.TokenInfo tokenInfo =
                parser.parseTokenResponse(tokenResponse, signingKey);

        // 使用token信息
        // System.out.println("Token类型: " + tokenInfo.getTokenType());
        // System.out.println("是否JWT: " + (tokenInfo.getExpiryFromJwt() != null));
        // System.out.println("颁发时间: " + tokenInfo.getIssuedAt());
        System.out.println("过期时间: " + tokenInfo.getBestEffortExpiry());
        System.out.println("剩余时间(秒): " + tokenInfo.getRemainingSeconds());
        System.out.println("是否已过期: " + tokenInfo.isExpired());
    }

}
