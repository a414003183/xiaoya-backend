/**
 * Java 迁移落点之二：<b>Spring {@code @Component} Bean 注入</b>（T65 / AUDIT DB-19）。
 *
 * <p><b>注册机制</b>：本包的迁移类是 Spring 组件（{@code @Component}），由 Spring Boot 的 Flyway
 * 自动装配把容器里所有 {@code JavaMigration} Bean 交给 Flyway 执行——因此能用 {@code @Value} 注入
 * 配置、拿 Bean（V22 要读 {@code zentao.admin.initial-password} 与 BCrypt 编码器）。
 *
 * <p><b>为什么包名刻意错开 {@code db.migration}</b>：那个包是 Flyway 的扫描位置（缺省
 * {@code classpath:db/migration}），落在那里的 Java 迁移会被扫描器发现；本包的 {@code @Component}
 * 迁移如果也放进 {@code db.migration}，同一版本会被扫描与 Bean 注入<b>重复登记</b>，Flyway 直接拒绝启动。
 * 两个落点并存是机制差异所致，不是两套风格——选哪个只看「要不要 Spring 容器能力」。
 *
 * <p><b>适用场景</b>：迁移逻辑依赖运行时配置或 Spring Bean（口令哈希编码器、外置配置值）；
 * 纯 JDBC/纯 SQL 干得了的放 {@code db.migration}（包扫描落点，那边有完整的选型指引）。
 *
 * <p><b>命名与硬约束</b>与另一落点完全一致：类名 {@code V{n}__snake_desc}（版本号取号制，
 * 在 {@code docs/plan/STATE.md} 登记）、已执行迁移冻结不改、拼出的 SQL 双方言合法（MySQL 8 +
 * H2 {@code MODE=MySQL}）、Java 迁移建的表/列不得被 SQL 迁移引用（SQL 链自洽，旧库导入 CLI 只跑 SQL）。
 * 看护门禁：{@code node tools/contract-check/check-migration-sql-chain.mjs}；
 * 细则见 {@code docs/plan/CONVENTIONS.md} §2.4 与 {@code docs/plan/architecture/ADR-011-data-policy.md}。
 */
package net.zentao.db.migration;
