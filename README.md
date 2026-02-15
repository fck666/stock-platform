# stock-platform

一个包含数据采集、后端 API 与 Web 前端的股票数据与分析平台。

## 模块说明

- `backend-api/`：Spring Boot（Java 17）后端，提供 `/api/**` REST 接口
- `frontend-web/`：Vue 3 + Vite + TypeScript 前端
- `data-collector/`：Python 数据采集与入库 CLI（可被后端触发执行）
- `docker/`：PostgreSQL 与应用近生产环境的 Docker Compose 配置

## 运行环境

- JDK 17
- Node.js 18+（建议）与 npm
- Python 3.10+（建议 3.12）与 venv
- Docker（本地启动 Postgres / 近生产一键启动时需要）

## 快速开始（本地开发）

### 1）启动 Postgres（测试库，端口 5433）

在仓库根目录执行：

```bash
docker compose \
  --env-file docker/postgresql/.env.test.example \
  -f docker/postgresql/compose.yml \
  -f docker/postgresql/compose.test.yml \
  up -d
```

默认连接信息（可通过 `.env.test.example` 覆盖）：

- Host：`localhost`
- Port：`5433`
- DB：`stock_platform_test`
- User：`stock`
- Password：`stockpass`

### 2）启动后端（默认 8080）

```bash
cd backend-api
./mvnw spring-boot:run
```

说明：

- 默认启用 `test` profile（见 `backend-api/src/main/resources/application.yaml`），会连接上一步的 `5433` 数据库
- 默认允许的前端跨域来源为 `http://localhost:5173`
- JWT secret 未配置时会在每次启动时随机生成（重启后旧 token 会失效）
- 若你不需要后端触发 Python 采集器，可设置 `DATA_COLLECTOR_ENABLED=false`（否则触发同步接口时需要配置好 `data-collector/` 的运行环境）

### 3）启动前端（默认 5173）

```bash
cd frontend-web
npm ci
npm run dev
```

前端通过环境变量 `VITE_API_BASE_URL` 指向后端，例如创建 `frontend-web/.env.development`：

```bash
VITE_API_BASE_URL=http://localhost:8080
```

打开：

- 前端：`http://localhost:5173`
- 后端：`http://localhost:8080/api/...`

## 一键启动（Docker / 近生产）

该方式会：

- 启动 Postgres（端口 5432）
- 构建并启动后端与前端（Nginx 对外暴露前端端口，默认 8089，并反代 `/api/` 到后端）

### 1）启动 Postgres（生产库，端口 5432）

```bash
docker compose \
  --env-file docker/postgresql/.env.prod.example \
  -f docker/postgresql/compose.yml \
  -f docker/postgresql/compose.prod.yml \
  up -d
```

### 2）启动应用（前端对外端口 8089）

```bash
docker compose \
  --env-file docker/app/.env.prod.example \
  -f docker/app/compose.prod.yml \
  up -d --build
```

访问：

- Web：`http://localhost:8089`
- API：`http://localhost:8089/api/...`（同域反代）

注意：

- `docker/app/compose.prod.yml` 使用外部网络 `stock-platform-net`；通常先启动上面的 Postgres compose 即可创建该网络
- 建议为 `.env.prod.example` 中的 `SPRING_DATASOURCE_PASSWORD`、`SECURITY_JWT_SECRET`、`INIT_ADMIN_*` 配置真实值，并勿提交明文密钥

## 鉴权与请求头

### JWT Bearer Token

- 登录：`POST /api/auth/login`
- 刷新：`POST /api/auth/refresh`
- 登出：`POST /api/auth/logout`

后端通过请求头解析 access token：

```text
Authorization: Bearer <accessToken>
```

前端在本地会自动保存 token，并在请求时自动携带该头。

### X-Profile-Key（匿名 profile）

部分用户态数据（例如 plans、alerts）通过 `X-Profile-Key` 将“匿名 profile”与数据关联。前端会自动生成并持久化该值，并在请求时自动携带：

```text
X-Profile-Key: <uuid>
```

如果你使用 Postman / curl 直接调这些接口，需要自行补上该请求头。

## data-collector（Python CLI）

该模块可独立运行，也可由后端通过“同步”相关接口触发执行。

### 安装依赖与查看命令

```bash
cd data-collector
python3 -m venv venv
./venv/bin/pip install -r requirements.txt
./venv/bin/python main.py --help
```

### 数据库连接

data-collector 使用 `DB_DSN`（形如 `postgresql://user:pass@host:port/db`）。当由后端触发时，会尝试根据后端的 `spring.datasource.*` 自动拼装并注入该环境变量。

### 数据源配置（行情 / 基本面）

data-collector 支持通过环境变量切换数据源；当由后端触发时，可通过后端的 `app.data-collector.*` 配置项（或对应环境变量）透传给采集器进程。

- 行情（K 线）：`PRICE_PROVIDER=stooq|eodhd`（默认 `stooq`）
- 基本面/公司行为（fundamentals/dividends/splits）：`METADATA_PROVIDER=auto|yahoo|eodhd`（默认 `auto`）
- EODHD Token：`EODHD_API_TOKEN=...`（留空则 EODHD 相关能力会自动回退）
- 仅在 `METADATA_PROVIDER=auto` 时生效：`EODHD_USE_FOR_SPX=true|false`（默认 `false`，控制 SPX 是否也优先使用 EODHD）

后端环境变量（推荐在 Docker / 部署时使用）：

```bash
DATA_COLLECTOR_PRICE_PROVIDER=stooq|eodhd
DATA_COLLECTOR_METADATA_PROVIDER=auto|yahoo|eodhd
EODHD_API_TOKEN=...
EODHD_USE_FOR_SPX=false
```

直接运行 data-collector 时也可使用同名变量：

```bash
export PRICE_PROVIDER=stooq
export METADATA_PROVIDER=auto
export EODHD_API_TOKEN=
export EODHD_USE_FOR_SPX=false
```

### 权限管理配置（JWT / 管理员 / 免登）

这些配置通过后端环境变量（或 `application.yaml`）生效，适用于 `docker/app/.env.prod.example` 或本地开发。

#### 1. JWT 认证

- `SECURITY_JWT_SECRET`：JWT 签名密钥（必须 ≥ 32 字符）。**生产环境必须设置固定值**，否则每次重启后端都会导致旧 Token 失效。
- `SECURITY_JWT_ACCESS_TTL_SECONDS`：Access Token 有效期（秒），默认 1800（30分钟）。
- `SECURITY_JWT_REFRESH_TTL_SECONDS`：Refresh Token 有效期（秒），默认 2592000（30天）。

#### 2. 初始化管理员 (Bootstrapping)

后端启动时可自动检查并创建/更新初始管理员账号（密码不写入代码库）。

- `INIT_ADMIN_USERNAME`：管理员用户名（例如 `admin`）。
- `INIT_ADMIN_PASSWORD`：管理员密码（例如 `ChangeMe123!`）。

> **注意**：系统默认会尝试初始化用户 `fcc` 为超级管理员（默认密码 `123456`，请及时修改）。

#### 3. 权限与审计

- **角色体系**：
  - `super_admin`：超级管理员，拥有所有权限，包括管理其他管理员。
  - `admin`：普通管理员，可管理业务数据与普通用户，不可管理超级管理员。
  - `manager` / `viewer` / `user`：业务角色。
- **审计日志**：
  - 所有敏感操作（如用户创建、删除、权限变更、密码重置）均会记录在 `iam.audit_logs` 表中。
  - 超级管理员可通过前端界面查看审计日志。
- **单点登录 (SSO)**：
  - 系统默认启用单点登录限制（同一账号同一端仅允许一个活跃会话）。
  - 登录时会自动踢出旧会话。

#### 4. 测试环境免登 (Dev/Test Only)

**仅建议在本地或内网测试环境开启**。开启后，若请求未携带 Authorization 头，后端会自动注入指定的身份，前端也会自动识别为已登录。

- `SECURITY_DEV_AUTH_ENABLED`：`true` | `false`（默认 `false`）。
- `SECURITY_DEV_AUTH_USERNAME`：免登时模拟的用户名（默认 `dev_admin`）。
- `SECURITY_DEV_AUTH_ROLES`：免登时模拟的角色（逗号分隔，默认 `admin`）。
- `SECURITY_DEV_AUTH_AUTO_CREATE_USER`：`true` | `false`（默认 `true`）。若模拟的用户不存在，是否自动创建并授权。

## 常见问题

- 后端触发同步时报 `DATA_COLLECTOR_WORKING_DIR is not configured`
  - 设置 `DATA_COLLECTOR_WORKING_DIR=../data-collector`（本地）或 `DATA_COLLECTOR_ENABLED=false`（不使用采集器）
- token 重启后失效
  - 配置固定的 `SECURITY_JWT_SECRET`（长度建议 ≥ 32 字符），避免随机生成 key
