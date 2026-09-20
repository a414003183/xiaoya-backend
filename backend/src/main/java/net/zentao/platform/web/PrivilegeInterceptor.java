package net.zentao.platform.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.rbac.PrivilegeChecker;
import net.zentao.platform.rbac.RequirePrivilege;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.session.SessionResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * @RequirePrivilege 拦截（platform 卡 §7.1）：未登录 40101（resolve 抛出），无码 40301。
 * 只对标注了权限码的端点生效；登录等匿名端点不经过此判定。
 */
@Component
public class PrivilegeInterceptor implements HandlerInterceptor {

  private final SessionResolver resolver;
  private final PrivilegeChecker checker;

  public PrivilegeInterceptor(SessionResolver resolver, PrivilegeChecker checker) {
    this.resolver = resolver;
    this.checker = checker;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!(handler instanceof HandlerMethod handlerMethod)) {
      return true;
    }
    RequirePrivilege annotation = handlerMethod.getMethodAnnotation(RequirePrivilege.class);
    if (annotation == null) {
      annotation = handlerMethod.getBeanType().getAnnotation(RequirePrivilege.class);
    }
    if (annotation == null) {
      return true;
    }
    SessionPrincipal principal = resolver.resolve(request);
    if (!checker.hasPrivilege(principal, annotation.value())) {
      throw ApiException.forbidden("缺少权限码 " + annotation.value() + "。");
    }
    return true;
  }
}
