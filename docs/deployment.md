# 部署文档

自动化营销任务系统部署指南。

## 环境要求

| 组件 | 版本要求  | 说明                       |
|---|-------|--------------------------|
| JDK | 21+   | 推荐 Temurin/OpenJDK 21    |
| MySQL | 8.0+  | 存储层，需启用 `utf8mb4`        |
| Node.js | 20+   | 前端构建和开发                  |
| pnpm | 9.x   | 包管理器，workspace 模式        |
| Maven | 3.9+  | 后端构建（wrapper 已自带 `mvnw`） |
| Nginx | 1.22+ | 生产环境反向代理（可选，用于静态资源服务）    |

## 本地开发

### 1. 克隆仓库

```bash
git clone <repo-url>
cd marketing-task-platform
```

### 2. 后端

#### 2.1 配置数据库

编辑 `backend/src/main/resources/application-dev.yml`，填写你的 MySQL 连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://<host>:<port>/marketing_task_platform?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
    username: <your-username>
    password: <your-password>
```

#### 2.2 创建数据库

```sql
CREATE DATABASE IF NOT EXISTS marketing_task_platform
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
```

#### 2.3 启动后端

Flyway 会在首次启动时自动创建所有表结构（`application-dev.yml` 中 `spring.flyway.enabled: true`）。

```bash
cd backend
./mvnw spring-boot:run
```

后端启动后：
- API 地址：`http://localhost:8080`
- Swagger UI：`http://localhost:8080/swagger-ui.html`
- OpenAPI JSON：`http://localhost:8080/v3/api-docs`

#### 2.4 运行测试

```bash
cd backend
./mvnw test
```

测试使用 H2 内存数据库（`application-test.yml`），无需 MySQL。

### 3. 管理后台 (admin-web)

```bash
cd admin-web
pnpm install
npm run dev
```

开发服务器默认运行在 `http://localhost:5173`。

### 4. C 端 (client-web)

```bash
cd client-web
pnpm install
npm run dev
```

开发服务器默认运行在 `http://localhost:5174`。

### 5. 生成 OpenAPI 类型

当后端 API 发生变更后，需要重新生成 TypeScript 类型定义：

```bash
# 确保后端在 localhost:8080 运行
./scripts/generate-api-types.sh     # macOS / Linux
scripts\generate-api-types.cmd       # Windows

# 或者分别在各自项目中运行：
npm --prefix admin-web run generate-api
npm --prefix client-web run generate-api
```

生成的 `schema.d.ts` 文件位于 `src/api/generated/` 下，不会被提交到版本控制。

## 测试环境部署

### 后端

```bash
# 构建可执行 JAR
cd backend
./mvnw package -DskipTests

# 上传到服务器后运行
java -jar target/marketing-task-platform-*.jar \
  --spring.profiles.active=test \
  --spring.datasource.url=jdbc:mysql://<test-db-host>:3306/marketing_task_platform \
  --spring.datasource.username=<username> \
  --spring.datasource.password=<password>
```

Flyway 会在启动时自动执行未应用的迁移脚本。

### 前端

```bash
# 构建管理后台
cd admin-web
pnpm install
npm run build
# 产物在 admin-web/dist/

# 构建 C 端
cd client-web
pnpm install
npm run build
# 产物在 client-web/dist/
```

### Nginx 配置示例

将构建产物部署到 Nginx，配置 SPA 路由回退和 API 代理：

```nginx
server {
    listen 80;
    server_name admin.example.com;

    root /var/www/admin-web/dist;
    index index.html;

    # SPA fallback
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API 代理到后端
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}

server {
    listen 80;
    server_name client.example.com;

    root /var/www/client-web/dist;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

## 生产环境

### 上线检查清单

- [ ] SSL 证书已配置（Let's Encrypt 或商业证书）
- [ ] 防火墙仅开放 80/443 端口
- [ ] 数据库备份策略已就绪（每日全量 + binlog 增量）
- [ ] `app.auth.mock-enabled` 设置为 `false`（生产环境禁止 Mock 鉴权）
- [ ] JWT secret 已替换为强随机字符串（`ADMIN_JWT_SECRET` / `CLIENT_JWT_SECRET`）
- [ ] 数据库密码已替换为生产凭据，不使用开发环境的账号密码
- [ ] 监控告警已配置（Prometheus / Grafana 或云监控）
- [ ] 日志收集已配置（ELK / Loki 或云日志服务）
- [ ] `springdoc.swagger-ui.enabled` 建议关闭或限制内网访问
- [ ] 压测通过，资源规格满足预期 QPS

### 环境变量参考

生产环境通过环境变量注入敏感配置：

| 环境变量 | 说明 | 示例 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | 激活的 Spring Profile | `prod` |
| `SPRING_DATASOURCE_URL` | 数据库 JDBC URL | `jdbc:mysql://...` |
| `SPRING_DATASOURCE_USERNAME` | 数据库用户名 | `app_user` |
| `SPRING_DATASOURCE_PASSWORD` | 数据库密码 | `***` |
| `APP_AUTH_MOCK` | 是否启用 Mock 鉴权 | `false` |
| `ADMIN_JWT_SECRET` | 管理后台 JWT 密钥 | `***` |
| `CLIENT_JWT_SECRET` | C 端 JWT 密钥 | `***` |
| `SERVER_PORT` | 服务端口 | `8080` |

### 数据库迁移策略

项目使用 **Flyway** 管理数据库版本：

- 迁移脚本位置：`backend/src/main/resources/db/migration/`
- 命名约定：`V{version}__{description}.sql`，例如 `V1__init_schema.sql`
- 新迁移脚本需按版本号递增

**生产环境迁移流程：**

1. 先在测试环境执行迁移，验证通过
2. 生产部署时 Flyway 自动按序执行未应用的迁移
3. **不要**手动修改已执行过的迁移脚本（Flyway 通过 checksum 检测变更）
4. 需要修改已有表结构时，创建新的迁移脚本（`ALTER TABLE` 等）
5. 回滚方案：通过新的迁移脚本反向操作，而非删除已有脚本

**启动参数控制：**

```bash
# 禁用自动迁移（需要手动执行）
java -jar app.jar --spring.flyway.enabled=false

# 允许迁移（默认）
java -jar app.jar --spring.flyway.enabled=true

# 从已有数据库开始（有表但无 flyway_schema_history）
java -jar app.jar --spring.flyway.baseline-on-migrate=true
```

## 常见问题

### 1. 后端启动报 "Access denied for user"

**原因：** 数据库连接信息配置错误。

**解决：**
- 检查 `application-dev.yml` 中的 `spring.datasource.url`、`username`、`password`
- 确认 MySQL 允许该用户从当前 IP 连接
- 确认数据库 `marketing_task_platform` 已创建

### 2. Flyway 迁移失败 (checksum mismatch)

**原因：** 某个已应用的迁移脚本被修改了内容。

**解决：**
- 如果是在开发环境：删除数据库，重建，让 Flyway 从头执行
- 如果是在测试/生产环境：不要修改已有脚本，新建一个脚本做变更
- 紧急情况（仅开发环境）：执行 `DROP TABLE flyway_schema_history` 后重启

### 3. pnpm install 报错或在 admin-web/node_modules 中有 .ignored 目录

**原因：** 之前使用 npm 安装的依赖与 pnpm 冲突。

**解决：**
```bash
# 清理所有 node_modules 后重新安装
rm -rf node_modules admin-web/node_modules client-web/node_modules
pnpm install
```

### 4. 前端构建时报 TypeScript 类型错误 (vue-tsc)

**原因：** 生成的 `schema.d.ts` 过期或后端 API 变更导致类型不匹配。

**解决：**
```bash
# 重新生成 API 类型
./scripts/generate-api-types.sh
# 或在各前端项目中：
npm --prefix admin-web run generate-api
npm --prefix client-web run generate-api
```

### 5. 前端开发时 API 请求 404

**原因：** Vite 开发服务器默认代理 `/api` 到后端，但后端未启动或端口不对。

**解决：**
- 确保后端在 `http://localhost:8080` 运行
- 检查 `vite.config.ts` 中的 proxy 配置
- 对于 client-web，确认端口配置（默认 5174，proxy 需指向 8080）

### 6. 测试失败 "Could not resolve placeholder"

**原因：** 测试中引用了一些未在 `application-test.yml` 中定义的配置项。

**解决：**
- 检查 `application-test.yml` 是否包含所有必需的配置
- 测试运行时会自动激活 `test` profile，使用 `@ActiveProfiles("test")`
- 如果你的测试需要特定配置，在 `backend/src/test/resources/application-test.yml` 中添加

### 7. CORS 错误

**原因：** 前端开发服务器（不同端口）请求后端 API 时跨域。

**解决：**
- 开发环境：Vite 已配置 proxy，确保请求通过 proxy 转发
- Nginx 环境：确保前端请求走 Nginx `/api/` 代理，不会跨域
- 如需后端直接处理 CORS，添加 `@CrossOrigin` 注解或全局 CORS 配置
