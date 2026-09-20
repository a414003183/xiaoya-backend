package net.zentao.doc.infra;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.zentao.doc.domain.DocAcl;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 白名单/账号集合的 JSON 文本编解码（doc 卡 §3.1/§3.2：whitelist、editors、readers、notify_accounts、files）。
 *
 * <p>落库格式是紧凑 JSON，且 <b>accounts 与 groupIds 都写成字符串</b>：
 * 文档列表的 ACL 谓词要在 SQL 里按 `%"值"%` 做可移植 LIKE（MySQL 与 H2 无共同 JSON 函数），
 * 只有引号能界定元素边界——数值元素（[3] / [3,5]）无法被单条 LIKE 精确命中。
 * 对外载荷不受影响：读侧强转回 Long，响应仍是整数数组（contract：DocAclPayload.groupIds: integer[]）。
 */
@Component
public class DocAclJson {

  private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};

  private final JsonMapper jsonMapper;

  public DocAclJson(JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  public String writeAcl(DocAcl acl) {
    DocAcl value = acl == null ? DocAcl.EMPTY : acl;
    if (value.accounts().isEmpty() && value.groupIds().isEmpty()) {
      return null;
    }
    Map<String, List<String>> body = new LinkedHashMap<>();
    body.put("accounts", value.accounts());
    body.put("groupIds", value.groupIds().stream().map(String::valueOf).toList());
    return jsonMapper.writeValueAsString(body);
  }

  public DocAcl readAcl(String json) {
    if (json == null || json.isBlank()) {
      return DocAcl.EMPTY;
    }
    JsonNode root = jsonMapper.readTree(json);
    List<String> accounts = new ArrayList<>();
    for (JsonNode node : root.path("accounts")) {
      accounts.add(node.asText());
    }
    List<Long> groupIds = new ArrayList<>();
    for (JsonNode node : root.path("groupIds")) {
      groupIds.add(node.asLong());
    }
    return new DocAcl(accounts, groupIds);
  }

  public String writeStrings(List<String> values) {
    return values == null || values.isEmpty() ? null : jsonMapper.writeValueAsString(values);
  }

  public List<String> readStrings(String json) {
    return json == null || json.isBlank() ? List.of() : jsonMapper.readValue(json, STRINGS);
  }

  public String writeIds(List<Long> ids) {
    return ids == null || ids.isEmpty() ? null : jsonMapper.writeValueAsString(ids);
  }

  /** files 列：兼容历史上的字符串数组写法，统一强转 Long。 */
  public List<Long> readIds(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    if (json.indexOf('"') >= 0) {
      return readStrings(json).stream().map(Long::valueOf).toList();
    }
    JsonNode root = jsonMapper.readTree(json);
    List<Long> ids = new ArrayList<>();
    for (JsonNode node : root) {
      ids.add(node.asLong());
    }
    return ids;
  }
}
