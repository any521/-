import * as THREE from 'three'
import { RECYCLE_CATEGORIES } from '@shared/data/baseData'
import { createPlasticBottleModel } from './models/plasticBottle'
import { createGlassBottleModel } from './models/glassBottle'
import { createPaperCupModel } from './models/paperCup'
import { createKitchenWasteModel } from './models/kitchenWaste'
import { createCardboardBoxModel } from './models/cardboardBox'
import { createAluminumCanModel } from './models/aluminumCan'
import { createOldPhoneModel } from './models/oldPhone'
import { createNewspaperStackModel } from './models/newspaperStack'
import { createSmallApplianceModel } from './models/smallAppliance'
import { modelData } from './types'

/**
 * 3D 模型注册表(img2threejs 风格)
 * 品类(RECYCLE_CATEGORIES.code)→ 程序化模型工厂 的唯一映射入口。
 * 新增品类时必须同时补充 RECYCLE_CATEGORIES 与 CATEGORY_TO_MODEL。
 */

export type ModelKey =
  | 'plasticBottle'
  | 'glassBottle'
  | 'paperCup'
  | 'kitchenWaste'
  | 'cardboardBox'
  | 'aluminumCan'
  | 'oldPhone'
  | 'newspaperStack'
  | 'smallAppliance'

const FACTORIES: Record<ModelKey, () => THREE.Group> = {
  plasticBottle: () => createPlasticBottleModel(),
  // Track G T5：glassBottle 从 plasticBottle 的 glass 变体升级为独立模型（圆肩瓶 + 通透材质）
  glassBottle: () => createGlassBottleModel(),
  // Track G T5：高频「其他垃圾」预设（无对应 RECYCLE_CATEGORIES 类目，仅注册模型供直传/未来映射）
  paperCup: () => createPaperCupModel(),
  kitchenWaste: () => createKitchenWasteModel(),
  cardboardBox: () => createCardboardBoxModel(),
  aluminumCan: () => createAluminumCanModel(),
  oldPhone: () => createOldPhoneModel(),
  newspaperStack: () => createNewspaperStackModel(),
  smallAppliance: () => createSmallApplianceModel(),
}

/**
 * 品类默认映射 + 物品名回退两段式（Track G T5）：
 * - 品类默认：识别结果命中 RECYCLE_CATEGORIES 时按品类映射（本段不变，避免破坏既有回退语义）；
 * - 物品名回退：resolveModelKey 最后分支按任意字符串键命中（识别 item 直命中；非品类，不占品类默认）。
 */
export const CATEGORY_TO_MODEL: Record<string, ModelKey> = {
  plastic: 'plasticBottle',
  glass: 'glassBottle',
  metal: 'aluminumCan',
  paper: 'newspaperStack',
  electronics: 'oldPhone',
  appliance: 'smallAppliance',
  fabric: 'cardboardBox',
  // —— 物品名级 fallback（识别 item 直命中；不新增类目，类目字典事实源 baseData.ts）——
  '纸杯': 'paperCup', '奶茶杯': 'paperCup', '一次性纸杯': 'paperCup',
  // 厨余不在回收品类字典，仅物品名命中（kitchenWaste 为 stylized 低多边形近似，见 models/kitchenWaste.ts）
  '香蕉皮': 'kitchenWaste', '果皮': 'kitchenWaste', '菜叶': 'kitchenWaste', '剩饭': 'kitchenWaste',
}

export const DEFAULT_MODEL: ModelKey = 'cardboardBox'

/** 展示名(优先取模型自身 label) */
export const MODEL_LABELS: Record<ModelKey, string> = {
  plasticBottle: '塑料瓶',
  glassBottle: '玻璃瓶',
  paperCup: '纸杯',
  kitchenWaste: '厨余垃圾',
  cardboardBox: '废纸箱',
  aluminumCan: '易拉罐',
  oldPhone: '旧手机',
  newspaperStack: '废纸堆',
  smallAppliance: '小家电',
}

const MODEL_KEY_SET = new Set<string>(Object.keys(FACTORIES))

/**
 * 解析模型 key:支持 ModelKey 直传、品类 code('plastic')、品类中文名('塑料类')。
 * 未知输入兜底 DEFAULT_MODEL,绝不抛错。
 */
export function resolveModelKey(codeOrName?: string | null): ModelKey {
  if (!codeOrName) return DEFAULT_MODEL
  if (MODEL_KEY_SET.has(codeOrName)) return codeOrName as ModelKey
  const cat =
    RECYCLE_CATEGORIES.find((c) => c.code === codeOrName || c.name === codeOrName) ??
    // 识别结果可能不带「类」后缀（如「塑料」「废纸」），做包含匹配
    RECYCLE_CATEGORIES.find((c) => codeOrName.length >= 2 && c.name.includes(codeOrName))
  if (cat) return CATEGORY_TO_MODEL[cat.code] ?? DEFAULT_MODEL
  return CATEGORY_TO_MODEL[codeOrName] ?? DEFAULT_MODEL
}

/** 由 key 创建模型实例(每次调用新建,调用方负责 disposeGroup) */
export function createModel(key: ModelKey): THREE.Group {
  return FACTORIES[key]()
}

/** 深度释放:遍历几何体 / 材质 / 纹理 */
export function disposeGroup(root: THREE.Object3D): void {
  root.traverse((obj) => {
    if (obj instanceof THREE.Mesh) {
      obj.geometry?.dispose()
      const mats = Array.isArray(obj.material) ? obj.material : [obj.material]
      mats.forEach((m) => {
        if (!m) return
        const rec = m as unknown as Record<string, unknown>
        ;['map', 'normalMap', 'roughnessMap', 'metalnessMap', 'emissiveMap', 'aoMap', 'bumpMap'].forEach((k) => {
          const tex = rec[k] as THREE.Texture | undefined
          tex?.dispose()
        })
        m.dispose()
      })
    }
  })
}

export { modelData }
