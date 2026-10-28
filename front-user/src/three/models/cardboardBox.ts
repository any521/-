import * as THREE from 'three'
import { RoundedBoxGeometry } from 'three/examples/jsm/geometries/RoundedBoxGeometry.js'
import { modelData } from '../types'

/**
 * 程序化瓦楞纸箱,img2threejs 风格重建。
 * 牛皮纸箱体 + 半开四片箱盖 + 封口胶带 + 侧面回收标识色块。
 */

// ---- palette ----
const KRAFT = 0xc9a26b; // 牛皮纸
const KRAFT_DARK = 0xa9854f; // 凹槽/内侧
const TAPE = 0xe8dcc0; // 牛皮纸胶带
const PRINT_GREEN = 0x21a666; // 印刷·品牌绿

export interface CardboardBoxOptions {
  scale?: number;
}

export function createCardboardBoxModel(options: CardboardBoxOptions = {}): THREE.Group {
  const root = new THREE.Group();
  root.name = 'cardboard-box';
  const body = new THREE.Group();
  body.name = 'body';
  root.add(body);

  const W = 1.1;
  const H = 0.72;
  const D = 0.8;

  const kraftMat = new THREE.MeshStandardMaterial({ color: KRAFT, roughness: 0.9 });
  const kraftDarkMat = new THREE.MeshStandardMaterial({ color: KRAFT_DARK, roughness: 0.95 });

  // ---- 箱体 ----
  const box = new THREE.Mesh(new RoundedBoxGeometry(W, H, D, 3, 0.02), kraftMat);
  box.name = 'box-shell';
  box.position.y = H / 2;
  body.add(box);

  // ---- 箱口内衬(深色,营造"打开"感) ----
  const inner = new THREE.Mesh(
    new THREE.BoxGeometry(W - 0.06, 0.03, D - 0.06),
    kraftDarkMat,
  );
  inner.name = 'box-inner';
  inner.position.y = H - 0.02;
  body.add(inner);

  // ---- 四片箱盖(前后外开、左右内扣) ----
  const flapMat = kraftMat;
  const mkFlap = (w: number, d: number) =>
    new THREE.Mesh(new THREE.BoxGeometry(w, 0.025, d), flapMat);

  const flapFront = mkFlap(W, D / 2);
  flapFront.geometry.translate(0, 0, -D / 4 + 0.02);
  flapFront.position.set(0, H + 0.01, D / 2 - 0.02);
  flapFront.rotation.x = -Math.PI * 0.32;
  body.add(flapFront);

  const flapBack = mkFlap(W, D / 2);
  flapBack.geometry.translate(0, 0, D / 4 - 0.02);
  flapBack.position.set(0, H + 0.01, -D / 2 + 0.02);
  flapBack.rotation.x = Math.PI * 0.32;
  body.add(flapBack);

  const flapLeft = mkFlap(D, W / 2);
  flapLeft.geometry.rotateY(Math.PI / 2);
  flapLeft.geometry.translate(-W / 4 + 0.02, 0, 0);
  flapLeft.position.set(-W / 2 + 0.02, H + 0.005, 0);
  flapLeft.rotation.z = Math.PI * 0.18;
  body.add(flapLeft);

  const flapRight = mkFlap(D, W / 2);
  flapRight.geometry.rotateY(Math.PI / 2);
  flapRight.geometry.translate(W / 4 - 0.02, 0, 0);
  flapRight.position.set(W / 2 - 0.02, H + 0.005, 0);
  flapRight.rotation.z = -Math.PI * 0.18;
  body.add(flapRight);

  // ---- 封口胶带(十字) ----
  const tapeMat = new THREE.MeshStandardMaterial({
    color: TAPE,
    roughness: 0.55,
    transparent: true,
    opacity: 0.9,
  });
  const tapeLong = new THREE.Mesh(new THREE.BoxGeometry(0.14, H + 0.02, D + 0.02), tapeMat);
  tapeLong.name = 'tape-long';
  tapeLong.position.y = H / 2;
  body.add(tapeLong);

  // ---- 侧面印刷:回收绿方块 + 文字条 ----
  const printMat = new THREE.MeshStandardMaterial({ color: PRINT_GREEN, roughness: 0.7 });
  const badge = new THREE.Mesh(new THREE.BoxGeometry(0.02, 0.2, 0.2), printMat);
  badge.name = 'print-badge';
  badge.position.set(W / 2 + 0.005, H * 0.55, -D * 0.18);
  body.add(badge);

  const lineMat = new THREE.MeshStandardMaterial({ color: KRAFT_DARK, roughness: 0.8 });
  for (let i = 0; i < 3; i++) {
    const line = new THREE.Mesh(new THREE.BoxGeometry(0.02, 0.03, 0.3), lineMat);
    line.position.set(W / 2 + 0.005, H * 0.62 - i * 0.08, D * 0.16);
    body.add(line);
  }

  if (options.scale) root.scale.setScalar(options.scale);

  // ---- 待机动画:轻微晃动 + 左箱盖微颤 ----
  modelData(root).tick = (t) => {
    body.rotation.z = Math.sin(t * 0.8) * 0.03;
    body.position.y = Math.abs(Math.sin(t * 1.6)) * 0.015;
    flapLeft.rotation.z = Math.PI * 0.18 + Math.sin(t * 2.2) * 0.04;
    flapRight.rotation.z = -Math.PI * 0.18 - Math.cos(t * 2.0) * 0.04;
  };
  modelData(root).label = '废纸箱';
  modelData(root).palette = { KRAFT, KRAFT_DARK, TAPE, PRINT_GREEN };

  return root;
}
