package net.zentao.platform.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** MVC 拦截器注册：@RequirePrivilege 判定挂到全部 /api 路由（未标注端点不受影响）。 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

  private final PrivilegeInterceptor privilegeInterceptor;

  public WebMvcConfig(PrivilegeInterceptor privilegeInterceptor) {
    this.privilegeInterceptor = privilegeInterceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(privilegeInterceptor).addPathPatterns("/api/**");
  }
}
