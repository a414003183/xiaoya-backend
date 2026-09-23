/**
 * Java 迁移落点之一：<b>Flyway 包扫描</b>（T65 / AUDIT DB-19）。
 *
 * <p><b>注册机制</b>：Spring Boot 的 Flyway 自动装配默认扫描 {@code classpath:db/migration}
 * （{@code application.yml} 里 {@code spring.flyway} 只有 {@code enabled: true}，locations 用缺省值），
 * 该位置下的 SQL 文件与 Java 类一起被 Flyway 的扫描器发现——本包类名即版本号
 * （{@code V{n}__snake_desc}，{@code BaseJavaMigration#getVersion} 取自类名），**无需任何注解**。
 *
 * <p><b>适用场景</b>：不需要 Spring 容器能力的迁移——纯 SQL 干不了、但 plain JDBC 干得了的事
 * （算 BCrypt 哈希、解析 JSON 挑值、按行搬迁数据）。需要 Spring 配置/Bean 的迁移放另一个落点
 * {@code net.zentao.db.migration}（{@code @Component} 机制），那里解释了为什么两个包必须分开。
 *
 * <p><b>新 Java 迁移放哪、怎么命名</b>：
 * <ul>
 *   <li>不需要 Spring Bean/配置 → 放本包；需要 → 放 {@code net.zentao.db.migration} 并加 {@code @Component}；</li>
 *   <li>类名 {@code V{n}__snake_desc}（如 {@code V38__user_role_reverse_index}），版本号取号制
 *       （在 {@code docs/plan/STATE.md} 登记，SQL 与 Java 迁移共用同一序列）；</li>
 *   <li>已执行的迁移一律冻结不改（Flyway checksum 校验）——事后修正只能是「新版本迁移在其后修」。</li>
 * </ul>
 *
 * <p><b>两条硬约束</b>（细则见 {@code docs/plan/CONVENTIONS.md} §2.4）：
 * <ul>
 *   <li><b>双方言合法</b>：本包拼出的 DDL/DML 同样要在 MySQL 8 与 H2({@code MODE=MySQL}) 都合法
 *       （生产/迁移工具走 MySQL、全部测试走 H2；只一边合法 = 测试绿、上线断）；</li>
 *   <li><b>SQL 链自洽</b>：本包建的表/列（如 {@code role}、{@code user_role}、{@code role_priv}）<b>不得被
 *       {@code resources/db/migration} 的 SQL 迁移引用</b>——旧库导入 CLI（{@code tools/migration}）的 Flyway
 *       只跑 SQL，Java 迁移在那边无法编译执行（只补一行历史标记，见 {@code Main#markJavaSeedMigrated}），
 *       引用即在那一版断链。要给这类表加索引/列，写成 Java 迁移排在建表版本之后（T53 的 V38 就是这么来的）。
 *       看护门禁：{@code node tools/contract-check/check-migration-sql-chain.mjs}。
 * </ul>
 */
package db.migration;
