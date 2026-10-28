# 点废成金 · 畅通低碳循环平台

面向社区再生资源回收与低碳生活的一体化平台：居民预约回收、厨余投放、家电维修、绿色商城、低碳账单，回收员/环卫员/技术员移动作业，以及管理端调度大屏与 AI 辅助决策。

## 功能概览

- **用户端 H5**：预约回收、厨余投放、维修下单、绿色商城、碳积分与低碳证书、消息中心；支持端侧 YOLO 垃圾分类与 AR 展示。
- **员工端 H5**：接单台、智能路线导航、物资登记、维修报告、配件登记、异常上报、打卡与绩效，弱网离线缓存。
- **管理驾驶舱**：订单/回收/维修/商城/碳资产全景看板，区域与员工调度，AI 配置与审计。
- **Java 后端**：多模块 Spring Boot 服务，JWT 双令牌、RBAC、数据权限、Flyway 版本化迁移、WebSocket 消息推送。
- **Python AI 服务**：多供应商 LLM 路由与降级、RAG 价格/知识检索、视觉核验、3D 物品生成、语音解析、时序预测。

## 技术栈

| 层 | 技术 |
| --- | --- |
| 管理端 | Vue 3、TypeScript、Element Plus、Three.js、Vite |
| 用户端 / 员工端 | Vue 3、TypeScript、Vant 4、Vite、PWA/离线缓存 |
| 共享层 | `front-shared`：设计令牌、请求封装、错误码、Mock 引擎 |
| 后端 | Java 17、Spring Boot 3.2、MyBatis-Plus、Spring Security、Redis、MySQL 8、Flyway |
| AI 服务 | Python 3.11+、FastAPI、ONNX Runtime、可选 Qdrant / LLM 供应商 |
| 部署 | Docker Compose、Nginx |

## 仓库结构

```
.
├── back/                 # Java 多模块后端（common/system/business/ai/dispatch/admin）
│   └── dianfei-admin/    # 启动模块与 Flyway 迁移脚本
├── ai-back/              # Python FastAPI AI 服务（app/、rag/、config/）
├── front/                # 管理驾驶舱（base=/点废成金/admin/）
├── front-user/           # 用户端 H5（base=/点废成金/）
├── front-staff/          # 员工端 H5（base=/点废成金/staff/）
├── front-shared/         # 三端共享源码层
├── deploy/               # docker-compose、Nginx 模板、环境变量示例
└── .github/workflows/    # CI：后端构建与单测、三端构建、AI 服务冒烟
```

## 快速开始

### 前置环境

- JDK 17、Maven 3.9+
- Node.js 22.18+（或 24.12+）、npm
- Python 3.11+
- MySQL 8、Redis 7（本地运行后端时需要）
- Docker（可选，用于一键编排）

### 1. 后端

```bash
# 准备数据库（Flyway 会在启动时自动建表/迁移）
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS dianfei DEFAULT CHARACTER SET utf8mb4;"

cd back
export MYSQL_PASSWORD='你的数据库口令'
# 可选：export JWT_SECRET='至少32字节的随机串'
mvn -s settings.xml clean package -DskipTests
java -jar dianfei-admin/target/dianfei-admin.jar --spring.profiles.active=dev
```

默认端口 `8080`，健康检查：`GET http://localhost:8080/api/health`。

### 2. AI 服务（可选）

```bash
cd ai-back
python -m venv .venv
# Windows: .venv\Scripts\activate    macOS/Linux: source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8100
```

未配置 LLM Key 时，服务会走 mock/规则降级路径，不影响主流程联调。后端开发环境默认连接 `http://127.0.0.1:8000`，如 AI 服务运行在 8100，请设置：

```bash
export DIANFEI_AI_BASE_URL=http://127.0.0.1:8100
```

### 3. 前端

三个前端可独立启动，开发端口分别为：管理端 `5173`、用户端 `5174`、员工端 `5175`。

```bash
cd front        # 管理端
cp .env.example .env.development
npm ci && npm run dev

cd ../front-user   # 用户端
cp .env.example .env.development
npm ci && npm run dev

cd ../front-staff  # 员工端
cp .env.example .env.development
npm ci && npm run dev
```

生产构建时把 `.env.example` 复制为 `.env.production`，并按部署子路径调整 `VITE_API_BASE`（如 `/点废成金/api`）。

访问地址需带 base 路径：

- 管理端：`http://localhost:5173/点废成金/admin/`
- 用户端：`http://localhost:5174/点废成金/`
- 员工端：`http://localhost:5175/点废成金/staff/`

开发服务器已将 `/api` 代理到 `http://localhost:8080`。

### 4. Docker Compose（可选）

```bash
cd deploy
cp .env.example .env
# 修改 .env 中全部密码/密钥后：
docker compose up -d --build
```

独立 Nginx 模式需先构建三端静态产物（`npm run build`），再执行：

```bash
docker compose --profile standalone up -d
```

## 配置与密钥

所有敏感配置均通过环境变量注入，仓库不包含真实密钥：

| 变量 | 说明 |
| --- | --- |
| `MYSQL_PASSWORD` / `MYSQL_ROOT_PASSWORD` | 数据库口令 |
| `REDIS_PASSWORD` | Redis 口令 |
| `JWT_SECRET` | JWT 签名密钥（≥32 字节随机值） |
| `DIANFEI_FIELD_KEY` | 敏感字段加密密钥 |
| `DIANFEI_AI_INTERNAL_TOKEN` / `AI_INTERNAL_TOKEN` | 后端与 AI 服务内网鉴权 Token |
| `DIANFEI_AI_CONFIG_AES_KEY` | AI 供应商密钥列 AES 密钥 |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | 发件邮箱与 SMTP 授权码 |
| `DEEPSEEK_API_KEY` / `ARK_API_KEY` / `BOCHA_API_KEY` | AI 供应商密钥 |
| `DIANFEI_OSS_ACCESS_KEY_ID` / `DIANFEI_OSS_ACCESS_KEY_SECRET` | 阿里云 OSS 凭据（可选） |
| `VITE_AMAP_WEB_KEY` / `VITE_AMAP_SECURITY_CODE` | 高德地图 Key（可选，也可由后端下发） |

请勿把填写后的 `.env`、`.env.local` 提交到仓库。若不慎泄露，请立即在对应平台轮换密钥。

## 未随仓库分发的运行时资产

为保持仓库为纯源码、避免大文件与第三方数据版权/隐私问题，以下内容未提交，可按需自行准备；缺失时相关功能会自动降级：

| 路径 | 内容 | 获取方式 |
| --- | --- | --- |
| `ai-back/data/` | 回收价格数据集（约 46MB） | 使用自有数据，按 `rag/indexing/build_index.py` 约定的字段放置 6 类 JSON |
| `ai-back/rag_data/` | RAG 索引产物（约 30MB） | 数据就位后执行 `cd ai-back && python -m rag.indexing.build_index` |
| `ai-back/models/`、`front-user/public/models/*.onnx` | YOLO 垃圾分类模型（约 10MB/个） | 使用自有训练产物，按 `front-user/public/models/manifest.json` 的命名与类别放置 |
| `front-user/src/assets/ort/*.wasm` | ONNX Runtime WASM（约 40MB） | 从 `onnxruntime-web` npm 包 `dist/` 复制对应版本文件到该目录 |

`front-user/public/models/manifest.json` 与 `src/assets/ort/*.mjs` 已保留，便于对齐模型版本和加载入口。

## 演示账号

员工端/用户端演示数据由 Flyway `V49__staff_biz_seed.sql` 写入：

| 账号 | 角色 | 密码 |
| --- | --- | --- |
| `R1001` | 回收员 | `123456` |
| `S2001` | 环卫员 | `123456` |
| `T3001` | 技术员 | `123456` |
| `user001` | 演示市民 | `123456` |

管理员账号为 `admin`。出于安全考虑，`V30__security_default_password_reset.sql` 会废弃旧演示口令；首次部署请自行生成 BCrypt 密文并更新 `sys_user.password` 后登录。

## 贡献

欢迎提交 Issue 与 Pull Request。提交前请确保：

- 不包含任何真实密钥、账号、服务器地址或用户隐私数据；
- 后端通过 `mvn -s settings.xml test`，前端通过 `npm run build`。

## 开源协议

本项目基于 [MIT License](LICENSE) 开源。
