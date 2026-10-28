# three/ — 程序化 3D 模型库(img2threejs 风格)

> 理念来自 [img2threejs](https://github.com/img2threejs/img2threejs):**代码即模型**。
> 所有模型均为纯程序化 TypeScript 工厂 —— 零网格文件、零网络加载、毫秒级实例化,
> 替代此前的 HuggingFace 浏览器端深度估计方案(数 MB 模型下载、2.5D 浮雕)。

## 目录

```
three/
  types.ts              # ModelTick / ModelUserData 契约
  registry.ts           # ModelKey、品类映射、createModel、disposeGroup、resolveModelKey
  models/               # 每品类一个工厂文件
    plasticBottle.ts    # 塑料瓶(PET)
    glassBottle.ts      # 玻璃瓶(独立模型,T5:圆肩瓶+通透材质+瓶内液体)
    paperCup.ts         # 纸杯/奶茶杯(T5:微锥杯身+杯盖+吸管)
    kitchenWaste.ts     # 厨余垃圾(T5:stylized 低多边形,有机体不追求写实)
    cardboardBox.ts     # 瓦楞纸箱(织物类复用)
    aluminumCan.ts      # 易拉罐
    oldPhone.ts         # 旧手机
    newspaperStack.ts   # 废纸堆
    smallAppliance.ts   # 小家电(复古微波炉)
```

## 工厂契约(新增模型必须遵守)

```ts
export interface XxxOptions { scale?: number; /* 变体等 */ }

export function createXxxModel(options: XxxOptions = {}): THREE.Group {
  const root = new THREE.Group()          // 返回根
  const body = new THREE.Group()          // tick 动画载体(位置/旋转只动 body)
  root.add(body)
  // ... 全部零件挂到 body 下,几何体围绕原点构建(底面 y≈0,x/z 居中)

  modelData(root).tick = (t, dt) => { /* 待机动画:浮沉/轻摇/发光脉冲 */ }
  modelData(root).label = '中文展示名'
  return root
}
```

硬性规则:

1. **纯程序化** — 只许用 three 内置几何体 / LatheGeometry / RoundedBoxGeometry
   (`three/examples/jsm/geometries/RoundedBoxGeometry.js`),禁止贴图文件与网络资源。
2. **色板常量化** — 顶部集中声明 hex 色常量,品牌色对齐
   `front-shared/src/styles/tokens.scss`(墨绿 #2f6b5e / 青蓝绿 #3d8b85 / 蜂蜜金 #e8bc3f)。
3. **tick 只动 `body`** — ThreeViewer 会对 root 做归一化定位(Box3 居中缩放),
   tick 若写 root.position 会被覆盖/互相打架;旋转 pivot 也因此保持在模型中心。
4. **动画语义** — `tick(t, dt)`:t 为累计秒。做"活着"的待机感:±0.02~0.05 浮沉、
   轻摇、拉环/盖板微颤、LED/屏幕呼吸;不要做大幅度位移。
5. **可释放** — 不要给材质挂外部纹理;`disposeGroup()` 会遍历释放几何体与材质。
6. **注册** — 在 `registry.ts` 的 `FACTORIES` / `MODEL_LABELS` 登记,并把对应回收
   品类 code 加入 `CATEGORY_TO_MODEL`(品类字典的唯一事实源是
   `front-shared/src/mock/baseData.ts` 的 `RECYCLE_CATEGORIES`);
   无对应类目的模型(如 paperCup/kitchenWaste)只注册模型本身,不新增类目
   (类目变更属数据字典范畴)。

## T5 预设库精修(Track G,2026-08-04)

| 模型 | 说明 | 类目映射 | 备注 |
| --- | --- | --- | --- |
| `glassBottle` | 独立玻璃瓶:圆肩瓶 lathe 轮廓 + 通透玻璃材质 + 瓶内液体 + 金属旋盖,替换原 plasticBottle 的 glass 变体 | `glass` | 通透感/瓶肩曲线独立表达 |
| `paperCup` | 纸杯/奶茶杯:lathe 微锥杯身 + 透明杯盖(带饮口) + 吸管 tube | 无品类(其他垃圾,未在 RECYCLE_CATEGORIES) | **物品名 fallback 命中**:`纸杯`/`奶茶杯`/`一次性纸杯`(registry CATEGORY_TO_MODEL 两段式) |
| `kitchenWaste` | 厨余垃圾:香蕉皮/菜叶/果皮 stylized 低多边形(低分段+色块近似) | 无品类(厨余不属可回收类目) | **物品名 fallback 命中**:`香蕉皮`/`果皮`/`菜叶`/`剩饭`;有机体不追求几何精确(skill 盲区,已按 T2 提示词同策略处理) |

截图存档：`models/previews/`（每模型 `{key}-preview.png` 合成图 + `{key}-front/three-quarter/top.png` 三视角，服务端 render_page 同款渲染管线生成）。

## 运行时

- `ThreeViewer.vue`(src/components)负责:RoomEnvironment + PMREM 环境光、
  OrbitControls(阻尼 + 自动旋转,拖拽暂停 1.6s)、Box3 归一化取景、
  离屏/后台暂停 RAF、卸载时 `disposeGroup + renderer.dispose + forceContextLoss`。
- `resolveModelKey()` 接受品类 code('plastic')、中文名('塑料类')或 ModelKey,
  未知输入静默回退 `DEFAULT_MODEL`(废纸箱)—— 新增品类忘登记不会崩,只会显示纸箱。

## 质量门槛(移植自 img2threejs 的门禁思想)

新增/大改模型时对照检查:

- [ ] 剪影可辨识:灰盒(无材质)状态下也能认出是什么物品
- [ ] 身份细节齐备:易拉罐要有拉环和卷边,手机要有听筒/侧键,纸箱要有盖和胶带
- [ ] 材质通道独立:metalness/roughness 各司其职,不把颜色当金属度用
- [ ] 附着关系合理:零件不悬空、不穿模(允许风格化简化,但要有意为之)
- [ ] tick 自然循环:无跳变、无漂移累计
