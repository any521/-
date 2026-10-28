/**
 * shuke-lab-flux 流体引擎 GLSL 着色器集合
 *
 * - FULLSCREEN_QUAD_VERT: 全屏 quad 顶点着色器
 * - NOISE_CHUNK: hash / value-noise / 5 阶 FBM 公共 chunk
 * - CAPSULE_FRAG: 玻璃胶囊片元着色器(2 层域扭曲 FBM + 3 主题色 + 左侧白色蒙版 + 随机种子)
 * - BACKGROUND_FRAG: PC 全局全屏背景片元着色器(自旧 utils/fluid.ts 提取,视觉保持不变:
 *   三色 mix + (f^3 + .6f^2 + .5f) * color 亮度曲线)
 */

/** 全屏 quad 顶点着色器 */
export const FULLSCREEN_QUAD_VERT = /* glsl */ `
  attribute vec2 a_position;
  void main() {
    gl_Position = vec4(a_position, 0.0, 1.0);
  }
`

/** hash / value noise / 5 阶 FBM 公共 chunk */
export const NOISE_CHUNK = /* glsl */ `
  float hash(vec2 st) {
    return fract(sin(dot(st.xy, vec2(12.9898, 78.233))) * 43758.5453123);
  }

  float noise(vec2 st) {
    vec2 i = floor(st);
    vec2 f = fract(st);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i + vec2(0.0, 0.0)),
                   hash(i + vec2(1.0, 0.0)), u.x),
               mix(hash(i + vec2(0.0, 1.0)),
                   hash(i + vec2(1.0, 1.0)), u.x), u.y);
  }

  // 5 阶 FBM(分数布朗运动)
  float fbm5(vec2 st) {
    float value = 0.0;
    float amplitude = 0.5;
    vec2 shift = vec2(100.0);
    for (int i = 0; i < 5; i++) {
      value += amplitude * noise(st);
      st = st * 2.0 + shift;
      amplitude *= 0.5;
    }
    return value;
  }
`

/**
 * 玻璃胶囊片元着色器
 * uniform:
 *   u_resolution - canvas 像素尺寸
 *   u_time       - 流动时间(逐帧 flowTime += dt * speed)
 *   u_color1/2/3 - 3 主题色(RGB 0-1)
 *   u_maskLeft   - 0..1 左侧白色渐隐蒙版宽度,0 表示无蒙版
 *   u_seed       - 每卡片随机噪声种子(同时决定初相位)
 */
export const CAPSULE_FRAG = /* glsl */ `
  precision mediump float;

  uniform vec2 u_resolution;
  uniform float u_time;
  uniform vec3 u_color1;
  uniform vec3 u_color2;
  uniform vec3 u_color3;
  uniform float u_maskLeft;
  uniform float u_seed;

  ${NOISE_CHUNK}

  void main() {
    vec2 uv = gl_FragCoord.xy / u_resolution.xy;
    vec2 st = uv;
    st.x *= u_resolution.x / u_resolution.y;

    // 随机种子:偏移噪声域,保证每个胶囊形态不同
    vec2 seed = vec2(u_seed * 17.31, u_seed * 7.77);
    float t = u_time;
    float scale = 2.2;

    // 第 1 层域扭曲
    vec2 q = vec2(0.0);
    q.x = fbm5(st * scale + seed + 0.00 * t);
    q.y = fbm5(st * scale + seed + vec2(1.0));

    // 第 2 层域扭曲(时间驱动流动)
    vec2 r = vec2(0.0);
    r.x = fbm5(st * scale + 1.4 * q + seed + vec2(1.7, 9.2) + 0.15 * t);
    r.y = fbm5(st * scale + 1.4 * q + seed + vec2(8.3, 2.8) + 0.126 * t);

    float f = fbm5(st * scale + 1.4 * r);

    // 三色云雾混合
    vec3 color = mix(u_color1, u_color2, clamp(f * f * 4.0, 0.0, 1.0));
    color = mix(color, u_color3, clamp(length(q), 0.0, 1.0));
    color = mix(color, u_color1, clamp(r.x, 0.0, 1.0));

    // 亮度曲线 + 轻微奶白提亮,形成玻璃云雾质感
    color = (f * f * f + 0.6 * f * f + 0.5 * f) * color;
    color = mix(color, vec3(1.0), 0.12);

    // 左侧白色渐隐蒙版(给文字留白)
    if (u_maskLeft > 0.001) {
      float m = 1.0 - smoothstep(0.0, u_maskLeft, uv.x);
      color = mix(color, vec3(1.0), m * 0.92);
    }

    gl_FragColor = vec4(color, 1.0);
  }
`

/**
 * PC 全局全屏背景片元着色器
 * 从旧 utils/fluid.ts 原样提取(新增 u_color3 替代原第二个 mix 中的 u_color2,
 * 传 color3 = color2 时视觉与旧版完全一致)。u_maskLeft / u_seed 不使用。
 */
export const BACKGROUND_FRAG = /* glsl */ `
  precision mediump float;

  uniform vec2 u_resolution;
  uniform float u_time;
  uniform vec3 u_color1;
  uniform vec3 u_color2;
  uniform vec3 u_color3;
  uniform float u_maskLeft;
  uniform float u_seed;

  ${NOISE_CHUNK}

  void main() {
    vec2 st = gl_FragCoord.xy / u_resolution.xy;
    st.x *= u_resolution.x / u_resolution.y;

    vec2 q = vec2(0.);
    q.x = fbm5( st + 0.00 * u_time);
    q.y = fbm5( st + vec2(1.0));

    vec2 r = vec2(0.);
    r.x = fbm5( st + 1.0 * q + vec2(1.7,9.2)+ 0.15 * u_time );
    r.y = fbm5( st + 1.0 * q + vec2(8.3,2.8)+ 0.126 * u_time);

    float f = fbm5(st + r);

    vec3 color = mix(u_color1, u_color2, clamp((f*f)*4.0, 0.0, 1.0));
    color = mix(color, u_color3, clamp(length(q), 0.0, 1.0));
    color = mix(color, u_color1, clamp(length(r.x), 0.0, 1.0));

    gl_FragColor = vec4((f * f * f + .6 * f * f + .5 * f) * color, 1.0);
  }
`
