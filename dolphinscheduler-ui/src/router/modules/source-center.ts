import datasource from './datasource'
import thirdpartyApiSource from './thirdparty-api-source'

export default {
  path: '/source-center',
  name: 'source-center',
  meta: { 
    title: '源中心',
    showSide: true,
    activeMenu: 'source-center'
  },
  component: () => import('@/layouts/content'),
  redirect: '/source-center/datasource',
  children: [
    datasource,
    thirdpartyApiSource
  ]
} 