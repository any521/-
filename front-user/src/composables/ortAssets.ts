// ============================================================
// ORT WASM 资产装载（本地化，不走 CDN）：
// 资产从 public/ort/ 迁移到 src/assets/ort/，经 Vite ?url 资产管线
// 产出 URL 后注入 ort.env.wasm.wasmPaths —— 直接指向 /public 文件会被
// Vite dev 拒绝（"should not be imported from source code" 报错）。
// 主线程（yoloDecode）与推理 Worker（yoloWorker）共用此配置。
// ============================================================
import * as ort from 'onnxruntime-web'
import ortMjsUrl from '@/assets/ort/ort-wasm-simd-threaded.jsep.mjs?url'
import ortWasmUrl from '@/assets/ort/ort-wasm-simd-threaded.jsep.wasm?url'

/** 配置 ort WASM 资产路径（创建 InferenceSession 前调用一次即可） */
export function configureOrtAssets(): void {
  ort.env.wasm.wasmPaths = {
    mjs: ortMjsUrl,
    wasm: ortWasmUrl,
  }
}
