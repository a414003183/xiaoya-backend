package net.zentao.org.app;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import net.zentao.org.domain.Group;
import net.zentao.org.domain.GroupAcl;
import net.zentao.org.domain.GroupRepository;
import net.zentao.platform.error.ApiException;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 创建权限组（org 卡 §5：name 全库唯一 → 42201）。 */
@Component
public class CreateGroupHandler {

  private final GroupRepository repository;

  public CreateGroupHandler(GroupRepository repository) {
    this.repository = repository;
  }

  public record GroupCreateRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name, String description) {}

  @Transactional
  public Group handle(String actor, GroupCreateRequest command) {
    requireName(command.name());
    if (repository.findByName(command.name()).isPresent()) {
      throw ApiException.validation(Map.of("name", "duplicate"));
    }
    return repository.insert(new Group(0, command.name(), command.description(), GroupAcl.EMPTY, actor, 0));
  }

  static void requireName(String name) {
    if (name == null || name.isBlank() || name.length() > 60) {
      throw ApiException.validation(Map.of("name", "size"));
    }
  }

  /** GroupView（contract：id/name/description/acl/memberCount/privilegeCount/审计/lockVersion）。 */
  public record GroupView(
      long id, String name, String description, GroupAcl acl, long memberCount, long privilegeCount,
      String createdBy, Instant createdAt, String updatedBy, Instant updatedAt, int lockVersion) {}
}
