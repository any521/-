// ============================================================
// dianfei-admin 入口（Track D · D1）
// Element Plus 组件按需引入（unplugin-vue-components），样式全量引入
// ============================================================
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import 'element-plus/dist/index.css'
// Element Plus 官方暗色变量（html.dark 类触发，见 @shared/theme applyTheme）：
// 覆盖弹窗/下拉/日期选择/描述列表等全部组件暗色化，品牌色由 :root[data-theme='dark'] 二次覆盖
import 'element-plus/theme-chalk/dark/css-vars.css'
import * as ElIcons from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import { setupPermissionDirective } from './directives/permission'
import '@/styles/index.scss'
import { applyTheme, getSavedTheme } from '@shared/theme'

applyTheme(getSavedTheme(), false)

const app = createApp(App)

// 图标全局注册（菜单图标按名称动态渲染）
for (const [name, comp] of Object.entries(ElIcons)) {
  app.component(name, comp)
}

app.use(createPinia())
app.use(router)
setupPermissionDirective(app)

app.mount('#app')
