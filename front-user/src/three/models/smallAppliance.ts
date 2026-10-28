import * as THREE from 'three'
import { RoundedBoxGeometry } from 'three/examples/jsm/geometries/RoundedBoxGeometry.js'
import { modelData } from '../types'

/**
 * 程序化小家电(复古微波炉),img2threejs 风格重建。
 * 圆角机箱 + 玻璃炉门 + 门把手 + 控制面板(旋钮/按键/绿色 LED 数码管)+ 支脚。
 * LED 呼吸闪烁,炉门内暖光微亮。
 */

// ---- palette ----
const SHELL = 0xe8e6e1; // 米白机箱
const SHELL_DARK = 0xc9c6bf; // 侧板
const DOOR_GLASS = 0x1c2229; // 炉门玻璃
const PANEL_DARK = 0x2c3138; // 控制面板
const LED_GREEN = 0x3dff9e; // LED 数码绿
const KNOB_METAL = 0xb9bec6;

export interface SmallApplianceOptions {
  scale?: number;
}

export function createSmallApplianceModel(options: SmallApplianceOptions = {}): THREE.Group {
  const root = new THREE.Group();
  root.name = 'small-appliance';
  const body = new THREE.Group();
  body.name = 'body';
  root.add(body);

  const W = 1.25;
  const H = 0.78;
  const D = 0.82;

  // ---- 机箱 ----
  const shell = new THREE.Mesh(
    new RoundedBoxGeometry(W, H, D, 4, 0.05),
    new THREE.MeshStandardMaterial({ color: SHELL, metalness: 0.25, roughness: 0.5 }),
  );
  shell.name = 'chassis';
  shell.position.y = H / 2 + 0.06;
  body.add(shell);

  // 顶部散热孔
  const ventMat = new THREE.MeshStandardMaterial({ color: SHELL_DARK, roughness: 0.7 });
  for (let i = 0; i < 5; i++) {
    const vent = new THREE.Mesh(new THREE.BoxGeometry(0.5, 0.008, 0.03), ventMat);
    vent.position.set(-W * 0.12, H + 0.062, -D * 0.25 + i * 0.09);
    body.add(vent);
  }

  // ---- 炉门(左侧 2/3):门框 + 玻璃 + 内腔暖光 ----
  const doorW = W * 0.62;
  const doorH = H * 0.72;
  const doorX = -W / 2 + doorW / 2 + W * 0.06;

  const doorFrame = new THREE.Mesh(
    new RoundedBoxGeometry(doorW, doorH, 0.04, 3, 0.025),
    new THREE.MeshStandardMaterial({ color: SHELL_DARK, metalness: 0.4, roughness: 0.4 }),
  );
  doorFrame.name = 'door-frame';
  doorFrame.position.set(doorX, H / 2 + 0.06, D / 2 + 0.01);
  body.add(doorFrame);

  const doorGlass = new THREE.Mesh(
    new RoundedBoxGeometry(doorW - 0.1, doorH - 0.1, 0.02, 3, 0.02),
    new THREE.MeshPhysicalMaterial({
      color: DOOR_GLASS,
      metalness: 0.2,
      roughness: 0.08,
      clearcoat: 1.0,
      clearcoatRoughness: 0.05,
    }),
  );
  doorGlass.name = 'door-glass';
  doorGlass.position.set(doorX, H / 2 + 0.06, D / 2 + 0.03);
  body.add(doorGlass);

  // 炉内暖光(透过玻璃)
  const cavityGlow = new THREE.Mesh(
    new THREE.PlaneGeometry(doorW - 0.16, doorH - 0.16),
    new THREE.MeshStandardMaterial({
      color: 0xffb35c,
      emissive: 0xff9e3d,
      emissiveIntensity: 0.25,
      transparent: true,
      opacity: 0.35,
    }),
  );
  cavityGlow.name = 'cavity-glow';
  cavityGlow.position.set(doorX, H / 2 + 0.06, D / 2 + 0.045);
  body.add(cavityGlow);

  // 门把手(右侧竖条)
  const handle = new THREE.Mesh(
    new THREE.CapsuleGeometry(0.025, doorH * 0.7, 6, 14),
    new THREE.MeshStandardMaterial({ color: KNOB_METAL, metalness: 0.9, roughness: 0.25 }),
  );
  handle.name = 'door-handle';
  handle.position.set(doorX + doorW / 2 + 0.03, H / 2 + 0.06, D / 2 + 0.035);
  body.add(handle);

  // ---- 控制面板(右侧 1/3) ----
  const panelW = W * 0.2;
  const panelX = W / 2 - panelW / 2 - W * 0.05;
  const panel = new THREE.Mesh(
    new RoundedBoxGeometry(panelW, H * 0.78, 0.03, 3, 0.02),
    new THREE.MeshStandardMaterial({ color: PANEL_DARK, metalness: 0.5, roughness: 0.45 }),
  );
  panel.name = 'control-panel';
  panel.position.set(panelX, H / 2 + 0.06, D / 2 + 0.012);
  body.add(panel);

  // LED 数码管
  const led = new THREE.Mesh(
    new THREE.PlaneGeometry(panelW * 0.7, 0.09),
    new THREE.MeshStandardMaterial({
      color: LED_GREEN,
      emissive: LED_GREEN,
      emissiveIntensity: 1.0,
    }),
  );
  led.name = 'led-display';
  led.position.set(panelX, H * 0.72, D / 2 + 0.03);
  body.add(led);

  // 旋钮 ×2
  const knobMat = new THREE.MeshStandardMaterial({ color: KNOB_METAL, metalness: 0.9, roughness: 0.28 });
  const knob1 = new THREE.Mesh(new THREE.CylinderGeometry(0.045, 0.05, 0.03, 24), knobMat);
  knob1.rotation.x = Math.PI / 2;
  knob1.position.set(panelX, H * 0.48, D / 2 + 0.03);
  body.add(knob1);
  const knob2 = knob1.clone();
  knob2.position.y = H * 0.34;
  body.add(knob2);

  // 按键 ×3
  for (let i = 0; i < 3; i++) {
    const btn = new THREE.Mesh(
      new THREE.CylinderGeometry(0.018, 0.018, 0.015, 16),
      new THREE.MeshStandardMaterial({ color: 0x454b54, metalness: 0.6, roughness: 0.4 }),
    );
    btn.rotation.x = Math.PI / 2;
    btn.position.set(panelX - 0.035 + i * 0.035, H * 0.2, D / 2 + 0.028);
    body.add(btn);
  }

  // ---- 支脚 ----
  const footMat = new THREE.MeshStandardMaterial({ color: 0x3a3f45, roughness: 0.7 });
  const mkFoot = (x: number, z: number) => {
    const f = new THREE.Mesh(new THREE.CylinderGeometry(0.045, 0.05, 0.06, 16), footMat);
    f.position.set(x, 0.03, z);
    body.add(f);
  };
  mkFoot(-W * 0.38, D * 0.32);
  mkFoot(W * 0.38, D * 0.32);
  mkFoot(-W * 0.38, -D * 0.32);
  mkFoot(W * 0.38, -D * 0.32);

  if (options.scale) root.scale.setScalar(options.scale);

  // ---- 待机动画:LED 呼吸 + 炉光轻闪 + 整体微浮 ----
  modelData(root).tick = (t) => {
    body.position.y = Math.sin(t * 0.9) * 0.015;
    body.rotation.y = Math.sin(t * 0.35) * 0.18;
    (led.material as THREE.MeshStandardMaterial).emissiveIntensity = 0.7 + Math.sin(t * 2.2) * 0.3;
    (cavityGlow.material as THREE.MeshStandardMaterial).emissiveIntensity =
      0.2 + Math.max(0, Math.sin(t * 1.4)) * 0.15;
  };
  modelData(root).label = '小家电';
  modelData(root).palette = { SHELL, PANEL_DARK, LED_GREEN };

  return root;
}
