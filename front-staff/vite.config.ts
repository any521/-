import { fileURLToPath, URL } from 'node:url'
import path from 'node:path'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { VantResolver } from '@vant/auto-import-resolver'
import { createSvgIconsPlugin } from 'vite-plugin-svg-icons'
import { compression } from 'vite-plugin-compression2'

// https://vite.dev/config/
export default defineConfig({
  // 部署路径对齐生产：https://zjhxbb.xyz/点废成金/staff/（原生 nginx 子路径托管，三端同域共存）
  base: '/点废成金/staff/',
  plugins: [
    vue(),
    // Vant 组件按需引入（D4 性能规范）；样式在 main.ts 全量引入
    Components({ resolvers: [VantResolver({ importStyle: false })], dts: 'src/components.d.ts' }),
    // 自定义 SVG 图标（SvgIcon 组件 + sprite）
    createSvgIconsPlugin({
      iconDirs: [path.resolve(process.cwd(), 'src/assets/icons')],
      symbolId: 'icon-[dir]-[name]',
    }),
    // W-10 生产构建 gzip 压缩（>10KB 才压缩，保留原始文件）
    compression({ algorithms: ['gzip'], threshold: 10240, deleteOriginalAssets: false }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      // D0 共享层：设计令牌 / 请求封装 / Mock 引擎 / 通用业务组件
      '@shared': fileURLToPath(new URL('../front-shared/src', import.meta.url)),
      // 共享层组件（front-shared/src）内 Vant 自动导入解析不到 vant（front-shared 无独立依赖）：
      // 显式指回本应用 node_modules，dev/构建一致（前缀匹配，vant/es 等子路径自动拼接）
      vant: fileURLToPath(new URL('./node_modules/vant', import.meta.url)),
    },
  },
  build: {
    sourcemap: false,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules/vant')) return 'vant'
        },
      },
    },
  },
  server: {
    port: 5175,
    // 经 front-user（5174）代理访问 /staff/ 时页面 location.port 为 5174：
    // 显式指定 HMR 端口，让 client 直连本 dev server 保持热更可用（C-O1 同域切换配套）
    hmr: { port: 5175 },
    proxy: {
      // 联调期切真实后端：仅需关掉 VITE_USE_MOCK，无需改代码
      // ws:true 必须开：IM 实时消息走 WebSocket（/api/ws/im），缺省时握手超时发不出去
      '/api': { target: 'http://localhost:8080', changeOrigin: true, ws: true },
      // C-O1 子路径化后：跳回用户端 /点废成金/ 时代理到用户端 dev server（5174），保持 dev/生产行为一致
      '/点废成金': { target: 'http://localhost:5174', changeOrigin: true, ws: true },
    },
  },
})
