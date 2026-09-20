package net.zentao;

import com.mybatisflex.core.FlexGlobalConfig;
import java.lang.reflect.Field;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * 测试基座的 MyBatis-Flex Row API（{@code Db.xxx}）绑定工具。
 *
 * <p>背景：Flex 的 Row API 走进程级静态状态——{@code Db.invoker()} 首次调用时用
 * {@code FlexGlobalConfig.getDefaultConfig().getSqlSessionFactory()} 造出并缓存一个静态 invoker（实测 1.11.8）。
 * 单个 Spring 上下文（生产/单测常态）无碍；但同一个测试 JVM 里既有 H2 上下文又有 Testcontainers MySQL 上下文时
 * （`mvn test -Dtest='*Product*'` 这类点名跑法），静态 invoker 会指向前一个上下文的数据源，
 * 造成「同一请求里 mapper 走 MySQL、Row API 走 H2」的错位。
 *
 * <p>故每个用例前把静态配置与缓存 invoker 重置到本上下文的 SqlSessionFactory。这是测试基建，
 * 生产（单数据源）不受影响；根治路径是把 Row API 调用点迁到注入式 Mapper（P3+ 按域顺手做）。
 */
public final class FlexRowApiSupport {

  private FlexRowApiSupport() {}

  public static void bind(SqlSessionFactory sqlSessionFactory) {
    FlexGlobalConfig config = FlexGlobalConfig.getDefaultConfig();
    config.setConfiguration(sqlSessionFactory.getConfiguration());
    config.setSqlSessionFactory(sqlSessionFactory);
    clearCachedInvoker();
  }

  private static void clearCachedInvoker() {
    try {
      Field field = com.mybatisflex.core.row.Db.class.getDeclaredField("defaultRowMapperInvoker");
      field.setAccessible(true);
      field.set(null, null);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("无法重置 MyBatis-Flex Row API 静态绑定", e);
    }
  }
}
