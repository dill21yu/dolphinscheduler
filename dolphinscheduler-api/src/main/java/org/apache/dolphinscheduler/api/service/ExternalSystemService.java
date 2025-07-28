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

package org.apache.dolphinscheduler.api.service;

import org.apache.dolphinscheduler.api.utils.PageInfo;
import org.apache.dolphinscheduler.dao.entity.ExternalSystem;
import org.apache.dolphinscheduler.dao.entity.ExternalSystemTaskQuery;
import org.apache.dolphinscheduler.dao.entity.User;
import org.apache.dolphinscheduler.plugin.task.externalSystem.BaseExternalSystemParams;

import java.util.List;

public interface ExternalSystemService {

    /**
     * create external system
     */
    ExternalSystem createExternalSystem(User loginUser, BaseExternalSystemParams externalSystemParam);

    /**
     * update external system
     */
    ExternalSystem updateExternalSystem(User loginUser, BaseExternalSystemParams externalSystemParam);

    /**
     * query external system
     */
    BaseExternalSystemParams queryExternalSystem(int id, User loginUser);

    /**
     * test external system connection
     */
    boolean testExternalSystemConnection(User loginUser, BaseExternalSystemParams externalSystemParam);

    /**
     * query external system list with paging
     */
    PageInfo<ExternalSystem> queryExternalSystemListPaging(User loginUser, String searchVal, Integer pageNo,
                                                           Integer pageSize);

    /**
     * query external system list
     * @param loginUser
     * @return
     */
    List<ExternalSystem> queryExternalSystemList(User loginUser);

    /**
     * delete external system
     */
    void deleteExternalSystem(User loginUser, int id);

    List<ExternalSystemTaskQuery> queryExternalSystemTasks(User loginUser, int externalSystemId);

    /**
     * unauthorized externalSystem
     *
     * @param loginUser login user
     * @param userId    user id
     * @return unauthed data source result code
     */
    List<ExternalSystem> unAuthExternalSystem(User loginUser, Integer userId);

    /**
     * authorized externalSystem
     *
     * @param loginUser login user
     * @param userId    user id
     * @return authorized result code
     */
    List<ExternalSystem> authedExternalSystem(User loginUser, Integer userId);

}
