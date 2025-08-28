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

ALTER TABLE t_ds_workflow_definition DROP PRIMARY KEY;
ALTER TABLE t_ds_workflow_definition ADD PRIMARY KEY(id);
ALTER TABLE t_ds_workflow_definition ADD UNIQUE KEY uniq_workflow_definition_code (code);


DROP TABLE IF EXISTS `t_ds_external_system`;
CREATE TABLE `t_ds_external_system` (
  `id` INT(11) NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `name` VARCHAR(255) COLLATE utf8_bin NOT NULL COMMENT 'System name',
  `type` VARCHAR(50) COLLATE utf8_bin COMMENT '[Temporarily unused] System type',
  `connection_params` TEXT COLLATE utf8_bin NOT NULL COMMENT 'Connection parameters (JSON format)',
  `user_id` INT(11) NOT NULL COMMENT 'Creator user ID',
  `create_time` DATETIME NOT NULL COMMENT 'Creation time',
  `update_time` DATETIME NOT NULL COMMENT 'Update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=INNODB  DEFAULT CHARSET=utf8 COLLATE=utf8_bin

DROP TABLE IF EXISTS `t_ds_relation_external_system_user`;
CREATE TABLE `t_ds_relation_external_system_user` (
  `id` INT(11) NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `user_id` INT(11) NOT NULL COMMENT 'User ID',
  `external_system_id` INT(11) NOT NULL COMMENT 'External system ID',
  `perm` INT(11) DEFAULT '1' COMMENT 'Permission level',
  `create_time` DATETIME DEFAULT NULL COMMENT 'Creation time',
  `update_time` DATETIME DEFAULT NULL COMMENT 'Update time',
  PRIMARY KEY (`id`)
) ENGINE=INNODB DEFAULT CHARSET=utf8 COLLATE=utf8_bin
