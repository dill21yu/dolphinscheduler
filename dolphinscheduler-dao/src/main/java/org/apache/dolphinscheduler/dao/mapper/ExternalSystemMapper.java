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

package org.apache.dolphinscheduler.dao.mapper;

import org.apache.dolphinscheduler.dao.entity.ExternalSystem;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

@Mapper
public interface ExternalSystemMapper extends BaseMapper<ExternalSystem> {

    /**
     * 根据名称查询外部系统
     */
    ExternalSystem queryBySystemName(@Param("name") String name);

    /**
     * 根据用户ID查询有权限的外部系统列表
     */
    List<ExternalSystem> selectList(@Param("userId") Integer userId);

    /**
     * 分页查询外部系统列表
     */
    IPage<ExternalSystem> selectPaging(IPage<ExternalSystem> page,
                                       @Param("name") String name,
                                       @Param("userId") Integer userId);

    /**
     * query authed externalSystem
     * @param userId userId
     * @return externalSystem list
     */
    List<ExternalSystem> queryAuthedExternalSystem(@Param("userId") int userId);

    /**
     * query externalSystem except userId
     * @param userId userId
     * @return externalSystem list
     */
    List<ExternalSystem> queryExternalSystemExceptUserId(@Param("userId") int userId);

}
