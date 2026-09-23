# 小雅管理后台 · 后端（xiaoya-backend）

产品 → 需求 → 项目/执行 → 任务 → 测试（Bug/用例）闭环，外加文档、报表、个人工作台的现代化项目管理平台后端。

- Java 21 + Spring Boot 4.1 + MyBatis-Flex + Flyway，单数据库（MySQL 8 / 开发态 H2）、零中间件
- DDD 四层：`api`（跨域契约）→ `app`（用例）→ `domain`（纯领域）→ `infra`（持久化/Web），ArchUnit 机器强制分层
- 契约先行：`contract/openapi.yaml` 是 API 唯一真源，运行时可导出比对
- 前端仓库：[xiaoya-frontend](https://github.com/a414003183/xiaoya-frontend)

## 环境要求

| 依赖 | 版本 |
|---|---|
| JDK | 21+（开发/CI 用 25） |
| MySQL | 8.x（仅生产；开发默认 H2 内存库，零依赖起服） |

## 快速开始

```bash
# 开发态（H2 内存库，含种子账号 admin / admin123）
mvn -f backend spring-boot:run

# 测试（单测打 H2；IT 走 Testcontainers MySQL，需本机 Docker）
mvn -f backend verify -Pit
```

服务起在 `http://localhost:8081`，OpenAPI 文档 `/swagger-ui.html`。

## 生产部署

```bash
export MYSQL_URL='jdbc:mysql://<db-host>:3306/zentao?useSSL=false&allowPublicKeyRetrieval=true'
export MYSQL_USER=zentao
export MYSQL_PASSWORD=***

mvn -f backend -DskipTests package
java -jar backend/target/zentao.jar --spring.profiles.active=prod
```

- 首次启动 Flyway 自动建表并写入内置 admin（初始口令随机生成并打印一次，或显式配置；**登录后立即改密**）
- 升级：替换 jar 重启，Flyway 自动增量迁移
- 附件存储在工作目录 `data/files/`，**该目录需持久化**

## 目录

```
backend/    Spring Boot 应用（src/main/java/net/zentao 下按域分包，每域 api/app/domain/infra 四层）
contract/   openapi.yaml —— 前后端共享的 API 唯一真源
```

## 许可与来源

本仓库是禅道（ZenTao）开源版管理后台的独立重写实现，上游为 <https://gitee.com/wwccss/zentaopms>，
以上游双授权中的 **AGPL-3.0** 发布。衍生范围、来源说明与商标声明见 [NOTICE.md](NOTICE.md)，许可全文见 [LICENSE](LICENSE)。
