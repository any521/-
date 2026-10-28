import * as THREE from 'three'
import { modelData } from '../types'

/**
 * 程序化纸杯/奶茶杯（img2threejs 风格，Track G T5 预设）。
 * 「其他垃圾」高频品类：lathe 微锥杯身（上宽下窄）+ 杯盖（带饮口）+ 吸管 tube。
 * 杯身纸材质（粗糙 + 纤维感），杯盖透明塑料，吸管斜插杯盖。
 */

// ---- palette ----
const CUP_PAPER = 0xe8ddd0 // 纸杯本色（牛皮纸淡色）
const CUP_BAND = 0x21a666 // 品牌绿环带
const LID_TINT = 0xd9e8f0 // 透明杯盖
const STRAW_RED = 0xd64545 // 吸管红
const LIQUID_BROWN = 0xb07a3c // 杯内奶茶

export interface PaperCupOptions {
  /** 杯身主色（默认牛皮纸色） */
  cupColor?: number
  /** 整体缩放（默认 1） */
  scale?: number
}

/** 杯身轮廓（lathe 截面：底部小圆角 → 上宽微锥） */
function cupProfile(): THREE.Vector2[] {
  return [
    new THREE.Vector2(0.001, 0.0),
    new THREE.Vector2(0.24, 0.0),
    new THREE.Vector2(0.29, 0.02), // 底卷边
    new THREE.Vector2(0.345, 0.12), // 微锥：下窄上宽
    new THREE.Vector2(0.375, 0.4),
    new THREE.Vector2(0.385, 0.62),
    new THREE.Vector2(0.39, 0.86),
    new THREE.Vector2(0.392, 1.0), // 杯口卷边
  ];
}

export function createPaperCupModel(options: PaperCupOptions = {}): THREE.Group {
  const root = new THREE.Group();
  root.name = 'paper-cup';
  const body = new THREE.Group();
  body.name = 'body';
  root.add(body);

  const cupColor = options.cupColor ?? CUP_PAPER;

  // ---- 杯身（纸质感：粗糙 + 略吸收光） ----
  const shell = new THREE.Mesh(
    new THREE.LatheGeometry(cupProfile(), 56),
    new THREE.MeshStandardMaterial({
      color: cupColor,
      roughness: 0.9,
      metalness: 0.0,
    }),
  );
  shell.name = 'cup-shell';
  body.add(shell);

  // ---- 品牌环带 + 白色饰条 ----
  const band = new THREE.Mesh(
    new THREE.CylinderGeometry(0.386, 0.37, 0.22, 56, 1, true),
    new THREE.MeshStandardMaterial({
      color: CUP_BAND,
      roughness: 0.7,
      side: THREE.DoubleSide,
    }),
  );
  band.name = 'brand-band';
  band.position.y = 0.62;
  body.add(band);

  const stripe = new THREE.Mesh(
    new THREE.CylinderGeometry(0.389, 0.372, 0.03, 56, 1, true),
    new THREE.MeshStandardMaterial({
      color: 0xffffff,
      roughness: 0.75,
      side: THREE.DoubleSide,
    }),
  );
  stripe.name = 'band-stripe';
  stripe.position.y = 0.74;
  body.add(stripe);

  // ---- 杯内液体（半透明深色，杯口下沿） ----
  const liquid = new THREE.Mesh(
    new THREE.CylinderGeometry(0.36, 0.33, 0.1, 48),
    new THREE.MeshPhysicalMaterial({
      color: LIQUID_BROWN,
      transparent: true,
      opacity: 0.6,
      roughness: 0.15,
    }),
  );
  liquid.name = 'liquid';
  liquid.position.y = 0.88;
  body.add(liquid);

  // ---- 杯盖（透明圆盖 + 饮口） ----
  const lidMat = new THREE.MeshPhysicalMaterial({
    color: LID_TINT,
    transparent: true,
    opacity: 0.75,
    roughness: 0.15,
    clearcoat: 0.8,
    side: THREE.DoubleSide,
  });
  const lid = new THREE.Mesh(new THREE.CylinderGeometry(0.398, 0.398, 0.05, 56), lidMat);
  lid.name = 'lid';
  lid.position.y = 1.03;
  body.add(lid);

  // 饮口凸台
  const lidSip = new THREE.Mesh(
    new THREE.CylinderGeometry(0.07, 0.08, 0.03, 24),
    new THREE.MeshPhysicalMaterial({
      color: 0xffffff,
      roughness: 0.25,
    }),
  );
  lidSip.name = 'lid-sip';
  lidSip.position.set(0.05, 1.065, 0.16);
  body.add(lidSip);

  // ---- 吸管（tube：从饮口斜插杯内，杯口上方弯折） ----
  const strawCurve = new THREE.CatmullRomCurve3([
    new THREE.Vector3(0.05, 0.9, 0.16), // 杯内下端
    new THREE.Vector3(0.05, 1.1, 0.16), // 穿出饮口
    new THREE.Vector3(0.09, 1.34, 0.18), // 斜上方
    new THREE.Vector3(0.2, 1.48, 0.15), // 折弯
  ]);
  const straw = new THREE.Mesh(
    new THREE.TubeGeometry(strawCurve, 16, 0.022, 12),
    new THREE.MeshStandardMaterial({
      color: STRAW_RED,
      roughness: 0.45,
    }),
  );
  straw.name = 'straw';
  body.add(straw);

  if (options.scale) root.scale.setScalar(options.scale);

  // ---- 待机动画：轻浮浮沉 + 吸管微颤 ----
  modelData(root).tick = (t) => {
    body.position.y = Math.sin(t * 1.2) * 0.028;
    body.rotation.z = Math.sin(t * 0.9) * 0.03;
  };
  modelData(root).label = '纸杯';
  modelData(root).palette = { CUP_PAPER: cupColor, CUP_BAND, LID_TINT, STRAW_RED };

  return root;
}
