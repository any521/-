// ============================================================
// ORT WASM 资产装载（本地化，不走 CDN）：
// 资产从 public/ort/ 迁移到 src/assets/ort/，经 Vite ?url 资产管线
// 产出 URL 后注入 ort.env.wasm.wasmPaths —— 直接指向 /public 文件会被
// Vite dev 拒绝（"should not be imported from source code" 报错）。
// 主线程（yoloDecode）与推理 Worker（yoloWorker）共用此配置。
// ============================================================
import * as ort from 'onnxruntime-web'
import ortMjsUrl from '@/assets/ort/ort-wasm-simd-threaded.jsep.mjs?url'

// WASM 体积较大且不随仓库分发（见 README）；用 glob 按存在性装载，
// 缺失时保持构建可用，运行时由调用方降级 mock。
const wasmAssets = import.meta.glob('../assets/ort/*.wasm', {
  eager: true,
  query: '?url',
  import: 'default',
}) as Record<string, string>
const ortWasmUrl = wasmAssets['../assets/ort/ort-wasm-simd-threaded.jsep.wasm'] ?? ''

/** 配置 ort WASM 资产路径；返回 false 表示 WASM 资产未就绪 */
export function configureOrtAssets(): boolean {
  if (!ortWasmUrl) return false
  ort.env.wasm.wasmPaths = {
    mjs: ortMjsUrl,
    wasm: ortWasmUrl,
  }
  return true
}
