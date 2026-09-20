package net.zentao.doc.app;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.zentao.doc.domain.DocAcl;
import net.zentao.org.api.AccountApi;
import net.zentao.platform.error.ApiException;

/** doc 字段校验（doc 卡 §3.1/§3.2/§3.3 校验列；create 与 update 同源，null = 不改不校验）。 */
final class DocFields {

  static final Set<String> SPACE_TYPES = Set.of("product", "project", "execution", "custom", "mine");
  static final Set<String> SPACE_ACLS = Set.of("open", "default", "private");
  static final Set<String> SPACE_DOC_SORTS = Set.of("id_asc", "id_desc");
  static final Set<String> DOC_TYPES = Set.of("markdown", "html");
  static final Set<String> DOC_STATUSES = Set.of("draft", "published");
  static final Set<String> DOC_ACLS = Set.of("open", "private");
  static final int NAME_MAX = 60;
  static final int TITLE_MAX = 255;
  static final int KEYWORDS_MAX = 255;
  static final int ACL_LIST_MAX = 50;
  static final int NOTIFY_MAX = 50;

  private DocFields() {}

  static Map<String, String> errors() {
    return new LinkedHashMap<>();
  }

  static void reject(Map<String, String> errors) {
    if (!errors.isEmpty()) {
      throw ApiException.validation(errors);
    }
  }

  /** 名称必填且 1–60（库名/目录名同规）。 */
  static void requireName(Map<String, String> errors, String field, String value) {
    if (value == null || value.trim().isEmpty()) {
      errors.put(field, "required");
    } else if (value.trim().length() > NAME_MAX) {
      errors.put(field, "maxLength");
    }
  }

  /** 标题必填且 1–255。 */
  static void requireTitle(Map<String, String> errors, String value) {
    if (value == null || value.trim().isEmpty()) {
      errors.put("title", "required");
    } else if (value.trim().length() > TITLE_MAX) {
      errors.put("title", "maxLength");
    }
  }

  static void maxLength(Map<String, String> errors, String field, String value, int max) {
    if (value != null && value.length() > max) {
      errors.put(field, "maxLength");
    }
  }

  static void oneOf(Map<String, String> errors, String field, String value, Set<String> allowed) {
    if (value != null && !allowed.contains(value)) {
      errors.put(field, "invalid");
    }
  }

  /** 白名单载荷：accounts/groupIds 各 ≤50，账号必须存在（doc 卡 §3.1/§3.2）。 */
  static void validateAcl(Map<String, String> errors, String field, DocAcl payload, AccountApi accountApi) {
    if (payload == null) {
      return;
    }
    if (payload.accounts().size() > ACL_LIST_MAX || payload.groupIds().size() > ACL_LIST_MAX) {
      errors.put(field, "tooMany");
      return;
    }
    if (!accountApi.missingAccounts(payload.accounts()).isEmpty()) {
      errors.put(field, "notFound");
    }
  }

  /** 账号集合（notifyAccounts）：≤50 且账号存在。 */
  static void validateAccounts(Map<String, String> errors, String field, List<String> accounts,
      AccountApi accountApi) {
    if (accounts == null) {
      return;
    }
    if (accounts.size() > NOTIFY_MAX) {
      errors.put(field, "tooMany");
      return;
    }
    if (!accountApi.missingAccounts(accounts).isEmpty()) {
      errors.put(field, "notFound");
    }
  }

  static void requirePresent(Map<String, String> errors, String field, Long value) {
    if (value == null || value == 0) {
      errors.put(field, "required");
    }
  }
}
