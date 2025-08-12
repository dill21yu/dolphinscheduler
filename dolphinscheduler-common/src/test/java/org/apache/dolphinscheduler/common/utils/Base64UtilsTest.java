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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class Base64UtilsTest {

    @ParameterizedTest
    @CsvSource({
            "prometheusUser, fS6%%okJ?4nb, Basic cHJvbWV0aGV1c1VzZXI6ZlM2JSVva0o/NG5i",
            "admin12345,xmN2^?abcdef,Basic YWRtaW4xMjM0NTp4bU4yXj9hYmNkZWY=",
            "user123456,ynO3?abcdefg,Basic dXNlcjEyMzQ1Njp5bk8zP2FiY2RlZmc=",
            "test123456,zoP4!bcdefgh,Basic dGVzdDEyMzQ1Njp6b1A0IWJjZGVmZ2g=",
            "demo123456,apQ5@cdefghi,Basic ZGVtbzEyMzQ1NjphcFE1QGNkZWZnaGk=",
            "prod123456,bqR6%defghij,Basic cHJvZDEyMzQ1NjpicVI2JWRlZmdoaWo=",
            "admin123456,crS7^efghijk,Basic YWRtaW4xMjM0NTY6Y3JTN15lZmdoaWpr",
            "prod.user,ujK5@wxyzABC,Basic cHJvZC51c2VyOnVqSzVAd3h5ekFCQw==",
            "admin@test,vkL6%xyzABCD,Basic YWRtaW5AdGVzdDp2a0w2JXh5ekFCQ0Q=",
            "user@demo.com,wlM7^yzABCDE,Basic dXNlckBkZW1vLmNvbTp3bE03Xnl6QUJDREU=",
            "test@prod.net,xmN8?zABCDEF,Basic dGVzdEBwcm9kLm5ldDp4bU44P3pBQkNERUY=",
            "demo@admin.org,ynO9!ABCDEFG,Basic ZGVtb0BhZG1pbi5vcmc6eW5POSFBQkNERUZH",
            "prod@user.dev,zoP0@BCDEFGH,Basic cHJvZEB1c2VyLmRldjp6b1AwQEJDREVGR0g=",
            "admin@test.io,apQ1%CDEFGHI,Basic YWRtaW5AdGVzdC5pbzphcFExJUNERUZHSEk=",
            "prometheusUser,bqR6%bcdefgh,Basic cHJvbWV0aGV1c1VzZXI6YnFSNiViY2RlZmdo",
            "admin@test,gvW3?zABCDEF,Basic YWRtaW5AdGVzdDpndlczP3pBQkNERUY=",
            "user@demo,mbC1%YZ01234,Basic dXNlckBkZW1vOm1iQzElWVowMTIzNA=="
    })
    public void getBaseAuth_Test(String userName, String password, String expectedAuth) {
        String actualAuth = Base64Utils.getBaseAuth(userName, password);
        assertEquals(expectedAuth, actualAuth);
    }
}
