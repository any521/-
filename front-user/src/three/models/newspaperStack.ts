import * as THREE from 'three'
import { RoundedBoxGeometry } from 'three/examples/jsm/geometries/RoundedBoxGeometry.js'
import { modelData } from '../types'

/**
 * 程序化废纸堆(报纸/书本叠),img2threejs 风格重建。
 * 多层错位纸张 + 顶层"头条"排版(报头色带 + 文字行 + 配图块),顶层纸微翘角。
 */

// ---- palette ----
const PAPER = 0xf2ede1; // 旧报纸
const PAPER_EDGE = 0xd9d2c0; // 纸边
const INK_DARK = 0x3a3f45; // 油墨
const INK_LIGHT = 0x8a8f96;
const MASTHEAD_GREEN = 0x21a666; // 报头绿

export interface NewspaperStackOptions {
  scale?: number;
}

export function createNewspaperStackModel(options: NewspaperStackOptions = {}): THREE.Group {
  const root = new THREE.Group();
  root.name = 'newspaper-stack';
  const body = new THREE.Group();
  body.name = 'body';
  root.add(body);

  const W = 1.05;
  const D = 0.78;
  const LAYER = 0.045;

  const paperMat = new THREE.MeshStandardMaterial({ color: PAPER, roughness: 0.92 });
  const edgeMat = new THREE.MeshStandardMaterial({ color: PAPER_EDGE, roughness: 0.95 });

  // ---- 6 层报纸,逐层轻微旋转/错位 ----
  const offsets: Array<[number, number, number]> = [
    [0, 0, 0],
    [0.03, 0.06, -0.02],
    [-0.025, -0.05, 0.03],
    [0.02, -0.03, -0.04],
    [-0.035, 0.04, 0.02],
    [0.01, 0.02, 0.05],
  ];
  offsets.forEach(([dx, rot, dz], i) => {
    const sheet = new THREE.Mesh(
      new RoundedBoxGeometry(W, LAYER, D, 2, 0.012),
      i % 2 === 0 ? paperMat : edgeMat,
    );
    sheet.position.set(dx, LAYER / 2 + i * (LAYER + 0.004), dz);
    sheet.rotation.y = rot;
    sheet.name = `sheet-${i}`;
    body.add(sheet);
  });

  const topY = LAYER + 5 * (LAYER + 0.004) + LAYER / 2 + 0.002;

  // ---- 顶层排版:报头 + 标题行 + 配图 + 文字栏 ----
  const print = new THREE.Group();
  print.name = 'print-layout';
  print.position.set(0.01, topY, 0.05);
  print.rotation.y = 0.02;
  body.add(print);

  const masthead = new THREE.Mesh(
    new THREE.BoxGeometry(W * 0.7, 0.004, 0.1),
    new THREE.MeshStandardMaterial({ color: MASTHEAD_GREEN, roughness: 0.7 }),
  );
  masthead.position.set(-W * 0.1, 0, -D * 0.32);
  print.add(masthead);

  const headline = new THREE.Mesh(
    new THREE.BoxGeometry(W * 0.55, 0.004, 0.045),
    new THREE.MeshStandardMaterial({ color: INK_DARK, roughness: 0.8 }),
  );
  headline.position.set(-W * 0.12, 0, -D * 0.16);
  print.add(headline);

  const photo = new THREE.Mesh(
    new THREE.BoxGeometry(W * 0.3, 0.004, D * 0.3),
    new THREE.MeshStandardMaterial({ color: INK_LIGHT, roughness: 0.85 }),
  );
  photo.position.set(W * 0.26, 0, D * 0.1);
  print.add(photo);

  const lineMat = new THREE.MeshStandardMaterial({ color: INK_LIGHT, roughness: 0.85 });
  // 左栏文字
  for (let row = 0; row < 4; row++) {
    const line = new THREE.Mesh(new THREE.BoxGeometry(W * 0.42, 0.003, 0.02), lineMat);
    line.position.set(-W * 0.2, 0, -D * 0.02 + row * 0.08);
    print.add(line);
  }
  // 右下栏文字(配图之下)
  for (let row = 0; row < 3; row++) {
    const line = new THREE.Mesh(new THREE.BoxGeometry(W * 0.2, 0.003, 0.02), lineMat);
    line.position.set(W * 0.26, 0, D * 0.3 + row * 0.06);
    print.add(line);
  }

  // ---- 顶层纸翘角 ----
  const curl = new THREE.Mesh(
    new THREE.CylinderGeometry(0.09, 0.09, W * 0.3, 12, 1, true, -Math.PI * 0.5, Math.PI * 0.42),
    paperMat,
  );
  curl.name = 'curl-corner';
  curl.rotation.z = Math.PI / 2;
  curl.rotation.y = -0.35;
  curl.position.set(W * 0.34, topY + 0.02, D * 0.3);
  body.add(curl);

  if (options.scale) root.scale.setScalar(options.scale);

  // ---- 待机动画:整体慢摇 + 翘角轻颤 ----
  modelData(root).tick = (t) => {
    body.rotation.y = Math.sin(t * 0.4) * 0.12;
    body.position.y = Math.sin(t * 1.1) * 0.012;
    curl.rotation.x = Math.sin(t * 2.4) * 0.06;
  };
  modelData(root).label = '废纸堆';
  modelData(root).palette = { PAPER, PAPER_EDGE, MASTHEAD_GREEN };

  return root;
}
