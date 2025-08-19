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

package org.apache.dolphinscheduler.common.utils;

import org.apache.commons.net.util.Base64;

/**
 * @author suyc
 * @date 2025-08-07 16:02:32
 * @desc
 */
public class Base64Utils {

    private Base64Utils() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Generates the HTTP Basic Authentication header value.
     * This method combines the username and password into a Base64 encoded string, which is used in the Authorization header of HTTP requests.
     *
     * @param userName The username for authentication.
     * @param password The password, used in conjunction with the username for authentication.
     * @return The Authorization header value formatted as "Basic " followed by the Base64 encoded username and password.
     */
    public static String getBaseAuth(String userName, String password) {
        // Concatenate the username and password into a single string, separated by a colon
        String auth = userName + ":" + password;
        // Convert the concatenated string to a byte array and encode it using Base64
        String base64Auth = Base64.encodeBase64String(auth.getBytes(), false);
        // Concatenate the "Basic " prefix with the Base64 encoded string to form the final header value
        return "Basic " + base64Auth;
    }

}
