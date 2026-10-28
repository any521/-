/**
 * shuke-lab-flux 流体引擎(每 canvas 独立 WebGL 实例)
 *
 * 性能规则(必须遵守):
 * 1. 胶囊离屏必须 pause() —— 由 FluidCapsule 用 IntersectionObserver 实现;
 * 2. 每页并发 WebGL 胶囊数量 <= 8;
 * 3. 移动端使用 lite 模式(纯 CSS 渐变漂移),不起 WebGL;
 * 4. 页面隐藏(document.visibilitychange = hidden)时引擎自动暂停,恢复可见时自动续跑。
 *
 * 速度模型:
 * - 逐帧 flowTime += dt * currentSpeed;
 * - currentSpeed 以指数趋近 (targetSpeed + stirBoost);
 * - stir() 叠加指针搅动加成,总速度上限约 4 * baseSpeed,无输入时约 1s 内平滑衰减回 baseSpeed。
 */
import { FULLSCREEN_QUAD_VERT, CAPSULE_FRAG } from './shaders'

export type RGB = [number, number, number]

export interface FluidRendererOptions {
  /** 3 主题色(RGB 0-1) */
  colors: [RGB, RGB, RGB]
  /** 基础流速,默认 0.22 */
  baseSpeed?: number
  /** 左侧白色蒙版宽度 0..1,默认 0(无蒙版) */
  maskLeft?: number
  /** 噪声种子(同时决定初相位),默认随机 */
  seed?: number
  /** 渲染分辨率缩放,默认 0.5(胶囊);全屏背景传 1 */
  resolutionScale?: number
  /** devicePixelRatio 上限,默认 1.5 */
  dprCap?: number
  /** 自定义片元着色器,默认胶囊着色器;全屏背景传 BACKGROUND_FRAG */
  fragmentShader?: string
}

export interface FluidHandle {
  /** 设置目标速度,内部做缓动趋近 */
  setSpeed(v: number): void
  /** 指针搅动:nx/ny 为归一化指针位置(-1..1,预留给方向性扰动),strength 为速度加成 */
  stir(nx: number, ny: number, strength: number): void
  pause(): void
  resume(): void
  /** cancelAnimationFrame + deleteProgram/Shader/Buffer + WEBGL_lose_context */
  dispose(): void
}

/** 搅动加成衰减时间常数(s):约 1s 内回落到 ~5% */
const STIR_DECAY_TAU = 0.35
/** currentSpeed 趋近目标的时间常数 */
const SPEED_EASE = 5
/** 总速度上限 = baseSpeed * STIR_MAX_MULT */
const STIR_MAX_MULT = 4

function createShader(gl: WebGLRenderingContext, type: number, source: string): WebGLShader | null {
  const shader = gl.createShader(type)
  if (!shader) return null
  gl.shaderSource(shader, source)
  gl.compileShader(shader)
  if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
    console.error('[fluid-engine] shader compile failed:', gl.getShaderInfoLog(shader))
    gl.deleteShader(shader)
    return null
  }
  return shader
}

export function createFluidRenderer(
  canvas: HTMLCanvasElement,
  opts: FluidRendererOptions,
): FluidHandle | null {
  const gl = (canvas.getContext('webgl2') || canvas.getContext('webgl')) as WebGLRenderingContext | null
  if (!gl) return null

  const colors = opts.colors
  const baseSpeed = opts.baseSpeed ?? 0.22
  const maskLeft = opts.maskLeft ?? 0
  const seed = opts.seed ?? Math.random()
  const resolutionScale = opts.resolutionScale ?? 0.5
  const dprCap = opts.dprCap ?? 1.5
  const fragSource = opts.fragmentShader ?? CAPSULE_FRAG

  const vertexShader = createShader(gl, gl.VERTEX_SHADER, FULLSCREEN_QUAD_VERT)
  const fragmentShader = createShader(gl, gl.FRAGMENT_SHADER, fragSource)
  const program = gl.createProgram()
  if (!vertexShader || !fragmentShader || !program) return null

  gl.attachShader(program, vertexShader)
  gl.attachShader(program, fragmentShader)
  gl.linkProgram(program)
  if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
    console.error('[fluid-engine] program link failed:', gl.getProgramInfoLog(program))
    gl.deleteProgram(program)
    gl.deleteShader(vertexShader)
    gl.deleteShader(fragmentShader)
    return null
  }

  const positionLoc = gl.getAttribLocation(program, 'a_position')
  const positionBuffer = gl.createBuffer()
  gl.bindBuffer(gl.ARRAY_BUFFER, positionBuffer)
  gl.bufferData(
    gl.ARRAY_BUFFER,
    new Float32Array([-1, -1, 1, -1, -1, 1, -1, 1, 1, -1, 1, 1]),
    gl.STATIC_DRAW,
  )

  // uniform 位置(未使用的 uniform 会被编译器优化掉,getUniformLocation 返回 null,
  // WebGL 对 null location 的 uniform 调用是合法 no-op)
  const uResolution = gl.getUniformLocation(program, 'u_resolution')
  const uTime = gl.getUniformLocation(program, 'u_time')
  const uColor1 = gl.getUniformLocation(program, 'u_color1')
  const uColor2 = gl.getUniformLocation(program, 'u_color2')
  const uColor3 = gl.getUniformLocation(program, 'u_color3')
  const uMaskLeft = gl.getUniformLocation(program, 'u_maskLeft')
  const uSeed = gl.getUniformLocation(program, 'u_seed')

  // ---- 尺寸同步:contentRect * resolutionScale * min(dpr, dprCap) ----
  const resize = () => {
    const dpr = Math.min(window.devicePixelRatio || 1, dprCap)
    const w = Math.max(1, Math.round(canvas.clientWidth * resolutionScale * dpr))
    const h = Math.max(1, Math.round(canvas.clientHeight * resolutionScale * dpr))
    if (canvas.width !== w) canvas.width = w
    if (canvas.height !== h) canvas.height = h
  }
  resize()
  const ro = typeof ResizeObserver !== 'undefined' ? new ResizeObserver(resize) : null
  ro?.observe(canvas)

  // ---- 速度 / 时间状态 ----
  let targetSpeed = baseSpeed
  let currentSpeed = baseSpeed
  let stirBoost = 0
  // 初相位由种子决定,避免所有胶囊同相
  let flowTime = seed * 40

  let rafId = 0
  let lastTime = 0
  let running = false
  let manuallyPaused = false
  let disposed = false

  const draw = () => {
    gl.viewport(0, 0, gl.canvas.width, gl.canvas.height)
    gl.clearColor(0, 0, 0, 0)
    gl.clear(gl.COLOR_BUFFER_BIT)
    gl.useProgram(program)
    gl.enableVertexAttribArray(positionLoc)
    gl.bindBuffer(gl.ARRAY_BUFFER, positionBuffer)
    gl.vertexAttribPointer(positionLoc, 2, gl.FLOAT, false, 0, 0)

    gl.uniform2f(uResolution, gl.canvas.width, gl.canvas.height)
    gl.uniform1f(uTime, flowTime)
    gl.uniform3f(uColor1, colors[0][0], colors[0][1], colors[0][2])
    gl.uniform3f(uColor2, colors[1][0], colors[1][1], colors[1][2])
    gl.uniform3f(uColor3, colors[2][0], colors[2][1], colors[2][2])
    gl.uniform1f(uMaskLeft, maskLeft)
    gl.uniform1f(uSeed, seed)

    gl.drawArrays(gl.TRIANGLES, 0, 6)
  }

  const frame = (now: number) => {
    if (!running) return
    const dt = Math.min(Math.max((now - lastTime) / 1000, 0), 0.1)
    lastTime = now

    // 搅动加成平滑衰减(约 1s 回落)
    stirBoost *= Math.exp(-dt / STIR_DECAY_TAU)
    if (stirBoost < 1e-4) stirBoost = 0

    // currentSpeed 指数趋近目标
    const goal = Math.min(targetSpeed + stirBoost, baseSpeed * STIR_MAX_MULT)
    currentSpeed += (goal - currentSpeed) * (1 - Math.exp(-dt * SPEED_EASE))

    flowTime += dt * currentSpeed
    draw()
    rafId = requestAnimationFrame(frame)
  }

  const start = () => {
    if (running || disposed) return
    running = true
    lastTime = performance.now()
    rafId = requestAnimationFrame(frame)
  }

  const stop = () => {
    if (!running) return
    running = false
    cancelAnimationFrame(rafId)
  }

  // ---- 页面可见性:hidden 自动暂停,visible 且未被人为暂停时恢复 ----
  const onVisibility = () => {
    if (disposed) return
    if (document.hidden) {
      stop()
    } else if (!manuallyPaused) {
      start()
    }
  }
  document.addEventListener('visibilitychange', onVisibility)

  start()

  return {
    setSpeed(v: number) {
      targetSpeed = v
    },
    stir(_nx: number, _ny: number, strength: number) {
      // nx/ny 预留(未来可做方向性扰动);当前仅按强度叠加速度加成
      stirBoost = Math.min(stirBoost + Math.max(strength, 0), baseSpeed * STIR_MAX_MULT)
    },
    pause() {
      manuallyPaused = true
      stop()
    },
    resume() {
      manuallyPaused = false
      if (!document.hidden) start()
    },
    dispose() {
      if (disposed) return
      disposed = true
      stop()
      ro?.disconnect()
      document.removeEventListener('visibilitychange', onVisibility)
      gl.deleteProgram(program)
      gl.deleteShader(vertexShader)
      gl.deleteShader(fragmentShader)
      gl.deleteBuffer(positionBuffer)
      const ext = gl.getExtension('WEBGL_lose_context')
      if (ext) ext.loseContext()
    },
  }
}
