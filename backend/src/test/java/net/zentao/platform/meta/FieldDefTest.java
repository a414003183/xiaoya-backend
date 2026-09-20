package net.zentao.platform.meta;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.zentao.platform.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A-05 field_def 链路：校验规则矩阵 + meta 追加 + 注册表加载排序（field_def 行由测试种子 + reload）。
 * 域侧写路径挂钩由各域补，本测试锁规则真源。
 */
@SpringBootTest
class FieldDefTest {

  /** 校验矩阵专用域（与真实域解耦，避免污染既有 meta/校验行为）。 */
  private static final String DOMAIN = "fdv";

  @Autowired
  JdbcTemplate jdbcTemplate;

  @Autowired
  FieldDefRegistry registry;

  @Autowired
  FieldDefValidator validator;

  @Autowired
  MetaRegistry metaRegistry;

  @BeforeEach
  void seedDefs() {
    jdbcTemplate.update("DELETE FROM field_def WHERE domain IN (?, 'product')", DOMAIN);
    insert(DOMAIN, "severity", "select", 1, "[{\"value\":\"high\",\"i18n\":\"x.high\"},{\"value\":\"low\",\"i18n\":\"x.low\"}]", null, 10);
    insert(DOMAIN, "labels", "multiselect", 0, "[{\"value\":\"a\",\"i18n\":\"x.a\"},{\"value\":\"b\",\"i18n\":\"x.b\"}]", null, 20);
    insert(DOMAIN, "flags", "checkbox", 0, null, null, 30);
    insert(DOMAIN, "estimate", "number", 0, null, null, 40);
    insert(DOMAIN, "deadline", "date", 0, null, null, 50);
    insert(DOMAIN, "plannedAt", "datetime", 0, null, null, 60);
    insert(DOMAIN, "note", "textarea", 0, null, null, 70);
    registry.reload();
  }

  private void insert(String domain, String key, String type, int required, String options, String visibleWhen, int sort) {
    jdbcTemplate.update(
        "INSERT INTO field_def (domain, item_key, type, required, options, visible_when, sort, created_by) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, 'test')",
        domain, key, type, required, options, visibleWhen, sort);
  }

  private void assertRejected(Map<String, Object> customFields, boolean create, String expectedField) {
    ApiException error = assertThrows(ApiException.class, () -> validator.validate(DOMAIN, customFields, create));
    assertEquals(42201, error.errorCode().code());
    assertTrue(error.fields().containsKey(expectedField), "应含字段 " + expectedField + ": " + error.fields());
  }

  @Test
  @DisplayName("校验矩阵：未知键/必填缺失/枚举越界/类型不符 → 42201")
  void validationMatrix() {
    assertDoesNotThrow(() -> validator.validate(DOMAIN, null, true));
    assertDoesNotThrow(() -> validator.validate(DOMAIN, Map.of(), true));

    assertRejected(Map.of("mystery", "x"), true, "mystery"); // 未知键

    assertRejected(Map.of("note", "ok"), true, "severity"); // create 缺 required
    assertDoesNotThrow(() -> validator.validate(DOMAIN, Map.of("note", "ok"), false)); // update 不查 required

    assertRejected(Map.of("severity", "mid", "note", "n"), true, "severity"); // select 越界
    assertDoesNotThrow(() -> validator.validate(DOMAIN, Map.of("severity", "high"), true));

    assertRejected(Map.of("labels", "a", "note", "n"), true, "labels"); // multiselect 非数组
    assertRejected(Map.of("labels", List.of(), "note", "n"), true, "labels"); // 空数组
    assertRejected(Map.of("labels", List.of("a", "c"), "note", "n"), true, "labels"); // 元素越界
    assertDoesNotThrow(() -> validator.validate(DOMAIN, Map.of("severity", "low", "labels", List.of("a", "b")), true));

    assertRejected(Map.of("flags", List.of(), "note", "n"), true, "flags"); // checkbox 仍需非空数组
    assertRejected(Map.of("flags", List.of(List.of("嵌套")), "note", "n"), true, "flags"); // 元素须标量
    assertDoesNotThrow(() -> validator.validate(DOMAIN, Map.of("severity", "high", "flags", List.of("任意", 1, true)), true)); // 无 options 任意标量

    assertRejected(Map.of("estimate", "5", "note", "n"), true, "estimate"); // number 拒字符串
    assertDoesNotThrow(() -> validator.validate(DOMAIN, Map.of("severity", "high", "estimate", 5), true));
    assertDoesNotThrow(() -> validator.validate(DOMAIN, Map.of("severity", "high", "estimate", 5.5), true)); // decimal

    assertDoesNotThrow(() -> validator.validate(DOMAIN, Map.of("severity", "high", "deadline", "2026-01-02"), true));
    assertRejected(Map.of("deadline", "2026-1-2", "note", "n"), true, "deadline");
    assertRejected(Map.of("deadline", "2026-13-01", "note", "n"), true, "deadline");

    assertDoesNotThrow(() -> validator.validate(DOMAIN, Map.of("severity", "high", "plannedAt", "2026-01-02T03:04:05Z"), true));
    assertDoesNotThrow(() -> validator.validate(DOMAIN, Map.of("severity", "high", "plannedAt", "2026-01-02T03:04:05"), true)); // 裸本地时间
    assertRejected(Map.of("plannedAt", "2026-01-02", "note", "n"), true, "plannedAt"); // date 不是 datetime

    assertDoesNotThrow(() -> validator.validate(DOMAIN, Map.of("severity", "high", "note", "文本"), true));
    assertRejected(Map.of("note", 7, "severity", "high"), true, "note"); // text 拒数字
  }

  @Test
  @DisplayName("注册表加载：域内按 sort 升序；未知域空表")
  void registryLoadsSortedBySort() {
    List<String> keys = registry.byDomain(DOMAIN).stream().map(FieldDef::itemKey).toList();
    assertEquals(List.of("severity", "labels", "flags", "estimate", "deadline", "plannedAt", "note"), keys);
    assertTrue(registry.byDomain("no-such-domain").isEmpty());
  }

  @Test
  @DisplayName("meta 追加：field_def 字段挂到已注册域尾部，i18n/options/visibleWhen/required 透传")
  void metaAppendsCustomFields() {
    assertFalse(metaRegistry.get("product").orElseThrow().fields().stream()
        .anyMatch(field -> field.key().equals("region")), "前置：seed 前无自定义字段");
    insert("product", "region", "select", 1, "[{\"value\":\"cn\",\"i18n\":\"x.cn\"}]",
        "{\"field\":\"acl\",\"eq\":\"custom\"}", 5);
    registry.reload();

    MetaView meta = metaRegistry.get("product").orElseThrow();
    var custom = meta.fields().stream()
        .filter(field -> field.key().equals("region"))
        .findFirst()
        .orElseThrow(() -> new AssertionError("meta 应含自定义字段 region"));
    assertEquals("select", custom.type());
    assertEquals(Boolean.TRUE, custom.required());
    assertEquals("customField.field.region", custom.i18n());
    assertEquals(1, custom.options().size());
    assertEquals("cn", custom.options().getFirst().get("value"));
    assertNotNull(custom.visibleWhen(), "visibleWhen 原样透传");
    assertTrue(meta.fields().stream().anyMatch(field -> field.key().equals("name")), "原生字段保留");
  }
}
