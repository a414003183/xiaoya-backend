package net.zentao.product.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.zentao.org.api.AccountApi;
import net.zentao.platform.error.ApiException;

/** 产品字段校验（product 卡 §3.1 校验/取值列；create 与 update 同源，null = 不改不校验）。 */
final class ProductFields {

  static final Set<String> TYPES = Set.of("normal", "branch", "platform");
  static final Set<String> ACLS = Set.of("public", "private", "custom");
  static final int WHITELIST_MAX = 100;

  private ProductFields() {}

  static void validate(String name, String code, String type, String acl, List<String> whitelist,
      String po, String qd, String rd, AccountApi accountApi) {
    Map<String, String> errors = new LinkedHashMap<>();
    if (name != null) {
      String trimmed = name.trim();
      if (trimmed.isEmpty()) {
        errors.put("name", "required");
      } else if (trimmed.length() > 90) {
        errors.put("name", "maxLength");
      }
    }
    if (code != null && code.length() > 45) {
      errors.put("code", "maxLength");
    }
    if (type != null && !TYPES.contains(type)) {
      errors.put("type", "invalid");
    }
    if (acl != null && !ACLS.contains(acl)) {
      errors.put("acl", "invalid");
    }
    if (whitelist != null && whitelist.size() > WHITELIST_MAX) {
      errors.put("whitelist", "tooMany");
    }
    List<String> referenced = new ArrayList<>();
    referenced.add(po);
    referenced.add(qd);
    referenced.add(rd);
    if (whitelist != null) {
      referenced.addAll(whitelist);
    }
    List<String> missing = accountApi.missingAccounts(referenced);
    if (!missing.isEmpty()) {
      if (po != null && missing.contains(po)) {
        errors.put("po", "notFound");
      }
      if (qd != null && missing.contains(qd)) {
        errors.put("qd", "notFound");
      }
      if (rd != null && missing.contains(rd)) {
        errors.put("rd", "notFound");
      }
      if (whitelist != null && whitelist.stream().anyMatch(missing::contains)) {
        errors.put("whitelist", "notFound");
      }
    }
    if (!errors.isEmpty()) {
      throw ApiException.validation(errors);
    }
  }
}
