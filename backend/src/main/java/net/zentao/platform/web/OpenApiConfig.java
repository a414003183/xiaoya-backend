package net.zentao.platform.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 运行时文档的头信息与契约同源（T14）：换掉 springdoc 的缺省值
 * （`OpenAPI definition` / `v0` / `servers=[http://localhost:PORT]`）。
 *
 * <p>servers 必须指到 `/api/v1`：Swagger UI 的 try-it-out 按 servers 拼 URL，缺省会把
 * `GET /api/v1/session` 发成 `GET /session` 而 404。版本号与 `contract/openapi.yaml`
 * 的 `info.version` 同步改（契约是唯一真源，此处只是让它显示出来）。
 */
@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI zentaoOpenApi() {
    return new OpenAPI()
        .info(new Info()
            .title("Zentao API")
            .version("0.2.0")
            .description("自研项目管理平台 API。所有时间 ISO-8601 带时区；信封见 components。"
                + "列表 DSL：page/limit/sort/filters[x]/q；游标时间线：limit/beforeId。"))
        .servers(List.of(new Server().url("/api/v1").description("唯一入口（契约 contract/openapi.yaml 的 servers）")));
  }
}
