package net.zentao.platform.web;

import io.swagger.v3.oas.annotations.Operation;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.meta.MetaRegistry;
import net.zentao.platform.meta.MetaView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GET /meta/{domain}（platform 卡 §5；未注册域 → 40401）。 */
@RestController
@RequestMapping("/api/v1")
public class MetaController {

  private final MetaRegistry registry;

  public MetaController(MetaRegistry registry) {
    this.registry = registry;
  }

  @GetMapping("/meta/{domain}")
  @Operation(operationId = "getMeta")
  public DataEnvelope<MetaView> getMeta(@PathVariable String domain, @RequestParam(required = false) String form) {
    return DataEnvelope.of(registry.get(domain).orElseThrow(() -> ApiException.notFound("域 " + domain)));
  }
}
