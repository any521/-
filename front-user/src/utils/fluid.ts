/**
 * PC 全局全屏流体背景 —— engine 薄封装
 *
 * 对外签名保持不变(FluidBackground.vue 在用):
 *   initFluidSimulation(canvas, colors?) => cleanup
 * 内部复用 utils/fluid/engine.ts(全屏背景:maskLeft 0、baseSpeed 0.12、
 * resolutionScale 1、第三色与第二色相同以保持旧版视觉)。
 */
import { createFluidRenderer } from './fluid/engine'
import { BACKGROUND_FRAG } from './fluid/shaders'

/** 品牌默认色(与 tokens.scss 对齐:墨绿 #2f6b5e / 青蓝绿 #3d8b85) */
const DEFAULT_COLOR1: [number, number, number] = [47 / 255, 107 / 255, 94 / 255];
const DEFAULT_COLOR2: [number, number, number] = [61 / 255, 139 / 255, 133 / 255];

export interface FluidColors {
  color1?: [number, number, number];
  color2?: [number, number, number];
}

export function initFluidSimulation(canvas: HTMLCanvasElement, colors: FluidColors = {}): () => void {
  const color1 = colors.color1 ?? DEFAULT_COLOR1;
  const color2 = colors.color2 ?? DEFAULT_COLOR2;

  const handle = createFluidRenderer(canvas, {
    // 第三色与第二色相同,保持旧版三色 mix + 亮度曲线的视觉效果
    colors: [color1, color2, color2],
    baseSpeed: 0.12,
    maskLeft: 0,
    seed: 0,
    resolutionScale: 1,
    dprCap: 1.5,
    fragmentShader: BACKGROUND_FRAG,
  });

  // 无 WebGL 时返回 no-op(CSS 渐变降级由 Vue 组件处理)
  return () => handle?.dispose();
}
