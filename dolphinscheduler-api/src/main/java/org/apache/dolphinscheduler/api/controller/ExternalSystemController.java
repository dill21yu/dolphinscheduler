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

package org.apache.dolphinscheduler.api.controller;

import static org.apache.dolphinscheduler.api.enums.Status.CREATE_EXTERNAL_SYSTEM_ERROR;
import static org.apache.dolphinscheduler.api.enums.Status.DELETE_EXTERNAL_SYSTEM_ERROR;
import static org.apache.dolphinscheduler.api.enums.Status.QUERY_EXTERNAL_SYSTEM_ERROR;
import static org.apache.dolphinscheduler.api.enums.Status.TEST_EXTERNAL_SYSTEM_CONNECTION_ERROR;
import static org.apache.dolphinscheduler.api.enums.Status.UPDATE_EXTERNAL_SYSTEM_ERROR;

import org.apache.dolphinscheduler.api.audit.OperatorLog;
import org.apache.dolphinscheduler.api.audit.enums.AuditType;
import org.apache.dolphinscheduler.api.exceptions.ApiException;
import org.apache.dolphinscheduler.api.service.ExternalSystemService;
import org.apache.dolphinscheduler.api.utils.PageInfo;
import org.apache.dolphinscheduler.api.utils.Result;
import org.apache.dolphinscheduler.common.constants.Constants;
import org.apache.dolphinscheduler.common.utils.JSONUtils;
import org.apache.dolphinscheduler.dao.entity.ExternalSystem;
import org.apache.dolphinscheduler.dao.entity.ExternalSystemTaskQuery;
import org.apache.dolphinscheduler.dao.entity.User;
import org.apache.dolphinscheduler.plugin.task.externalSystem.BaseExternalSystemParams;
import org.apache.dolphinscheduler.plugin.task.api.utils.ParameterUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "EXTERNAL_SYSTEM_TAG")
@RestController
@RequestMapping("external-systems")
public class ExternalSystemController extends BaseController {

    @Autowired
    private ExternalSystemService externalSystemService;

    /**
     * create external system
     *
     * @param loginUser login user
     * @param jsonStr   external system param
     *                  example: {"type":"SEATUNNEL","name":"test-system","baseUrl":"http://localhost:8080","authConfig":{"client_id":"xxx","client_secret":"xxx","grant_type":"password"},"endpoints":{"auth":"/oauth/token","start":"/api/v1/start","status":"/api/v1/status","stop":"/api/v1/stop"}}
     * @return create result code
     */
    @Operation(summary = "createExternalSystem", description = "CREATE_EXTERNAL_SYSTEM_NOTES")
    @PostMapping()
    @ResponseStatus(HttpStatus.CREATED)
    @ApiException(CREATE_EXTERNAL_SYSTEM_ERROR)
    @OperatorLog(auditType = AuditType.EXTERNAL_SYSTEM_CREATE)
    public Result<ExternalSystem> createExternalSystem(@Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                                       @Parameter(name = "externalSystemParam", description = "EXTERNAL_SYSTEM_PARAM", required = true) @RequestBody String jsonStr) {
        BaseExternalSystemParams externalSystemParam = JSONUtils.parseObject(jsonStr, BaseExternalSystemParams.class);
        ExternalSystem externalSystem = externalSystemService.createExternalSystem(loginUser, externalSystemParam);
        return Result.success(externalSystem);
    }

    /**
     * update external system
     *
     * @param loginUser login user
     * @param id        external system id
     * @param jsonStr   external system param
     *                  example: {"type":"SEATUNNEL","name":"test-system","baseUrl":"http://localhost:8080","authConfig":{"client_id":"xxx","client_secret":"xxx","grant_type":"password"},"endpoints":{"auth":"/oauth/token","start":"/api/v1/start","status":"/api/v1/status","stop":"/api/v1/stop"}}
     * @return update result code
     */
    @Operation(summary = "updateExternalSystem", description = "UPDATE_EXTERNAL_SYSTEM_NOTES")
    @Parameters({
            @Parameter(name = "id", description = "EXTERNAL_SYSTEM_ID", required = true, schema = @Schema(implementation = int.class)),
            @Parameter(name = "externalSystemParam", description = "EXTERNAL_SYSTEM_PARAM", required = true, schema = @Schema(implementation = BaseExternalSystemParams.class))
    })
    @PutMapping(value = "/{id}")
    @ResponseStatus(HttpStatus.OK)
    @ApiException(UPDATE_EXTERNAL_SYSTEM_ERROR)
    @OperatorLog(auditType = AuditType.EXTERNAL_SYSTEM_UPDATE)
    public Result<ExternalSystem> updateExternalSystem(@Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                                       @PathVariable(value = "id") Integer id,
                                                       @RequestBody String jsonStr) {
        BaseExternalSystemParams externalSystemParam = JSONUtils.parseObject(jsonStr, BaseExternalSystemParams.class);
        externalSystemParam.setId(id);
        ExternalSystem externalSystem = externalSystemService.updateExternalSystem(loginUser, externalSystemParam);
        return Result.success(externalSystem);
    }

    /**
     * query external system detail
     *
     * @param loginUser login user
     * @param id external system id
     * @return external system detail
     */
    @Operation(summary = "queryExternalSystem", description = "QUERY_EXTERNAL_SYSTEM_NOTES")
    @Parameters({
            @Parameter(name = "id", description = "EXTERNAL_SYSTEM_ID", required = true, schema = @Schema(implementation = int.class, example = "100"))
    })
    @GetMapping(value = "/{id}")
    @ResponseStatus(HttpStatus.OK)
    @ApiException(QUERY_EXTERNAL_SYSTEM_ERROR)
    public Result<Object> queryExternalSystem(@Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                              @PathVariable("id") int id) {
        BaseExternalSystemParams externalSystem = externalSystemService.queryExternalSystem(id, loginUser);
        return Result.success(externalSystem);
    }

    /**
     * test external system connection
     *
     * @param loginUser login user
     * @param jsonStr   external system param
     * @return test connection result
     */
    @Operation(summary = "testExternalSystemConnection", description = "TEST_EXTERNAL_SYSTEM_CONNECTION_NOTES")
    @PostMapping("/test-connection")
    @ResponseStatus(HttpStatus.OK)
    @ApiException(TEST_EXTERNAL_SYSTEM_CONNECTION_ERROR)
    public Result<Object> testExternalSystemConnection(@Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                                       @Parameter(name = "externalSystemParam", description = "EXTERNAL_SYSTEM_PARAM", required = true) @RequestBody String jsonStr) {
        BaseExternalSystemParams externalSystemParam =  JSONUtils.parseObject(jsonStr, BaseExternalSystemParams.class);
        boolean connectionResult = externalSystemService.testExternalSystemConnection(loginUser, externalSystemParam);
        return Result.success(connectionResult);
    }

    /**
     * query external system list
     *
     * @param loginUser login user
     * @param searchVal search value
     * @param pageNo    page number
     * @param pageSize  page size
     * @return external system list
     */
    @Operation(summary = "queryExternalSystemListPaging", description = "QUERY_EXTERNAL_SYSTEM_LIST_NOTES")
    @Parameters({
            @Parameter(name = "searchVal", description = "SEARCH_VAL", schema = @Schema(implementation = String.class)),
            @Parameter(name = "pageNo", description = "PAGE_NO", required = true, schema = @Schema(implementation = int.class, example = "1")),
            @Parameter(name = "pageSize", description = "PAGE_SIZE", required = true, schema = @Schema(implementation = int.class, example = "10"))
    })
    @GetMapping()
    @ResponseStatus(HttpStatus.OK)
    @ApiException(QUERY_EXTERNAL_SYSTEM_ERROR)
    public Result<PageInfo<ExternalSystem>> queryExternalSystemListPaging(@Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                                                    @RequestParam(value = "searchVal", required = false) String searchVal,
                                                                    @RequestParam("pageNo") Integer pageNo,
                                                                    @RequestParam("pageSize") Integer pageSize) {
        checkPageParams(pageNo, pageSize);
        searchVal = ParameterUtils.handleEscapes(searchVal);
        PageInfo<ExternalSystem> result =
                externalSystemService.queryExternalSystemListPaging(loginUser, searchVal, pageNo, pageSize);
        return Result.success(result);
    }

    /**
     * query datasource by type
     *
     * @param loginUser login user
     * @return data source list page
     */
    @Operation(summary = "queryExternalSystemList", description = "QUERY_DATA_SOURCE_LIST_BY_TYPE_NOTES")
    @GetMapping(value = "/queryExternalSystemList")
    @ResponseStatus(HttpStatus.OK)
    @ApiException(QUERY_EXTERNAL_SYSTEM_ERROR)
    public Result<Object> queryDataSourceList(@Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser) {
        List<ExternalSystem> datasourceList = externalSystemService.queryDataSourceList(loginUser);
        return Result.success(datasourceList);
    }


    /**
     * delete external system
     *
     * @param loginUser login user
     * @param id        external system id
     * @return delete result
     */
    @Operation(summary = "deleteExternalSystem", description = "DELETE_EXTERNAL_SYSTEM_NOTES")
    @Parameters({
            @Parameter(name = "id", description = "EXTERNAL_SYSTEM_ID", required = true, schema = @Schema(implementation = int.class, example = "100"))
    })
    @DeleteMapping(value = "/{id}")
    @ResponseStatus(HttpStatus.OK)
    @ApiException(DELETE_EXTERNAL_SYSTEM_ERROR)
    @OperatorLog(auditType = AuditType.EXTERNAL_SYSTEM_DELETE)
    public Result<Object> deleteExternalSystem(@Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                               @PathVariable("id") int id) {
        externalSystemService.deleteExternalSystem(loginUser, id);
        return Result.success();
    }


    @Operation(summary = "queryExternalSystemTasks", description = "QUERY_EXTERNAL_SYSTEM_TASKS_NOTES")
    @GetMapping(value = "/queryExternalSystemTasks")
    @ResponseStatus(HttpStatus.OK)
    @ApiException(QUERY_EXTERNAL_SYSTEM_ERROR)
    public Result<List<ExternalSystemTaskQuery>> queryExternalSystemTasks(@Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                                                          @RequestParam("externalSystemId") Integer externalSystemId) {
        List<ExternalSystemTaskQuery> result =
                externalSystemService.queryExternalSystemTasks(loginUser, externalSystemId);
        return Result.success(result);
    }

}
