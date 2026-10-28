// ============================================================
// dianfei-user 入口（Track D · D2）
// Vant 组件按需引入（unplugin-vue-components），样式全量引入
// ============================================================
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { Lazyload } from 'vant'
import 'vant/lib/index.css'
import 'virtual:svg-icons-register'

import App from './App.vue'
import router from './router'
import '@/styles/index.scss'
import SvgIcon from '@/components/SvgIcon.vue'
import { applyTheme, getSavedTheme } from '@shared/theme'
import { restoreFromHandoff } from '@/api/token'

applyTheme(getSavedTheme(), false)

// C-O1 双身份互通：员工端「切换客户端」经 /auth/switch-user 换取 USER 会话写入中转，
// 首屏恢复内存令牌免重新登录（无中转令牌时静默，走正常登录/Refresh Cookie 链路）
restoreFromHandoff()

const app = createApp(App)

app.component('SvgIcon', SvgIcon)
app.use(createPinia())
app.use(router)
// D4 图片懒加载：v-lazy 指令（商城/科普配图进入视口再加载；WebP 由存储侧出图参数保证）
app.use(Lazyload, { lazyComponent: true })

app.mount('#app')
