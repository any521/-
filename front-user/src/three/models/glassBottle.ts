import * as THREE from 'three'
import { modelData } from '../types'

/**
 * 程序化玻璃瓶（img2threejs 风格，Track G T5 独立预设）。
 * 经典圆肩瓶：花瓣底 → 圆瓶肩 → 长瓶颈 → 瓶口环；通透玻璃材质 + 瓶内液体，
 * 与 plasticBottle 的 glass 变体解耦——瓶肩曲线、通透感、瓶盖形态独立表达。
 */

// ---- palette ----
const GLASS_TINT = 0xdcefe4 // 淡绿通透玻璃
const LIQUID_BROWN = 0xb07a3c // 瓶内液体（酱油/饮料）
const CAP_GOLD = 0xd9a534 // 金属旋盖
const LABEL_CREAM = 0xf2e6cd // 瓶身标签

export interface GlassBottleOptions {
  /** 玻璃主色（默认淡绿通透） */
  tint?: number
  /** 整体缩放（默认 1） */
  scale?: number
}

/** 圆肩瓶轮廓（lathe 截面，x=半径 y=高度） */
function bottleProfile(): THREE.Vector2[] {
  return [
    new THREE.Vector2(0.001, 0.0),
    new THREE.Vector2(0.19, 0.0),
    new THREE.Vector2(0.26, 0.02), // 底卷边
    new THREE.Vector2(0.29, 0.1),
    new THREE.Vector2(0.30, 0.3), // 瓶身直筒
    new THREE.Vector2(0.30, 0.55),
    new THREE.Vector2(0.26, 0.72), // 瓶肩弧线（圆肩，非斜肩）
    new THREE.Vector2(0.14, 0.9),
    new THREE.Vector2(0.09, 0.96),
    new THREE.Vector2(0.075, 1.06), // 瓶颈
    new THREE.Vector2(0.088, 1.12), // 瓶口环
    new THREE.Vector2(0.088, 1.16),
  ];
}

export function createGlassBottleModel(options: GlassBottleOptions = {}): THREE.Group {
  const root = new THREE.Group();
  root.name = 'glass-bottle';
  const body = new THREE.Group();
  body.name = 'body';
  root.add(body);

  const tint = options.tint ?? GLASS_TINT;

  // ---- 瓶体（通透玻璃：浅色低饱和 + 高清漆，禁止深色实心） ----
  const glassMat = new THREE.MeshPhysicalMaterial({
    color: tint,
    transparent: true,
    opacity: 0.78,
    roughness: 0.1,
    metalness: 0.0,
    clearcoat: 1.0,
    clearcoatRoughness: 0.05,
    envMapIntensity: 1.2,
    side: THREE.DoubleSide,
  });
  const shell = new THREE.Mesh(new THREE.LatheGeometry(bottleProfile(), 56), glassMat);
  shell.name = 'bottle-shell';
  body.add(shell);

  // ---- 瓶内液体（半透明深色内嵌，八分满） ----
  const liquidPts = bottleProfile()
    .filter((p) => p.y <= 0.9)
    .map((p) => new THREE.Vector2(Math.max(p.x - 0.03, 0.001), p.y));
  const liquid = new THREE.Mesh(
    new THREE.LatheGeometry(liquidPts, 40),
    new THREE.MeshPhysicalMaterial({
      color: LIQUID_BROWN,
      transparent: true,
      opacity: 0.55,
      roughness: 0.2,
      metalness: 0.0,
    }),
  );
  liquid.name = 'liquid-fill';
  body.add(liquid);

  // ---- 瓶身标签（米白贴标环 + 主色块） ----
  const label = new THREE.Mesh(
    new THREE.CylinderGeometry(0.305, 0.305, 0.26, 56, 1, true),
    new THREE.MeshStandardMaterial({
      color: LABEL_CREAM,
      roughness: 0.55,
      side: THREE.DoubleSide,
    }),
  );
  label.name = 'label';
  label.position.y = 0.46;
  body.add(label);

  const labelBand = new THREE.Mesh(
    new THREE.CylinderGeometry(0.307, 0.307, 0.04, 56, 1, true),
    new THREE.MeshStandardMaterial({
      color: CAP_GOLD,
      roughness: 0.4,
      metalness: 0.3,
      side: THREE.DoubleSide,
    }),
  );
  labelBand.name = 'label-band';
  labelBand.position.y = 0.34;
  body.add(labelBand);

  // ---- 金属旋盖 + 防盗环 ----
  const capMat = new THREE.MeshStandardMaterial({
    color: CAP_GOLD,
    roughness: 0.3,
    metalness: 0.5,
  });
  const cap = new THREE.Mesh(new THREE.CylinderGeometry(0.1, 0.1, 0.08, 32), capMat);
  cap.name = 'cap';
  cap.position.y = 1.2;
  body.add(cap);

  const capRing = new THREE.Mesh(new THREE.TorusGeometry(0.096, 0.011, 12, 40), capMat);
  capRing.name = 'cap-ring';
  capRing.rotation.x = Math.PI / 2;
  capRing.position.y = 1.16;
  body.add(capRing);

  if (options.scale) root.scale.setScalar(options.scale);

  // ---- 待机动画：轻浮浮沉 + 微微摇摆 ----
  modelData(root).tick = (t) => {
    body.position.y = Math.sin(t * 1.1) * 0.03;
    body.rotation.z = Math.sin(t * 0.8) * 0.03;
    body.rotation.x = Math.cos(t * 0.65) * 0.018;
  };
  modelData(root).label = '玻璃瓶';
  modelData(root).palette = { GLASS_TINT: tint, LIQUID_BROWN, CAP_GOLD, LABEL_CREAM };

  return root;
}
