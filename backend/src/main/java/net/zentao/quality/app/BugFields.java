package net.zentao.quality.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.zentao.org.api.AccountApi;
import net.zentao.platform.error.ApiException;
import net.zentao.quality.domain.Bug;
import net.zentao.quality.domain.BugRepository;

/** Bug 字段校验（quality 卡 §3.1 校验/取值列；create 与 update 同源，null = 不改不校验）。 */
final class BugFields {

  static final Set<String> TYPES =
      Set.of("codeerror", "config", "install", "security", "performance", "standard", "automation",
          "designdefect", "others");
  static final Set<String> RESOLUTIONS =
      Set.of("bydesign", "duplicate", "external", "fixed", "notrepro", "postponed", "willnotfix", "tostory");

  private BugFields() {}

  static void validate(String title, String keywords, Integer severity, Integer priority, String type,
      String os, String browser, String openedBuilds, String resolvedBuild, String assignee,
      List<String> notifyAccounts, AccountApi accountApi) {
    Map<String, String> errors = new LinkedHashMap<>();
    if (title != null) {
      String trimmed = title.trim();
      if (trimmed.isEmpty()) {
        errors.put("title", "required");
      } else if (trimmed.length() > 255) {
        errors.put("title", "maxLength");
      }
    }
    if (keywords != null && keywords.length() > 255) {
      errors.put("keywords", "maxLength");
    }
    if (severity != null && (severity < 1 || severity > 4)) {
      errors.put("severity", "invalidRange");
    }
    if (priority != null && (priority < 1 || priority > 4)) {
      errors.put("priority", "invalidRange");
    }
    if (type != null && !TYPES.contains(type)) {
      errors.put("type", "invalid");
    }
    if (openedBuilds != null && openedBuilds.length() > 255) {
      errors.put("openedBuilds", "maxLength");
    }
    if (resolvedBuild != null && resolvedBuild.length() > 90) {
      errors.put("resolvedBuild", "maxLength");
    }
    List<String> referenced = new ArrayList<>();
    referenced.add(assignee);
    if (notifyAccounts != null) {
      referenced.addAll(notifyAccounts);
    }
    List<String> missing = accountApi.missingAccounts(referenced);
    if (!missing.isEmpty()) {
      if (assignee != null && missing.contains(assignee)) {
        errors.put("assignee", "notFound");
      }
      if (notifyAccounts != null && notifyAccounts.stream().anyMatch(missing::contains)) {
        errors.put("notifyAccounts", "notFound");
      }
    }
    if (!errors.isEmpty()) {
      throw ApiException.validation(errors);
    }
  }

  /** relatedBugIds：同产品未删 Bug（§3.1）。 */
  static void validateRelatedBugs(long productId, List<Long> relatedBugIds, BugRepository repository) {
    if (relatedBugIds == null || relatedBugIds.isEmpty()) {
      return;
    }
    List<Long> distinct = relatedBugIds.stream().distinct().toList();
    List<Bug> found = repository.findActiveByIds(distinct);
    if (found.size() != distinct.size() || found.stream().anyMatch(bug -> bug.productId() != productId)) {
      throw ApiException.validation(Map.of("relatedBugIds", "crossProduct"));
    }
  }

  /** resolve 请求体：resolution 必填且在枚举内；=duplicate 需同产品未删 duplicateOfId；=fixed 需 resolvedBuild（§4.1/§8）。 */
  static void validateResolve(String resolution, String resolvedBuild, Long duplicateOfId, long productId,
      BugRepository repository) {
    if (resolution == null || !RESOLUTIONS.contains(resolution)) {
      throw ApiException.validation(Map.of("resolution", "invalid"));
    }
    if ("duplicate".equals(resolution)) {
      if (duplicateOfId == null || duplicateOfId == 0) {
        throw ApiException.validation(Map.of("duplicateOfId", "required"));
      }
      Bug target = repository.findActiveById(duplicateOfId).orElse(null);
      if (target == null || target.productId() != productId) {
        throw ApiException.validation(Map.of("duplicateOfId", "notFound"));
      }
    }
    if ("fixed".equals(resolution) && (resolvedBuild == null || resolvedBuild.isBlank())) {
      throw ApiException.validation(Map.of("resolvedBuild", "required"));
    }
  }

  /** assign 请求体：assignee 必填且账号存在。 */
  static void validateAssignee(String assignee, AccountApi accountApi) {
    if (assignee == null || assignee.isBlank()) {
      throw ApiException.validation(Map.of("assignee", "required"));
    }
    if (!accountApi.missingAccounts(List.of(assignee)).isEmpty()) {
      throw ApiException.validation(Map.of("assignee", "notFound"));
    }
  }
}
