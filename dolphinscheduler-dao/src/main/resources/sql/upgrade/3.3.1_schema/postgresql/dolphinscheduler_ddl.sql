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

drop index if exists workflow_definition_index;
create unique index uniq_workflow_definition_code on t_ds_workflow_definition (code);
ALTER TABLE t_ds_command DROP COLUMN test_flag;
ALTER TABLE t_ds_error_command DROP COLUMN test_flag;
ALTER TABLE t_ds_workflow_instance DROP COLUMN test_flag;
ALTER TABLE t_ds_task_instance DROP COLUMN test_flag;

ALTER TABLE t_ds_workflow_task_lineage
DROP CONSTRAINT t_ds_workflow_task_lineage_pkey;
CREATE SEQUENCE t_ds_workflow_task_lineage_id_seq;
ALTER TABLE t_ds_workflow_task_lineage
ALTER COLUMN id TYPE integer;
ALTER TABLE t_ds_workflow_task_lineage
ALTER COLUMN id SET DEFAULT nextval('t_ds_workflow_task_lineage_id_seq');
ALTER SEQUENCE t_ds_workflow_task_lineage_id_seq
OWNED BY t_ds_workflow_task_lineage.id;
ALTER TABLE t_ds_workflow_task_lineage
ALTER COLUMN id SET NOT NULL;
ALTER TABLE t_ds_workflow_task_lineage
ADD PRIMARY KEY (id);


DROP TABLE IF EXISTS t_ds_external_system;
CREATE TABLE t_ds_external_system (
    id SERIAL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50),
    connection_params TEXT NOT NULL,
    user_id INTEGER NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_name UNIQUE (name)
);

DROP TABLE IF EXISTS t_ds_relation_external_system_user;
CREATE TABLE t_ds_relation_external_system_user (
    id SERIAL,
    user_id INTEGER NOT NULL,
    external_system_id INTEGER NOT NULL,
    perm INTEGER DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);