import * as THREE from 'three'
import { modelData } from '../types'

/**
 * 程序化塑料瓶(PET)/ 玻璃瓶,img2threejs 风格重建。
 * 轮廓来自经典 500ml PET 瓶:花瓣底内收 → 直筒瓶身 → 弧形瓶肩 → 防盗环瓶颈。
 * variant 'glass' 切换为绿色玻璃瓶(不去瓶盖贴标,改磨砂绿)。
 */

// ---- palette(对齐 tokens.scss) ----
const PET_TINT = 0xe6f4ec; // PET 微绿透明
const GLASS_GREEN = 0x3f7d54; // 玻璃绿
const LABEL_GREEN = 0x21a666; // 品牌绿
const LABEL_STRIPE = 0xffffff;
const CAP_GREEN = 0x178a53; // 品牌深绿
const WATER_BLUE = 0x9fd8f0;

export interface PlasticBottleOptions {
  /** 'pet' 透明塑料瓶(默认)| 'glass' 绿色玻璃瓶 */
  variant?: 'pet' | 'glass';
  /** 整体缩放(默认 1) */
  scale?: number;
}

/** 瓶体轮廓(lathe 截面,x=半径,y=高度) */
function bottleProfile(): THREE.Vector2[] {
  return [
    new THREE.Vector2(0.001, 0.0),
    new THREE.Vector2(0.20, 0.0),
    new THREE.Vector2(0.27, 0.02),
    new THREE.Vector2(0.295, 0.07),
    new THREE.Vector2(0.24, 0.13), // 花瓣底内收
    new THREE.Vector2(0.27, 0.18),
    new THREE.Vector2(0.305, 0.3), // 瓶身直筒
    new THREE.Vector2(0.305, 0.68),
    new THREE.Vector2(0.29, 0.78),
    new THREE.Vector2(0.235, 0.9), // 瓶肩
    new THREE.Vector2(0.15, 1.0),
    new THREE.Vector2(0.095, 1.06),
    new THREE.Vector2(0.088, 1.1), // 防盗环
    new THREE.Vector2(0.088, 1.16), // 瓶口
  ];
}

function makeBodyMaterial(variant: 'pet' | 'glass'): THREE.MeshPhysicalMaterial {
  if (variant === 'glass') {
    return new THREE.MeshPhysicalMaterial({
      color: GLASS_GREEN,
      transparent: true,
      opacity: 0.82,
      roughness: 0.12,
      metalness: 0.0,
      clearcoat: 1.0,
      clearcoatRoughness: 0.06,
      envMapIntensity: 1.1,
      side: THREE.DoubleSide,
    });
  }
  return new THREE.MeshPhysicalMaterial({
    color: PET_TINT,
    transparent: true,
    opacity: 0.45,
    roughness: 0.06,
    metalness: 0.0,
    clearcoat: 1.0,
    clearcoatRoughness: 0.05,
    envMapIntensity: 0.9,
    side: THREE.DoubleSide,
  });
}

export function createPlasticBottleModel(options: PlasticBottleOptions = {}): THREE.Group {
  const variant = options.variant ?? 'pet';
  const root = new THREE.Group();
  root.name = variant === 'glass' ? 'glass-bottle' : 'plastic-bottle';
  const body = new THREE.Group();
  body.name = 'body';
  root.add(body);

  // ---- 瓶体(lathe) ----
  const bottleGeo = new THREE.LatheGeometry(bottleProfile(), 56);
  const bottle = new THREE.Mesh(bottleGeo, makeBodyMaterial(variant));
  bottle.name = 'bottle-shell';
  body.add(bottle);

  if (variant === 'pet') {
    // ---- 瓶内液体(八分满) ----
    const waterPts = bottleProfile()
      .filter((p) => p.y <= 0.72)
      .map((p) => new THREE.Vector2(Math.max(p.x - 0.02, 0.001), p.y));
    const water = new THREE.Mesh(
      new THREE.LatheGeometry(waterPts, 40),
      new THREE.MeshPhysicalMaterial({
        color: WATER_BLUE,
        transparent: true,
        opacity: 0.5,
        roughness: 0.15,
      }),
    );
    water.name = 'water-fill';
    body.add(water);

    // ---- 贴标(品牌绿环 + 白色饰带) ----
    const label = new THREE.Mesh(
      new THREE.CylinderGeometry(0.315, 0.315, 0.3, 56, 1, true),
      new THREE.MeshStandardMaterial({
        color: LABEL_GREEN,
        roughness: 0.5,
        side: THREE.DoubleSide,
      }),
    );
    label.name = 'label';
    label.position.y = 0.5;
    body.add(label);

    const stripe = new THREE.Mesh(
      new THREE.CylinderGeometry(0.317, 0.317, 0.05, 56, 1, true),
      new THREE.MeshStandardMaterial({
        color: LABEL_STRIPE,
        roughness: 0.6,
        side: THREE.DoubleSide,
      }),
    );
    stripe.name = 'label-stripe';
    stripe.position.y = 0.5;
    body.add(stripe);
  }

  // ---- 瓶盖(带防滑纹视觉的圆柱 + 底部防盗圈) ----
  const capMat = new THREE.MeshStandardMaterial({
    color: variant === 'glass' ? 0xd9a534 : CAP_GREEN,
    roughness: 0.35,
    metalness: 0.1,
  });
  const cap = new THREE.Mesh(new THREE.CylinderGeometry(0.096, 0.096, 0.085, 32), capMat);
  cap.name = 'cap';
  cap.position.y = 1.2;
  body.add(cap);

  const capRing = new THREE.Mesh(new THREE.TorusGeometry(0.093, 0.012, 12, 40), capMat);
  capRing.name = 'cap-ring';
  capRing.rotation.x = Math.PI / 2;
  capRing.position.y = 1.16;
  body.add(capRing);

  if (options.scale) root.scale.setScalar(options.scale);

  // ---- 待机动画:轻浮浮沉 + 微微摇摆 ----
  modelData(root).tick = (t) => {
    body.position.y = Math.sin(t * 1.15) * 0.035;
    body.rotation.z = Math.sin(t * 0.85) * 0.035;
    body.rotation.x = Math.cos(t * 0.7) * 0.02;
  };
  modelData(root).label = variant === 'glass' ? '玻璃瓶' : '塑料瓶';
  modelData(root).palette = { PET_TINT, GLASS_GREEN, LABEL_GREEN, CAP_GREEN };

  return root;
}
