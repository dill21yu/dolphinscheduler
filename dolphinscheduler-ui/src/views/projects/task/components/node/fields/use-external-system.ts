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
import { ref, onMounted, nextTick, Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { queryExternalSystemList, queryExternalSystemTasks } from '@/service/modules/data-source'
import { indexOf, find } from 'lodash'
import type { IJsonItem } from '../types'

export function useExternalSystem(
  model: { [field: string]: any },
  params: {
    externalSystemField?: string
    taskField?: string
    span?: Ref | number
  } = {}
): IJsonItem[] {
  const { t } = useI18n()

  const externalSystemOptions = ref([] as { label: string; value: string }[])
  const taskOptions = ref([] as { label: string; value: string }[]) // 修改为 value: string

  const getExternalSystems = async () => {
    const res = await queryExternalSystemList()
    externalSystemOptions.value = res.map((item: any) => ({
      label: item.name,
      value: String(item.id)
    }))
  }

  const refreshTasks = async () => {
    const externalSystemId = model[params.externalSystemField || 'externalSystemId']
    if (!externalSystemId) return

    try {
      const res = await queryExternalSystemTasks(externalSystemId)
      taskOptions.value = res.map((item: any) => ({
        label: item.name,
        value: item.id // 确保 value 是字符串
      }))
    } catch (error) {
      console.error('Error fetching external system tasks:', error)
    }

    const taskField = params.taskField || 'task'
    if (!taskOptions.value.length && model[taskField]) model[taskField] = null
    if (taskOptions.value.length && model[taskField]) {
      const item = find(taskOptions.value, { value: model[taskField] })
      if (!item) {
        model[taskField] = null
      }
    }
  }

  const onChange = () => {
    // 清空任务选项
    taskOptions.value = []
    // 清空模型中的任务字段
    const taskField = params.taskField || 'externalTaskId'
    model[taskField] = null
    // 刷新任务选项
    refreshTasks()
  }

  onMounted(async () => {
    await getExternalSystems()
    await nextTick()
    refreshTasks()
  })

  return [
    {
      type: 'select',
      field: params.externalSystemField || 'externalSystemId', // 明确字段名
      span: params.span || 12,
      name: t('project.node.external_systems'),
      props: { 'on-update:value': onChange },
      options: externalSystemOptions,
      validate: {
        trigger: ['input', 'blur'],
        required: true
      }
    },
    {
      type: 'select',
      field: params.taskField || 'externalTaskId', // 明确字段名
      span: params.span || 12,
      name: t('project.node.external_system_tasks'),
      options: taskOptions,
      validate: {
        trigger: ['input', 'blur'],
        required: true,
        validator(unuse: any, value) {
          if (!value && value !== 0) {
            return Error(t('project.node.external_system_tasks'))
          }
        }
      }
    }
  ]
}