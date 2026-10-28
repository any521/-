import type * as THREE from 'three'

/** 待机动画函数:t = 累计秒,dt = 帧间隔秒 */
export type ModelTick = (t: number, dt: number) => void

/** 模型 root.userData 契约(img2threejs 风格) */
export interface ModelUserData {
  /** 每帧待机动画,由 ThreeViewer 的 RAF 循环调用 */
  tick?: ModelTick
  /** 展示用中文名 */
  label?: string
  /** 参考色板(调试用) */
  palette?: Record<string, number>
  [key: string]: unknown
}

/** 读取 userData(类型安全) */
export function modelData(root: THREE.Object3D): ModelUserData {
  return root.userData as ModelUserData
}
