import * as THREE from 'three'
import { modelData } from '../types'

/**
 * 程序化厨余垃圾 stylized 低多边形（img2threejs 风格，Track G T5 预设）。
 * 有机不规则体不追求几何精确：香蕉皮/菜叶/果皮用低多边形近似 + 表面色块，追求颜色与形态神似。
 * 注意：img2threejs skill 对有机体弱，本模型为 stylized 近似，不追求写实（README 已标注）。
 */

// ---- palette ----
const PEEL_YELLOW = 0xe8c44a // 香蕉皮黄
const PEEL_DARK = 0xd9a52e // 皮内暗黄
const LEAF_GREEN = 0x5da23f // 菜叶绿
const ORANGE_RIND = 0xe08a3c // 果皮橙

export interface KitchenWasteOptions {
  /** 整体缩放（默认 1） */
  scale?: number
}

export function createKitchenWasteModel(options: KitchenWasteOptions = {}): THREE.Group {
  const root = new THREE.Group();
  root.name = 'kitchen-waste';
  const body = new THREE.Group();
  body.name = 'body';
  root.add(body);

  const peelMat = new THREE.MeshStandardMaterial({ color: PEEL_YELLOW, roughness: 0.85 });
  const peelInnerMat = new THREE.MeshStandardMaterial({ color: PEEL_DARK, roughness: 0.8 });
  const leafMat = new THREE.MeshStandardMaterial({
    color: LEAF_GREEN,
    roughness: 0.9,
    side: THREE.DoubleSide,
  });

  // ---- 香蕉皮 ×2（半环长条，低分段 → 折痕感，交叉叠放） ----
  const peel1 = new THREE.Mesh(new THREE.TorusGeometry(0.4, 0.085, 5, 8, Math.PI * 0.9), peelMat);
  peel1.name = 'peel-1';
  peel1.rotation.x = Math.PI / 2; // 环面摊平
  peel1.position.set(-0.26, 0.05, 0.02);
  body.add(peel1);

  const peel2 = new THREE.Mesh(new THREE.TorusGeometry(0.36, 0.075, 5, 8, Math.PI * 0.8), peelMat);
  peel2.name = 'peel-2';
  peel2.rotation.x = Math.PI / 2;
  peel2.rotation.z = 0.5;
  peel2.position.set(-0.12, 0.02, 0.18);
  body.add(peel2);

  // 皮内暗黄衬里（小半环贴在内侧下方）
  const peelIn = new THREE.Mesh(new THREE.TorusGeometry(0.34, 0.05, 4, 6, Math.PI * 0.6), peelInnerMat);
  peelIn.name = 'peel-inner';
  peelIn.rotation.x = Math.PI / 2;
  peelIn.rotation.z = 0.25;
  peelIn.position.set(-0.18, -0.005, 0.1);
  body.add(peelIn);

  // ---- 菜叶（低分段平面沿长度起伏 + 边缘上卷，有机感） ----
  const leafGeo = new THREE.PlaneGeometry(0.95, 0.52, 6, 4);
  const pos = leafGeo.attributes.position as THREE.BufferAttribute;
  for (let i = 0; i < pos.count; i++) {
    const x = pos.getX(i);
    const y = pos.getY(i);
    pos.setZ(i, Math.sin(x * 2.4) * 0.11 + Math.abs(y) * 0.05); // 中脊起伏 + 边缘上卷
  }
  leafGeo.computeVertexNormals();
  const leaf = new THREE.Mesh(leafGeo, leafMat);
  leaf.name = 'leaf';
  leaf.rotation.x = -Math.PI / 2 + 0.18;
  leaf.rotation.z = -0.5;
  leaf.position.set(0.3, 0.1, -0.15);
  body.add(leaf);

  // ---- 果皮（低多边形球压扁 + 内切） ----
  const rind = new THREE.Mesh(new THREE.SphereGeometry(0.22, 6, 4), peelMat);
  rind.name = 'rind';
  rind.scale.set(1.15, 0.45, 0.9);
  rind.rotation.z = 0.6;
  rind.position.set(0.05, 0.03, -0.28);
  body.add(rind);

  const rindCut = new THREE.Mesh(new THREE.SphereGeometry(0.16, 6, 4), peelInnerMat);
  rindCut.name = 'rind-cut';
  rindCut.scale.set(1.1, 0.35, 0.85);
  rindCut.rotation.z = 0.6;
  rindCut.position.set(0.1, 0.045, -0.3);
  body.add(rindCut);

  // 橘子果肉粒（低多边形小球散落）
  const pulpMat = new THREE.MeshStandardMaterial({ color: ORANGE_RIND, roughness: 0.9 });
  for (const [px, pz] of [[0.22, -0.32], [0.14, -0.18], [-0.05, -0.35]] as const) {
    const pulp = new THREE.Mesh(new THREE.SphereGeometry(0.045, 5, 4), pulpMat);
    pulp.name = 'pulp';
    pulp.position.set(px, 0.02, pz);
    body.add(pulp);
  }

  if (options.scale) root.scale.setScalar(options.scale);

  // ---- 待机动画：整体缓慢浮沉 + 轻摆（有机体的随意感） ----
  modelData(root).tick = (t) => {
    body.position.y = Math.sin(t * 0.9) * 0.022;
    body.rotation.z = Math.sin(t * 0.6) * 0.04;
  };
  modelData(root).label = '厨余垃圾';
  modelData(root).palette = { PEEL_YELLOW, PEEL_DARK, LEAF_GREEN, ORANGE_RIND };

  return root;
}
