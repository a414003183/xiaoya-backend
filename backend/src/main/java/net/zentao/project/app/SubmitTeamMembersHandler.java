package net.zentao.project.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.zentao.org.api.AccountApi;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.i18n.MessageResolver;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.project.domain.TeamMember;
import net.zentao.project.domain.TeamMemberRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 团队成员全量提交（project 卡 §5/§8，旧 manageMembers 语义）：提交表即目标态，diff 增/删/改；
 * 逐项校验（account 缺失/重复/账号不存在/days 超所在项目 days → 该行 error=42201，其余行照常落库）。
 * 行值语义：role 直接取（null = 无角色）；joinDate null 时新行取当天、既有行保留原值（日期无「空」态，
 * 避免提交省略该字段即被误清）；days/hours/sort null 取 0。同内容重复提交不产生写入（幂等）。
 */
@Component
public class SubmitTeamMembersHandler {

  private static final int MAX_ROLE_LENGTH = 30;
  private static final BigDecimal MAX_DAILY_HOURS = new BigDecimal("24");
  private static final String CODE = "42201:";

  private final TeamMemberRepository repository;
  private final TeamMemberQueryService queryService;
  private final ProjectQueryService projectQueryService;
  private final AccountApi accountApi;

  private final MessageResolver messages;

  public SubmitTeamMembersHandler(TeamMemberRepository repository, TeamMemberQueryService queryService,
      ProjectQueryService projectQueryService, AccountApi accountApi,
      MessageResolver messages) {
    this.repository = repository;
    this.queryService = queryService;
    this.projectQueryService = projectQueryService;
    this.accountApi = accountApi;
    this.messages = messages;
  }

  public record TeamMemberInput(String account, String role, LocalDate joinDate, Integer days, BigDecimal hours,
      Integer sort) {}

  public record TeamMemberSubmitRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<TeamMemberInput> members) {}

  public record TeamMemberSubmitResultItem(String account, boolean ok, String error) {}

  public record TeamMemberSubmitResult(List<TeamMemberSubmitResultItem> results) {}

  @Transactional
  public TeamMemberSubmitResult handle(SessionPrincipal actor, String objectType, long objectId,
      TeamMemberSubmitRequest command) {
    projectQueryService.requireVisible(actor, objectType, objectId);
    if (command == null || command.members() == null) {
      throw ApiException.validation(Map.of("members", "required"));
    }
    int daysLimit = queryService.daysLimit(objectType, objectId);
    Set<String> missing = Set.copyOf(
        accountApi.missingAccounts(command.members().stream().map(TeamMemberInput::account).toList()));
    Map<String, TeamMember> existing = new LinkedHashMap<>();
    for (TeamMember member : repository.findActive(objectType, objectId)) {
      existing.put(member.account(), member);
    }

    Set<String> targetAccounts = new LinkedHashSet<>();
    Set<String> seen = new LinkedHashSet<>();
    List<TeamMemberSubmitResultItem> results = new ArrayList<>(command.members().size());
    for (TeamMemberInput row : command.members()) {
      String account = row.account();
      if (account == null || account.isBlank()) {
        results.add(failed(account, messages.forRequest("team.error.accountRequired")));
        continue;
      }
      targetAccounts.add(account);
      if (!seen.add(account)) {
        results.add(failed(account, messages.forRequest("team.error.accountDuplicated")));
        continue;
      }
      String error = validate(row, missing, daysLimit);
      if (error != null) {
        results.add(failed(account, error));
        continue;
      }
      apply(actor, objectType, objectId, row, account, existing.get(account));
      results.add(new TeamMemberSubmitResultItem(account, true, null));
    }
    for (TeamMember member : existing.values()) {
      if (!targetAccounts.contains(member.account())) {
        repository.softDelete(member.id(), actor.account());
      }
    }
    return new TeamMemberSubmitResult(results);
  }

  private String validate(TeamMemberInput row, Set<String> missing, int daysLimit) {
    if (missing.contains(row.account())) {
      return messages.forRequest("team.error.accountMissing");
    }
    if (row.role() != null && row.role().length() > MAX_ROLE_LENGTH) {
      return messages.forRequest("team.error.roleTooLong");
    }
    if (row.days() != null && row.days() < 0) {
      return messages.forRequest("team.error.daysNegative");
    }
    if (daysLimit >= 0 && row.days() != null && row.days() > daysLimit) {
      return messages.forRequest("team.error.daysExceedLimit");
    }
    if (row.hours() != null && (row.hours().signum() < 0 || row.hours().compareTo(MAX_DAILY_HOURS) > 0
        || row.hours().stripTrailingZeros().scale() > 1)) {
      return messages.forRequest("team.error.hoursInvalid");
    }
    return null;
  }

  private void apply(SessionPrincipal actor, String objectType, long objectId, TeamMemberInput row, String account,
      TeamMember member) {
    LocalDate joinDate = row.joinDate() != null ? row.joinDate()
        : member != null && member.joinDate() != null ? member.joinDate() : LocalDate.now();
    int days = row.days() == null ? 0 : row.days();
    BigDecimal hours = row.hours() == null ? BigDecimal.ZERO : row.hours();
    int sort = row.sort() == null ? 0 : row.sort();
    if (member == null) {
      repository.insert(new TeamMember(0, objectType, objectId, account, row.role(), joinDate, days, hours, sort,
          actor.account(), Instant.now(), null, null));
      return;
    }
    if (member.sameAs(row.role(), joinDate, days, hours, sort)) {
      return;
    }
    member.apply(row.role(), joinDate, days, hours, sort);
    member.markUpdatedBy(actor.account());
    repository.update(member);
  }

  private static TeamMemberSubmitResultItem failed(String account, String message) {
    return new TeamMemberSubmitResultItem(account, false, CODE + message);
  }
}
