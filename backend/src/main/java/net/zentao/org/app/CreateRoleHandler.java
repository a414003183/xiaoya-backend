package net.zentao.org.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import net.zentao.org.domain.Role;
import net.zentao.org.domain.RoleAcl;
import net.zentao.org.domain.RoleRepository;
import net.zentao.platform.error.ApiException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 创建角色（T23：name 全库唯一 → 42201；code 可选，给了要合法且唯一）。 */
@Component
public class CreateRoleHandler {

  private final RoleRepository repository;

  public CreateRoleHandler(RoleRepository repository) {
    this.repository = repository;
  }

  public record RoleCreateRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
      @Schema(description = "可选稳定标识（小写字母/数字/连字符）；留空表示这个角色不对外暴露码") String code,
      String description) {}

  @Transactional
  public Role handle(String actor, RoleCreateRequest command) {
    requireName(command.name());
    String code = normalizeCode(command.code());
    if (repository.findByName(command.name().trim()).isPresent()) {
      throw ApiException.validation(Map.of("name", "duplicate"));
    }
    if (code != null && repository.findAll().stream().anyMatch(role -> code.equals(role.code()))) {
      throw ApiException.validation(Map.of("code", "duplicate"));
    }
    return repository.insert(new Role(0, code, command.name().trim(), command.description(), RoleAcl.EMPTY, false,
        nextSort(), actor, java.time.Instant.now(), null, null, 0));
  }

  static void requireName(String name) {
    if (name == null || name.isBlank() || name.trim().length() > Role.NAME_MAX) {
      throw ApiException.validation(Map.of("name", "size"));
    }
  }

  /** 空 = 不要码；非空要合法（与旧账号角色码同一口径）。 */
  static String normalizeCode(String code) {
    if (code == null || code.isBlank()) {
      return null;
    }
    String value = code.trim();
    if (!Role.validCode(value)) {
      throw ApiException.validation(Map.of("code", "pattern"));
    }
    return value;
  }

  private int nextSort() {
    return repository.findAll().stream().mapToInt(Role::sort).max().orElse(0) + 10;
  }
}
