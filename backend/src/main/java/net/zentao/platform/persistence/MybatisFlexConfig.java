package net.zentao.platform.persistence;

import com.mybatisflex.core.FlexGlobalConfig;
import com.mybatisflex.core.mybatis.FlexConfiguration;
import com.mybatisflex.spring.FlexSqlSessionFactoryBean;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * MyBatis-Flex × Spring Boot 4 手动装配（phase-0 T-2 预案，已登记 STATE.md）。
 * 官方 starter 的自动配置走 spring.factories 注册（Boot 4 已废弃该机制），boot3-starter 在 Boot 4 下静默失效，
 * 且其装配链从不初始化 Row API 所需的 FlexGlobalConfig.sqlSessionFactory → 此处全量手动装配。
 *
 * <p>事务接线（P4 T-1 实测修正）：FlexSqlSessionFactoryBean 给 environment 强装 FlexTransactionFactory，
 * 且把 DataSource 包成 FlexDataSource（与 Spring bean 不同实例）——事务同步按 DataSource 实例绑定，
 * 两者叠加使 @Transactional 对 mapper 完全失效（P0–P3 全部写路径实为逐句自动提交）。
 * 修正两步：① environment 换 SpringManagedTransactionFactory（保留 FlexDataSource，FlexMapperProxy 强制要求）；
 * ② 事务管理器绑定 environment 的 FlexDataSource——与 SpringManagedTransaction 取连接的实例一致，
 * 事务同步由此打通。@Bean 覆盖 Boot 自动装配的 JdbcTransactionManager(ConditionalOnMissingBean 让位)。
 */
@Configuration
@MapperScan(basePackages = "net.zentao", annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class MybatisFlexConfig {

  @Bean
  public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
    FlexSqlSessionFactoryBean factoryBean = new FlexSqlSessionFactoryBean();
    factoryBean.setDataSource(dataSource);
    factoryBean.setConfiguration(new FlexConfiguration());
    factoryBean.afterPropertiesSet();
    SqlSessionFactory factory = factoryBean.getObject();
    var environment = factory.getConfiguration().getEnvironment();
    factory.getConfiguration().setEnvironment(
        new org.apache.ibatis.mapping.Environment(environment.getId(),
            new SpringManagedTransactionFactory(), environment.getDataSource()));
    FlexGlobalConfig globalConfig = FlexGlobalConfig.getDefaultConfig();
    globalConfig.setConfiguration(factory.getConfiguration());
    globalConfig.setSqlSessionFactory(factory);
    return factory;
  }

  /** 事务管理器绑定 FlexDataSource（与 mapper 会话取连接的实例一致，事务同步才可达）。 */
  @Bean
  public PlatformTransactionManager transactionManager(SqlSessionFactory sqlSessionFactory) {
    return new JdbcTransactionManager(sqlSessionFactory.getConfiguration().getEnvironment().getDataSource());
  }
}
