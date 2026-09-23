package net.zentao.platform.langimport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;
import net.zentao.H2TestSupport;
import net.zentao.platform.session.SessionPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * T57 / BE-03：**Excel 解析必须在事务之外**（解析期间不占 DB 连接——并发上传不会打满连接池）。
 *
 * <p>这条不变量是结构性的：解析/校验在 {@link LangImportService}，落库在 {@link ApplyLangImportHandler}，
 * 事务只包后者。行为面（失败仍留痕、覆盖层与记录同事务）由 {@code LangImportApiTest} 的既有用例看护；
 * 这里直接钉注解布局——把 {@code @Transactional} 加回解析方法，本用例即红。
 */
class LangImportTransactionBoundaryTest extends H2TestSupport {

  @Autowired LangImportService service;

  @Autowired ApplyLangImportHandler applyHandler;

  @Test
  @DisplayName("解析面不在事务里；落库面两个入口都在")
  void parsingStaysOutOfTransaction() throws Exception {
    Method parse = target(service).getMethod("importFile", SessionPrincipal.class, String.class, long.class,
        java.io.InputStream.class);
    assertFalse(isTransactional(parse),
        "importFile 不得带 @Transactional：解析全程占着连接（BE-03；落库已交给 ApplyLangImportHandler）");

    Method success = target(applyHandler).getMethod("recordSuccess", SessionPrincipal.class, String.class, List.class);
    Method failure = target(applyHandler).getMethod("recordFailure", SessionPrincipal.class, String.class,
        int.class, int.class, String.class);
    assertTrue(isTransactional(success), "成功落库（覆盖层 + 记录）必须原子");
    assertTrue(isTransactional(failure), "失败留痕也要自己一条事务（否则与无写路径混在一起）");
  }

  private static Class<?> target(Object bean) {
    return AopUtils.getTargetClass(bean);
  }

  private static boolean isTransactional(Method method) {
    return method.isAnnotationPresent(Transactional.class);
  }
}
