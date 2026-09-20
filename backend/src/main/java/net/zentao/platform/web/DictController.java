package net.zentao.platform.web;

import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.Map;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.meta.DictRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /dicts/{name}（platform 卡 §3.9；未注册名 → 40401）。 */
@RestController
@RequestMapping("/api/v1")
public class DictController {

  private final DictRegistry registry;

  public DictController(DictRegistry registry) {
    this.registry = registry;
  }

  @GetMapping("/dicts/{name}")
  @Operation(operationId = "getDict")
  public DataEnvelope<DictView> getDict(@PathVariable String name) {
    var provider = registry.get(name).orElseThrow(() -> ApiException.notFound("字典 " + name));
    return DataEnvelope.of(new DictView(name, provider.items()));
  }

  /** DictView（contract：{name, items}）。 */
  public record DictView(String name, List<Map<String, Object>> items) {}
}
