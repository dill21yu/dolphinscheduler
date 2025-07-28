import type { Component } from 'vue'
import utils from '@/utils'

// All TSX files under the views folder automatically generate mapping relationship
const modules = import.meta.glob('/src/views/**/**.tsx')
const components: { [key: string]: Component } = utils.mapping(modules)

export default {
  path: 'thirdparty-api-source',
  name: 'thirdparty-api-source',
  meta: {
    title: 'thirdparty_api_source',
    isMenuItem: true
  },
  children: [
    {
      path: '',
      name: 'thirdparty-api-source-list',
      component: components['thirdparty-api-source'],
      meta: {
        title: 'thirdparty_api_source',
        activeMenu: 'source-center',
        activeSide: '/source-center/thirdparty-api-source'
      }
    }
  ]
} 