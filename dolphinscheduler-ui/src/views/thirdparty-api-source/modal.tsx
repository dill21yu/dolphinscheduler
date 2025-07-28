import { defineComponent, PropType, reactive, watch, computed, ref } from 'vue'
import type { FormInst } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import styles from './index.module.scss'
import {
  NModal,
  NForm,
  NFormItem,
  NInput,
  NButton,
  NSpace,
  NDivider,
  NDynamicInput,
  NSelect
} from 'naive-ui'
import MonacoEditor from '@/components/monaco-editor'

export default defineComponent({
  name: 'ThirdpartyApiSourceModal',
  props: {
    show: Boolean,
    data: Object as PropType<any>
  },
  emits: ['close', 'submit', 'test'],
  setup(props, { emit }) {
    const { t } = useI18n()

    const authTypeOptions = computed(() => [
      { label: t('thirdparty_api_source.basic_auth'), value: 'BASIC_AUTH' },
      { label: t('thirdparty_api_source.oauth2'), value: 'OAUTH2' },
      { label: t('thirdparty_api_source.jwt'), value: 'JWT' }
    ])

    const methodOptions = computed(() => [
      { label: t('thirdparty_api_source.get'), value: 'GET' },
      { label: t('thirdparty_api_source.post'), value: 'POST' },
      { label: t('thirdparty_api_source.put'), value: 'PUT' }
    ])

    const form = reactive({
      systemName: '',
      serviceAddress: '',
      fieldMappings: [] as any[],
      authConfig: {
        authType: 'BASIC_AUTH',
        basicUsername: '',
        basicPassword: '',
        jwtToken: '',
        oauth2TokenUrl: '',
        oauth2ClientId: '',
        oauth2ClientSecret: '',
        oauth2GrantType: '',
        oauth2Username: '',
        oauth2Password: '',
        authMappings: [] as any[]
      },
      selectInterface: {
        url: '',
        method: 'GET',
        parameters: [] as any[],
        body: ''
      },
      submitInterface: {
        url: '',
        method: 'POST',
        parameters: [] as any[],
        body: ''
      },
      pollStatusInterface: {
        url: '',
        method: 'GET',
        parameters: [] as any[],
        body: '',
        pollingSuccessConfig: {
          successField: '',
          successValue: ''
        },
        pollingFailureConfig: {
          successField: '',
          successValue: ''
        }
      },
      stopInterface: {
        url: '',
        method: 'POST',
        parameters: [] as any[],
        body: ''
      }
    })

    // 新增：表单校验规则
    const rules = {
      systemName: [
        { required: true, message: t('thirdparty_api_source.system_name_required'), trigger: 'blur' }
      ],
      serviceAddress: [
        { required: true, message: t('thirdparty_api_source.service_address_required'), trigger: 'blur' }
      ],
      'authConfig.authType': [
        { required: true, message: t('thirdparty_api_source.auth_type_required'), trigger: 'change' }
      ],
      'authConfig.basicUsername': [
        {
          validator: (rule: any, value: any) => {
            if (form.authConfig.authType === 'BASIC_AUTH' && !value) {
              return new Error(t('thirdparty_api_source.username_required'))
            }
            return true
          },
          trigger: 'blur'
        }
      ],
      'authConfig.basicPassword': [
        {
          validator: (rule: any, value: any) => {
            if (form.authConfig.authType === 'BASIC_AUTH' && !value) {
              return new Error(t('thirdparty_api_source.password_required'))
            }
            return true
          },
          trigger: 'blur'
        }
      ],
      'authConfig.oauth2TokenUrl': [
        {
          validator: (rule: any, value: any) => {
            if (form.authConfig.authType === 'OAUTH2' && !value) {
              return new Error(t('thirdparty_api_source.oauth2_token_url_required'))
            }
            return true
          },
          trigger: 'blur'
        }
      ],
      'authConfig.oauth2ClientId': [
        {
          validator: (rule: any, value: any) => {
            if (form.authConfig.authType === 'OAUTH2' && !value) {
              return new Error(t('thirdparty_api_source.oauth2_client_id_required'))
            }
            return true
          },
          trigger: 'blur'
        }
      ],
      'authConfig.oauth2ClientSecret': [
        {
          validator: (rule: any, value: any) => {
            if (form.authConfig.authType === 'OAUTH2' && !value) {
              return new Error(t('thirdparty_api_source.oauth2_client_secret_required'))
            }
            return true
          },
          trigger: 'blur'
        }
      ],
      'authConfig.oauth2GrantType': [
        {
          validator: (rule: any, value: any) => {
            if (form.authConfig.authType === 'OAUTH2' && !value) {
              return new Error(t('thirdparty_api_source.oauth2_grant_type_required'))
            }
            return true
          },
          trigger: 'blur'
        }
      ],
      'authConfig.jwtToken': [
        {
          validator: (rule: any, value: any) => {
            if (form.authConfig.authType === 'JWT' && !value) {
              return new Error(t('thirdparty_api_source.jwt_token_required'))
            }
            return true
          },
          trigger: 'blur'
        }
      ],
      // 新增接口地址必填
      'selectInterface.url': [
        { required: true, message: t('thirdparty_api_source.input_interface_url_required'), trigger: ['blur', 'change'] }
      ],
      'submitInterface.url': [
        { required: true, message: t('thirdparty_api_source.submit_interface_url_required'), trigger: ['blur', 'change'] }
      ],
      'pollStatusInterface.url': [
        { required: true, message: t('thirdparty_api_source.query_interface_url_required'), trigger: ['blur', 'change'] }
      ],
      'stopInterface.url': [
        { required: true, message: t('thirdparty_api_source.stop_interface_url_required'), trigger: ['blur', 'change'] }
      ]
    }

    // 新增：表单ref
    const formRef = ref<FormInst | null>(null)

    const systemFieldOptions = computed(() => {
      return form.fieldMappings
        .filter(item => item.externalField)
        .map((item) => ({ label: item.externalField, value: item.externalField }))
    })

    watch(() => props.data, (val) => {
      if (val) {
        Object.assign(form, JSON.parse(JSON.stringify(val)))
      } else {
        Object.assign(form, {
          systemName: '',
          serviceAddress: '',
          fieldMappings: [],
          authConfig: {
            authType: 'BASIC_AUTH',
            basicUsername: '',
            basicPassword: '',
            jwtToken: '',
            oauth2TokenUrl: '',
            oauth2ClientId: '',
            oauth2ClientSecret: '',
            oauth2GrantType: '',
            oauth2Username: '',
            oauth2Password: '',
            authMappings: []
          },
          selectInterface: { url: '', method: 'GET', parameters: [], body: '' },
          submitInterface: { url: '', method: 'POST', parameters: [], body: '' },
          pollStatusInterface: {
            url: '', method: 'GET', parameters: [], body: '',
            pollingSuccessConfig: { successField: '', successValue: '' },
            pollingFailureConfig: { successField: '', successValue: '' }
          },
          stopInterface: { url: '', method: 'POST', parameters: [], body: '' }
        })
      }
    }, { immediate: true })

    const handleClose = () => emit('close')
    // 修改：提交时校验
    const handleSubmit = () => {
      (formRef.value as any)?.validate((errors: any) => {
        if (!errors) {
          emit('submit', JSON.parse(JSON.stringify(form)))
        }
      })
    }
    // 测试按钮也校验
    const handleTest = () => {
      (formRef.value as any)?.validate((errors: any) => {
        if (!errors) {
          emit('test', JSON.parse(JSON.stringify(form)))
        }
      })
    }

    // location 下拉选项与 method 联动
    const getLocationOptions = (method: string) => {
      if (method === 'GET') {
        return [
          { label: t('thirdparty_api_source.header'), value: 'Header' },
          { label: t('thirdparty_api_source.query'), value: 'Query' }
        ]
      } else if (method === 'POST' || method === 'PUT') {
        return [
          { label: t('thirdparty_api_source.header'), value: 'Header' },
          { label: t('thirdparty_api_source.query'), value: 'Query' },
        ]
      }
      return []
    }

    return () => (
      <NModal show={props.show} preset="card" class="dialog-source-modal" style="position: relative; width: 50%; max-width: 700px; max-height: 100vh;" title={form.systemName ? t('thirdparty_api_source.edit_thirdparty_api_source') : t('thirdparty_api_source.create_thirdparty_api_source')} onClose={handleClose}>
        <div style="max-height: 70vh; overflow-y: auto;">
          <NForm labelWidth={120} labelAlign="left" model={form} rules={rules} ref={formRef}>
            <NFormItem label={t('thirdparty_api_source.system_name')} path="systemName" required>
              <NInput v-model={[form.systemName, 'value']} placeholder={t('thirdparty_api_source.system_name_tips')} />
            </NFormItem>
            <NFormItem label={t('thirdparty_api_source.service_address')} path="serviceAddress" required>
              <NInput v-model={[form.serviceAddress, 'value']} placeholder={t('thirdparty_api_source.service_address_tips')} />
            </NFormItem>
            <NFormItem label={t('thirdparty_api_source.field_mapping')} labelAlign="left">
              <NDynamicInput v-model={[form.fieldMappings, 'value']} onCreate={() => ({ externalField: '', internalField: '' })} >
                {{
                  default: ({ value }: { value: { externalField: string; internalField: string } }) => (
                    <NSpace>
                      <NInput v-model={[value.externalField, 'value']} placeholder={t('thirdparty_api_source.external_field')} />
                      <NInput v-model={[value.internalField, 'value']} placeholder={t('thirdparty_api_source.internal_field')} />
                    </NSpace>
                  )
                }}
              </NDynamicInput>
            </NFormItem>
            <NDivider />
            <NFormItem label={t('thirdparty_api_source.auth_type')} path="authConfig.authType" required>
              <NSelect v-model={[form.authConfig.authType, 'value']} options={authTypeOptions.value} style="width:150px" placeholder={t('thirdparty_api_source.auth_type_tips')} />
            </NFormItem>
            {/* BASIC_AUTH */}
            <NFormItem v-show={form.authConfig.authType === 'BASIC_AUTH'} label={t('thirdparty_api_source.username')} path="authConfig.basicUsername" required>
              <NInput v-model={[form.authConfig.basicUsername, 'value']} placeholder={t('thirdparty_api_source.username_tips')} />
            </NFormItem>
            <NFormItem v-show={form.authConfig.authType === 'BASIC_AUTH'} label={t('thirdparty_api_source.password')} path="authConfig.basicPassword" required>
              <NInput v-model={[form.authConfig.basicPassword, 'value']} placeholder={t('thirdparty_api_source.password_tips')} type="password" show-password-on="click" />
            </NFormItem>
            {/* OAUTH2 */}
            <NFormItem v-show={form.authConfig.authType === 'OAUTH2'} label={t('thirdparty_api_source.oauth2_token_url')} path="authConfig.oauth2TokenUrl" required>
              <NInput v-model={[form.authConfig.oauth2TokenUrl, 'value']} placeholder={t('thirdparty_api_source.oauth2_token_url_tips')} />
            </NFormItem>
            <NFormItem v-show={form.authConfig.authType === 'OAUTH2'} label={t('thirdparty_api_source.oauth2_client_id')} path="authConfig.oauth2ClientId" required>
              <NInput v-model={[form.authConfig.oauth2ClientId, 'value']} placeholder={t('thirdparty_api_source.oauth2_client_id_tips')} />
            </NFormItem>
            <NFormItem v-show={form.authConfig.authType === 'OAUTH2'} label={t('thirdparty_api_source.oauth2_client_secret')} path="authConfig.oauth2ClientSecret" required>
              <NInput v-model={[form.authConfig.oauth2ClientSecret, 'value']} placeholder={t('thirdparty_api_source.oauth2_client_secret_tips')} />
            </NFormItem>
            <NFormItem v-show={form.authConfig.authType === 'OAUTH2'} label={t('thirdparty_api_source.oauth2_grant_type')} path="authConfig.oauth2GrantType" required>
              <NInput v-model={[form.authConfig.oauth2GrantType, 'value']} placeholder={t('thirdparty_api_source.oauth2_grant_type_tips')} />
            </NFormItem>
            <NFormItem v-show={form.authConfig.authType === 'OAUTH2'} label={t('thirdparty_api_source.oauth2_username')}>
              <NInput v-model={[form.authConfig.oauth2Username, 'value']} placeholder={t('thirdparty_api_source.oauth2_username_tips')} />
            </NFormItem>
            <NFormItem v-show={form.authConfig.authType === 'OAUTH2'} label={t('thirdparty_api_source.oauth2_password')}>
              <NInput v-model={[form.authConfig.oauth2Password, 'value']} placeholder={t('thirdparty_api_source.oauth2_password_tips')} type="password" show-password-on="click" />
            </NFormItem>
            {/* JWT */}
            <NFormItem v-show={form.authConfig.authType === 'JWT'} label={t('thirdparty_api_source.jwt_token')} path="authConfig.jwtToken" required>
              <NInput v-model={[form.authConfig.jwtToken, 'value']} placeholder={t('thirdparty_api_source.jwt_token_tips')} />
            </NFormItem>
            <NFormItem label={t('thirdparty_api_source.additional_params')}>
              <NDynamicInput v-model={[form.authConfig.authMappings, 'value']} onCreate={() => ({ key: '', value: '' })} >
                {{
                  default: ({ value }: { value: { key: string; value: string } }) => (
                    <NSpace>
                      <NInput v-model={[value.key, 'value']} placeholder={t('thirdparty_api_source.key')} style="width:200px" />
                      <NInput v-model={[value.value, 'value']} placeholder={t('thirdparty_api_source.value')} style="width:200px" />
                    </NSpace>
                  )
                }}
              </NDynamicInput>
            </NFormItem>
            <NDivider />
            <NFormItem label={t('thirdparty_api_source.input_interface')} path="selectInterface.url" required>
              <NInput v-model={[form.selectInterface.url, 'value']} placeholder={t('thirdparty_api_source.input_interface_tips')} onChange={() => formRef.value?.validate?.()} />
              <NSelect v-model={[form.selectInterface.method, 'value']} options={methodOptions.value} style="width:150px" />
            </NFormItem>
            <NFormItem label={t('thirdparty_api_source.parameters')}>
              <NDynamicInput v-model={[form.selectInterface.parameters, 'value']} onCreate={() => ({ paramName: '', paramValue: null, location: 'Header' })} >
                {{
                  default: ({ value }: { value: { paramName: string; paramValue: any; location: string } }) => (
                    <NSpace>
                      <NSelect v-model={[value.location, 'value']} options={getLocationOptions(form.selectInterface.method)} placeholder={t('thirdparty_api_source.param_location_tips')} style="width:120px" />
                      <NInput v-model={[value.paramName, 'value']} placeholder={t('thirdparty_api_source.param_name_tips')} style="width:150px" />
                      <NSelect v-model={[value.paramValue, 'value']} options={systemFieldOptions.value} placeholder={t('thirdparty_api_source.system_field_tips')} style="width:150px" />
                    </NSpace>
                  )
                }}
              </NDynamicInput>
            </NFormItem>
            {(form.selectInterface.method === 'POST' || form.selectInterface.method === 'PUT') && (
              <NFormItem label={t('thirdparty_api_source.request_body')}>
                <MonacoEditor
                  v-model={[form.selectInterface.body, 'value']}
                  options={{
                    language: 'json',
                    readOnly: false
                  }}
                />
              </NFormItem>
            )}
            <NDivider />
            <NFormItem label={t('thirdparty_api_source.submit_interface')} path="submitInterface.url" required>
              <NInput v-model={[form.submitInterface.url, 'value']} placeholder={t('thirdparty_api_source.submit_interface_tips')} style="margin-bottom: 8px;" onChange={() => formRef.value?.validate?.()} />
              <NSelect v-model={[form.submitInterface.method, 'value']} options={methodOptions.value} style="width:150px; margin-bottom: 8px;" />
            </NFormItem>
            <NFormItem label={t('thirdparty_api_source.parameters')}>
              <NDynamicInput v-model={[form.submitInterface.parameters, 'value']} onCreate={() => ({ paramName: '', paramValue: null, location: 'Header' })} >
                {{
                  default: ({ value }: { value: { paramName: string; paramValue: any; location: string } }) => (
                    <NSpace>
                      <NSelect v-model={[value.location, 'value']} options={getLocationOptions(form.submitInterface.method)} placeholder={t('thirdparty_api_source.param_location_tips')} style="width:120px" />
                      <NInput v-model={[value.paramName, 'value']} placeholder={t('thirdparty_api_source.param_name_tips')} style="width:150px" />
                      <NSelect v-model={[value.paramValue, 'value']} options={systemFieldOptions.value} placeholder={t('thirdparty_api_source.system_field_tips')} style="width:150px" />
                    </NSpace>
                  )
                }}
              </NDynamicInput>
            </NFormItem>
            {(form.submitInterface.method === 'POST' || form.submitInterface.method === 'PUT') && (
              <NFormItem label={t('thirdparty_api_source.request_body')}>
                <MonacoEditor
                  v-model={[form.submitInterface.body, 'value']}
                  options={{
                    language: 'json',
                    readOnly: false
                  }}
                />
              </NFormItem>
            )}
            <NDivider />
            <NFormItem label={t('thirdparty_api_source.query_interface')} path="pollStatusInterface.url" required>
              <NInput v-model={[form.pollStatusInterface.url, 'value']} placeholder={t('thirdparty_api_source.query_interface_tips')} onChange={() => formRef.value?.validate?.()} />
              <NSelect v-model={[form.pollStatusInterface.method, 'value']} options={methodOptions.value} style="width:150px" />
            </NFormItem>
            <NFormItem label={t('thirdparty_api_source.parameters')}>
              <NDynamicInput v-model={[form.pollStatusInterface.parameters, 'value']} onCreate={() => ({ paramName: '', paramValue: null, location: '', systemField: '' })} >
                {{
                  default: ({ value }: { value: { paramName: string; paramValue: any; location: string; systemField: string } }) => (
                    <NSpace>
                      <NSelect v-model={[value.location, 'value']} options={getLocationOptions(form.pollStatusInterface.method)} placeholder={t('thirdparty_api_source.param_location_tips')} style="width:120px" />
                      <NInput v-model={[value.paramName, 'value']} placeholder={t('thirdparty_api_source.param_name_tips')} style="width:150px" />
                      <NSelect v-model={[value.paramValue, 'value']} options={systemFieldOptions.value} placeholder={t('thirdparty_api_source.system_field_tips')} style="width:150px" />
                    </NSpace>
                  )
                }}
              </NDynamicInput>
            </NFormItem>
            {(form.pollStatusInterface.method === 'POST' || form.pollStatusInterface.method === 'PUT') && (
              <NFormItem label={t('thirdparty_api_source.request_body')}>
                <MonacoEditor
                  v-model={[form.pollStatusInterface.body, 'value']}
                  options={{
                    language: 'json',
                    readOnly: false
                  }}
                />
              </NFormItem>
            )}
            <NFormItem label={t('thirdparty_api_source.success_condition')}>
              <NInput v-model={[form.pollStatusInterface.pollingSuccessConfig.successField, 'value']} placeholder={t('thirdparty_api_source.success_field_tips')} style="width:50%" />
              <NInput v-model={[form.pollStatusInterface.pollingSuccessConfig.successValue, 'value']} placeholder={t('thirdparty_api_source.success_value_tips')} style="width:50%; margin-left:8px;" />
            </NFormItem>
            <NFormItem label={t('thirdparty_api_source.failure_condition')}>
              <NInput v-model={[form.pollStatusInterface.pollingFailureConfig.successField, 'value']} placeholder={t('thirdparty_api_source.failure_field_tips')} style="width:50%" />
              <NInput v-model={[form.pollStatusInterface.pollingFailureConfig.successValue, 'value']} placeholder={t('thirdparty_api_source.failure_value_tips')} style="width:50%; margin-left:8px;" />
            </NFormItem>
            <NDivider />
            <NFormItem label={t('thirdparty_api_source.stop_interface')} path="stopInterface.url" required>
              <NInput v-model={[form.stopInterface.url, 'value']} placeholder={t('thirdparty_api_source.stop_interface_tips')} onChange={() => formRef.value?.validate?.()} />
              <NSelect v-model={[form.stopInterface.method, 'value']} options={methodOptions.value} style="width:150px" />
            </NFormItem>
            <NFormItem label={t('thirdparty_api_source.parameters')}>
              <NDynamicInput v-model={[form.stopInterface.parameters, 'value']} onCreate={() => ({ paramName: '', paramValue: null, location: 'Header' })} >
                {{
                  default: ({ value }: { value: { paramName: string; paramValue: any; location: string } }) => (
                    <NSpace>
                      <NSelect v-model={[value.location, 'value']} options={getLocationOptions(form.stopInterface.method)} placeholder={t('thirdparty_api_source.param_location_tips')} style="width:120px" />
                      <NInput v-model={[value.paramName, 'value']} placeholder={t('thirdparty_api_source.param_name_tips')} style="width:150px" />
                      <NSelect v-model={[value.paramValue, 'value']} options={systemFieldOptions.value} placeholder={t('thirdparty_api_source.system_field_tips')} style="width:120px" />
                    </NSpace>
                  )
                }}
              </NDynamicInput>
            </NFormItem>
            {(form.stopInterface.method === 'POST' || form.stopInterface.method === 'PUT') && (
              <NFormItem label={t('thirdparty_api_source.request_body')}>
                <MonacoEditor
                  v-model={[form.stopInterface.body, 'value']}
                  options={{
                    language: 'json',
                    readOnly: false
                  }}
                />
              </NFormItem>
            )}
          </NForm>
        </div>
        <div className={styles['modal-footer']}>
          <NSpace justify="end">
            <NButton onClick={handleClose}>{t('thirdparty_api_source.cancel')}</NButton>
            <NButton type="primary" onClick={handleSubmit}>{t('thirdparty_api_source.submit')}</NButton>
            <NButton type="success" onClick={handleTest}>{t('thirdparty_api_source.test')}</NButton>
          </NSpace>
        </div>
      </NModal >
    )
  }
})
