package net.zentao;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 单测基座：H2(MySQL mode) 上下文 + 把 MyBatis-Flex Row API 绑回本上下文
 * （与 {@link MySqlContainerSupport} 对称，保证同 JVM 混跑 H2/MySQL 时不错位，见 {@link FlexRowApiSupport}）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class H2TestSupport {

  @Autowired
  private SqlSessionFactory sqlSessionFactory;

  @BeforeEach
  void bindFlexRowApi() {
    FlexRowApiSupport.bind(sqlSessionFactory);
  }
}
