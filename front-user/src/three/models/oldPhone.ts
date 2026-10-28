import * as THREE from 'three'
import { RoundedBoxGeometry } from 'three/examples/jsm/geometries/RoundedBoxGeometry.js'
import { modelData } from '../types'

/**
 * 程序化旧智能手机,img2threejs 风格重建。
 * 圆角机身 + 微裂纹亮面屏幕(程序化刻痕)+ 听筒/前摄/Home 键 + 侧键。
 * 屏幕带品牌绿待机辉光,呼吸闪烁。
 */

// ---- palette ----
const BODY_DARK = 0x2b323b; // 深灰机身
const BODY_EDGE = 0x454e59; // 中框
const SCREEN_GLASS = 0x0d1117; // 熄屏黑
const GLOW_GREEN = 0x21a666; // 待机绿光
const CRACK_WHITE = 0xcfd6dd; // 裂纹

export interface OldPhoneOptions {
  scale?: number;
}

export function createOldPhoneModel(options: OldPhoneOptions = {}): THREE.Group {
  const root = new THREE.Group();
  root.name = 'old-phone';
  const body = new THREE.Group();
  body.name = 'body';
  root.add(body);

  const W = 0.72;
  const H = 1.48;
  const T = 0.075;

  // ---- 机身 ----
  const chassis = new THREE.Mesh(
    new RoundedBoxGeometry(W, H, T, 4, 0.06),
    new THREE.MeshStandardMaterial({ color: BODY_DARK, metalness: 0.55, roughness: 0.42 }),
  );
  chassis.name = 'chassis';
  chassis.position.y = H / 2;
  body.add(chassis);

  // 中框亮边
  const frame = new THREE.Mesh(
    new RoundedBoxGeometry(W + 0.012, H - 0.05, T - 0.02, 4, 0.05),
    new THREE.MeshStandardMaterial({ color: BODY_EDGE, metalness: 0.85, roughness: 0.3 }),
  );
  frame.name = 'mid-frame';
  frame.position.y = H / 2;
  body.add(frame);

  // ---- 屏幕(亮面玻璃 + 品牌绿待机画面) ----
  const screenMat = new THREE.MeshPhysicalMaterial({
    color: SCREEN_GLASS,
    metalness: 0.1,
    roughness: 0.08,
    clearcoat: 1.0,
    clearcoatRoughness: 0.04,
    emissive: GLOW_GREEN,
    emissiveIntensity: 0.0,
  });
  const screen = new THREE.Mesh(new RoundedBoxGeometry(W - 0.1, H - 0.34, 0.012, 3, 0.04), screenMat);
  screen.name = 'screen';
  screen.position.set(0, H / 2 + 0.04, T / 2 + 0.002);
  body.add(screen);

  // 屏幕上的"回收绿"待机卡片(发光小方块)
  const glowCard = new THREE.Mesh(
    new THREE.PlaneGeometry(W * 0.5, H * 0.22),
    new THREE.MeshStandardMaterial({
      color: GLOW_GREEN,
      emissive: GLOW_GREEN,
      emissiveIntensity: 0.9,
      transparent: true,
      opacity: 0.92,
    }),
  );
  glowCard.name = 'glow-card';
  glowCard.position.set(0, H / 2 + 0.04, T / 2 + 0.01);
  body.add(glowCard);

  // ---- 屏幕裂纹(旧机痕迹,程序化细刻痕) ----
  const crackMat = new THREE.MeshBasicMaterial({ color: CRACK_WHITE, transparent: true, opacity: 0.55 });
  const mkCrack = (len: number, angle: number, x: number, y: number) => {
    const c = new THREE.Mesh(new THREE.PlaneGeometry(len, 0.006), crackMat);
    c.rotation.z = angle;
    c.position.set(x, y, T / 2 + 0.012);
    return c;
  };
  body.add(mkCrack(0.3, 0.5, -W * 0.18, H * 0.72));
  body.add(mkCrack(0.22, -0.9, -W * 0.1, H * 0.66));
  body.add(mkCrack(0.16, 1.2, -W * 0.22, H * 0.62));

  // ---- 听筒 + 前摄 ----
  const speaker = new THREE.Mesh(
    new THREE.CapsuleGeometry(0.012, 0.07, 4, 10),
    new THREE.MeshStandardMaterial({ color: 0x11151a, roughness: 0.6 }),
  );
  speaker.rotation.z = Math.PI / 2;
  speaker.position.set(0, H - 0.09, T / 2 + 0.004);
  body.add(speaker);

  const camera = new THREE.Mesh(
    new THREE.CylinderGeometry(0.02, 0.02, 0.006, 20),
    new THREE.MeshPhysicalMaterial({ color: 0x1a2532, roughness: 0.1, clearcoat: 1 }),
  );
  camera.rotation.x = Math.PI / 2;
  camera.position.set(-W * 0.22, H - 0.09, T / 2 + 0.004);
  body.add(camera);

  // ---- Home 键 ----
  const homeBtn = new THREE.Mesh(
    new THREE.TorusGeometry(0.045, 0.008, 10, 32),
    new THREE.MeshStandardMaterial({ color: BODY_EDGE, metalness: 0.8, roughness: 0.3 }),
  );
  homeBtn.position.set(0, 0.09, T / 2 + 0.004);
  body.add(homeBtn);

  // ---- 侧键 ----
  const sideBtnMat = new THREE.MeshStandardMaterial({ color: BODY_EDGE, metalness: 0.85, roughness: 0.3 });
  const powerBtn = new THREE.Mesh(new THREE.BoxGeometry(0.012, 0.12, 0.03), sideBtnMat);
  powerBtn.position.set(W / 2 + 0.006, H * 0.62, 0);
  body.add(powerBtn);
  const volBtn = new THREE.Mesh(new THREE.BoxGeometry(0.012, 0.18, 0.03), sideBtnMat);
  volBtn.position.set(-W / 2 - 0.006, H * 0.66, 0);
  body.add(volBtn);

  if (options.scale) root.scale.setScalar(options.scale);

  // ---- 待机动画:悬浮 + 屏幕呼吸辉光 ----
  modelData(root).tick = (t) => {
    body.position.y = Math.sin(t * 1.0) * 0.04;
    body.rotation.y = Math.sin(t * 0.45) * 0.35;
    const pulse = 0.55 + Math.sin(t * 1.8) * 0.35;
    (glowCard.material as THREE.MeshStandardMaterial).emissiveIntensity = pulse;
    screenMat.emissiveIntensity = Math.max(0, pulse - 0.5) * 0.35;
  };
  modelData(root).label = '旧手机';
  modelData(root).palette = { BODY_DARK, SCREEN_GLASS, GLOW_GREEN };

  return root;
}
