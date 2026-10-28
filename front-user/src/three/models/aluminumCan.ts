import * as THREE from 'three'
import { modelData } from '../types'

/**
 * 程序化易拉罐,img2threejs 风格重建。
 * 330ml 标准罐:直筒罐身 + 上下缩颈 + 卷边 + 罐盖/拉环,金属拉丝质感 + 品牌绿环带。
 */

// ---- palette ----
const ALUMINUM = 0xd8dde3; // 铝银
const ALUMINUM_DARK = 0x9aa3ad; // 罐盖
const BAND_GREEN = 0x21a666; // 品牌绿环带
const TAB_STEEL = 0xb9c0c9;

export interface AluminumCanOptions {
  scale?: number;
}

export function createAluminumCanModel(options: AluminumCanOptions = {}): THREE.Group {
  const root = new THREE.Group();
  root.name = 'aluminum-can';
  const body = new THREE.Group();
  body.name = 'body';
  root.add(body);

  const R = 0.33; // 罐身半径
  const H = 1.15; // 总高

  const aluMat = new THREE.MeshStandardMaterial({
    color: ALUMINUM,
    metalness: 0.92,
    roughness: 0.32,
    envMapIntensity: 1.2,
  });
  const aluDarkMat = new THREE.MeshStandardMaterial({
    color: ALUMINUM_DARK,
    metalness: 0.9,
    roughness: 0.42,
  });

  // ---- 罐身(lathe:底卷边 → 缩颈 → 直筒 → 上缩颈 → 顶卷边) ----
  const pts = [
    new THREE.Vector2(0.001, 0.0),
    new THREE.Vector2(R * 0.55, 0.0),
    new THREE.Vector2(R * 0.9, 0.015),
    new THREE.Vector2(R, 0.05), // 底卷边
    new THREE.Vector2(R, H * 0.72), // 直筒
    new THREE.Vector2(R * 0.94, H * 0.8),
    new THREE.Vector2(R * 0.82, H * 0.9), // 上缩颈
    new THREE.Vector2(R * 0.8, H * 0.96),
    new THREE.Vector2(R * 0.84, H), // 顶卷边
  ];
  const shell = new THREE.Mesh(new THREE.LatheGeometry(pts, 56), aluMat);
  shell.name = 'can-shell';
  body.add(shell);

  // ---- 罐盖(内凹圆盘) ----
  const lid = new THREE.Mesh(new THREE.CylinderGeometry(R * 0.8, R * 0.8, 0.02, 48), aluDarkMat);
  lid.name = 'can-lid';
  lid.position.y = H - 0.008;
  body.add(lid);

  // 刻痕开口(椭圆浅槽)
  const score = new THREE.Mesh(
    new THREE.TorusGeometry(R * 0.28, 0.008, 8, 40),
    new THREE.MeshStandardMaterial({ color: 0x6f7883, metalness: 0.85, roughness: 0.5 }),
  );
  score.name = 'lid-score';
  score.rotation.x = Math.PI / 2;
  score.scale.set(1.6, 1, 1);
  score.position.set(0, H + 0.002, R * 0.12);
  body.add(score);

  // ---- 拉环 ----
  const tabGroup = new THREE.Group();
  tabGroup.name = 'pull-tab';
  const tab = new THREE.Mesh(
    new THREE.TorusGeometry(0.055, 0.016, 10, 28),
    new THREE.MeshStandardMaterial({ color: TAB_STEEL, metalness: 0.95, roughness: 0.25 }),
  );
  tab.rotation.x = Math.PI / 2;
  tabGroup.add(tab);
  const tabTongue = new THREE.Mesh(
    new THREE.BoxGeometry(0.05, 0.014, 0.14),
    tab.material,
  );
  tabTongue.position.z = 0.09;
  tabGroup.add(tabTongue);
  tabGroup.position.set(0, H + 0.012, -R * 0.15);
  body.add(tabGroup);

  // 铆钉
  const rivet = new THREE.Mesh(new THREE.SphereGeometry(0.022, 16, 12), tab.material);
  rivet.position.set(0, H + 0.008, -R * 0.15);
  body.add(rivet);

  // ---- 品牌绿环带 ----
  const band = new THREE.Mesh(
    new THREE.CylinderGeometry(R + 0.004, R + 0.004, H * 0.3, 56, 1, true),
    new THREE.MeshStandardMaterial({
      color: BAND_GREEN,
      metalness: 0.4,
      roughness: 0.45,
      side: THREE.DoubleSide,
    }),
  );
  band.name = 'brand-band';
  band.position.y = H * 0.42;
  body.add(band);

  const bandStripe = new THREE.Mesh(
    new THREE.CylinderGeometry(R + 0.006, R + 0.006, 0.035, 56, 1, true),
    new THREE.MeshStandardMaterial({ color: 0xffffff, metalness: 0.2, roughness: 0.5, side: THREE.DoubleSide }),
  );
  bandStripe.position.y = H * 0.42;
  body.add(bandStripe);

  if (options.scale) root.scale.setScalar(options.scale);

  // ---- 待机动画:罐身轻摇 + 拉环微颤 ----
  modelData(root).tick = (t) => {
    body.rotation.z = Math.sin(t * 1.05) * 0.045;
    body.position.y = Math.sin(t * 1.3) * 0.02;
    tabGroup.rotation.y = Math.sin(t * 2.6) * 0.25;
  };
  modelData(root).label = '易拉罐';
  modelData(root).palette = { ALUMINUM, ALUMINUM_DARK, BAND_GREEN };

  return root;
}
