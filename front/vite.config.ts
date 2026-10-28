import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { compression } from 'vite-plugin-compression2'

// https://vite.dev/config/
export default defineConfig({
  // 部署路径对齐生产：https://zjhxbb.xyz/点废成金/admin/（原生 nginx 子路径托管，三端同域共存）
  base: '/点废成金/admin/',
  plugins: [
    vue(),
    vueDevTools(),
    // Element Plus 组件按需引入（D4 性能规范）；样式在 main.ts 全量引入
    Components({ resolvers: [ElementPlusResolver({ importStyle: false })], dts: 'src/components.d.ts' }),
    // W-10 生产构建 gzip 压缩（>10KB 才压缩，保留原始文件）
    compression({ algorithms: ['gzip'], threshold: 10240, deleteOriginalAssets: false }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      // D0 共享层：设计令牌 / 请求封装 / Mock 引擎 / 通用业务组件
      '@shared': fileURLToPath(new URL('../front-shared/src', import.meta.url)),
    },
  },
  build: {
    sourcemap: false,
    // 注意：不能启用 manualChunks 重库分包（element-plus 独立 chunk）——rolldown 会把
    // 引用它的业务模块（request 封装等）错误归并进 element-plus chunk，形成跨 chunk 循环
    // 依赖，顶层初始化时 t is not a function 崩溃导致白屏（2026-08 线上事故根因）。
    // 依赖 rolldown 自动分包即可（组件级 chunk + 动态 import，首屏按需加载）。
  },
  server: {
    port: 5173,
    proxy: {
      // 联调期切真实后端：仅需关掉 VITE_USE_MOCK，无需改代码
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
})
