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

## 常见问题

- 后端触发同步时报 `DATA_COLLECTOR_WORKING_DIR is not configured`
  - 设置 `DATA_COLLECTOR_WORKING_DIR=../data-collector`（本地）或 `DATA_COLLECTOR_ENABLED=false`（不使用采集器）
- token 重启后失效
  - 配置固定的 `SECURITY_JWT_SECRET`（长度建议 ≥ 32 字符），避免随机生成 key

