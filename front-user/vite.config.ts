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
  // 部署路径对齐生产：https://zjhxbb.xyz/点废成金（原生 nginx 子路径托管，三端同域共存）
  base: '/点废成金/',
  plugins: [
    vue(),
    // Vant 组件按需引入（D4 性能规范）；样式在 main.ts 全量引入
    Components({ resolvers: [VantResolver({ importStyle: false })], dts: 'src/components.d.ts' }),
    createSvgIconsPlugin({
      iconDirs: [path.resolve(process.cwd(), 'src/assets/icons')],
      symbolId: 'icon-[dir]-[name]',
    }),
    // W-10 生产构建 gzip 压缩（>10KB 才压缩，保留原始文件）；插件 2.x 字段为复数 algorithms
    compression({ algorithms: ['gzip'], threshold: 10240, deleteOriginalAssets: false }),
  ],
  resolve: {
    // 共享层源码位于 ../front-shared，裸依赖固定回本应用 node_modules
    // （CI 无仓库根 node_modules 兜底，否则报 Failed to resolve import "axios"）
    dedupe: ['axios', 'vue'],
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
    port: 5174,
    // 卡顿修复 B：跨域隔离头 → SharedArrayBuffer 可用 → ort-wasm 多线程；
    // credentialless 而非 require-corp：跨域图片（MinIO 等）无需 CORS 头仍可匿名加载
    headers: {
      'Cross-Origin-Opener-Policy': 'same-origin',
      'Cross-Origin-Embedder-Policy': 'credentialless',
    },
    proxy: {
      // 联调期切真实后端：仅需关掉 VITE_USE_MOCK，无需改代码
      // ws:true 必须开：IM 实时消息走 WebSocket（/api/ws/im），缺省时握手超时发不出去
      '/api': { target: 'http://localhost:8080', changeOrigin: true, ws: true },
      // C-O1 工作人员模式无感切换：dev 下 /staff/ 代理到员工端 dev server（5175），
      // 保持同源（localStorage df_staff_* token 共享）实现切换/工作台跳转；
      // 生产由 nginx 同域路由 /staff/，本配置仅 dev 生效不参与构建
      '/staff': { target: 'http://localhost:5175', changeOrigin: true, ws: true },
      // C-O1 子路径化后：/点废成金/staff/ 跳转员工端 dev server（5175），保持 dev/生产行为一致
      '/点废成金/staff': { target: 'http://localhost:5175', changeOrigin: true, ws: true },
    },
  },
  // 支持 onnxruntime-web 的 WASM 模型（F6 端侧 AR 识别）
  optimizeDeps: {
    exclude: ['onnxruntime-web']
  },
  worker: {
    format: 'es'
  }
})
