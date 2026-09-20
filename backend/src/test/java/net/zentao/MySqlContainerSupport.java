package net.zentao;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * IT 基座（01 §5）：Testcontainers MySQL 8.4 LTS 真实库 + Flyway 迁移，替换单测用的 H2。
 * 类名不匹配 failsafe 的 {@code *IT} 模式，故只作为基类被继承。
 *
 * <p>Row API 的静态绑定按上下文重置，见 {@link FlexRowApiSupport}（同 JVM 混跑 H2/MySQL 的错位防护）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class MySqlContainerSupport {

  private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
      .withDatabaseName("zentao")
      .withUsername("zentao")
      .withPassword("zentao");

  @Autowired
  private SqlSessionFactory sqlSessionFactory;

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    if (!MYSQL.isRunning()) {
      MYSQL.start();
    }
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
  }

  @BeforeEach
  void bindFlexRowApi() {
    FlexRowApiSupport.bind(sqlSessionFactory);
  }
}
