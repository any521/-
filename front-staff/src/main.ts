// ============================================================
// dianfei-staff 入口（Track D · D3）
// Vant 组件按需引入（unplugin-vue-components），样式全量引入
// ============================================================
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import 'vant/lib/index.css'
import 'virtual:svg-icons-register'

import App from './App.vue'
import router from './router'
import '@/styles/index.scss'
import SvgIcon from '@/components/SvgIcon.vue'
import { applyTheme, getSavedTheme } from '@shared/theme'
import { restoreFromHandoff } from '@/api/token'

applyTheme(getSavedTheme(), false)

// 免登录切换（C-O1）：router 守卫前恢复用户端中转的 access token，避免跳登录页
restoreFromHandoff()

const app = createApp(App)

app.component('SvgIcon', SvgIcon)
app.use(createPinia())
app.use(router)

app.mount('#app')
