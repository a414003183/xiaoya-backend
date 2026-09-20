package net.zentao.platform.langimport;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 语言目录（platform 卡 §3.12）：把语言包展平成「全点分键」，作为上传校验与导出的键真源。
 *
 * <p>资产是 {@code classpath:lang/*.json}——前端 {@code packages/i18n/src/locales/} 语言包的副本，
 * 由 {@code tools/contract-check/check-lang-catalog.mjs} 门禁按字节比对看护（漂移即 CI 红）。
 * 服务端必须知道每一个键：不知道就校验不了上传（未知键 → 42201），也生成不了导出。
 *
 * <p><b>键 ↔ lang_item 列映射（可逆，平台卡 §3.8/§3.12）</b>：
 * <pre>
 *   全键 platform.langUpload.title
 *     → domain   = 首段             "platform"
 *     → section  = 第二段（键 ≥3 段） "langUpload"；键只有两段时 section = "_"
 *     → item_key = 其余段以 '.' 连接 "title"
 * </pre>
 * 故 {@code item_key} 仍是「域内相对键」，{@link #fullKey} 加回域/节即得回全键。
 */
@Component
public class LangCatalog {

  public static final String LANG_ZH_CN = "zh-cn";
  public static final String LANG_EN = "en";
  /** 两段键没有 section 可用：以 `_` 占位（列 NOT NULL，且 `_` 不会与真实段名冲突——键段字符集不含它）。 */
  public static final String NO_SECTION = "_";
  /** lang_item.item_key 列宽（V4 迁移）：全键既已 ≤60，其余段更短，超宽即键非法。 */
  private static final int MAX_ITEM_KEY_LENGTH = 60;

  /** 语言码 → 语言包文件名（不含扩展名）；导出列顺序即此声明顺序。 */
  private static final Map<String, String> BUNDLES = Map.of(
      LANG_ZH_CN, "zh-CN",
      LANG_EN, "en");

  private final Map<String, Map<String, String>> defaultsByLang;
  private final Set<String> keys;

  public LangCatalog(JsonMapper jsonMapper) {
    Map<String, Map<String, String>> loaded = new LinkedHashMap<>();
    Set<String> union = new LinkedHashSet<>();
    for (Map.Entry<String, String> bundle : BUNDLES.entrySet()) {
      Map<String, String> defaults = load(jsonMapper, bundle.getValue());
      loaded.put(bundle.getKey(), defaults);
      union.addAll(defaults.keySet());
    }
    this.defaultsByLang = Map.copyOf(loaded);
    this.keys = Set.copyOf(union);
  }

  /** 支持的语言码（导出列顺序）。 */
  public List<String> languages() {
    return List.of(LANG_ZH_CN, LANG_EN);
  }

  /** 目录键全集（两语言并集；两语言键位一致由 check-lang-keys 门禁看护）。 */
  public Set<String> keys() {
    return keys;
  }

  /** 目录键全集，按字典序（导出行顺序稳定，便于人工比对 diff）。 */
  public List<String> sortedKeys() {
    return List.copyOf(new TreeSet<>(keys));
  }

  /** 该语言的内建默认文案（未知语言返回空表）。 */
  public Map<String, String> defaults(String lang) {
    return defaultsByLang.getOrDefault(normalizeLang(lang), Map.of());
  }

  /** 语言码归一（大小写/空白不敏感）：`zh-CN`/`EN` → `zh-cn`/`en`；未识别的一律小写原样返回。 */
  public String normalizeLang(String lang) {
    if (lang == null || lang.isBlank()) {
      return LANG_ZH_CN;
    }
    return lang.trim().toLowerCase(Locale.ROOT);
  }

  public boolean isKnownLang(String lang) {
    return BUNDLES.containsKey(normalizeLang(lang));
  }

  /** 全键 → (domain, section, item_key) 存储拆分。 */
  public static KeyParts parts(String fullKey) {
    int firstDot = fullKey.indexOf('.');
    if (firstDot < 0) {
      return new KeyParts(fullKey, NO_SECTION, "");
    }
    String domain = fullKey.substring(0, firstDot);
    int secondDot = fullKey.indexOf('.', firstDot + 1);
    if (secondDot < 0) {
      return new KeyParts(domain, NO_SECTION, fullKey.substring(firstDot + 1));
    }
    return new KeyParts(domain, fullKey.substring(firstDot + 1, secondDot), fullKey.substring(secondDot + 1));
  }

  /** (domain, section, item_key) → 全键（{@link #parts} 的逆）。 */
  public static String fullKey(String domain, String section, String itemKey) {
    String prefix = NO_SECTION.equals(section) ? domain : domain + "." + section;
    return itemKey == null || itemKey.isEmpty() ? prefix : prefix + "." + itemKey;
  }

  /** 存储拆分结果，见 {@link #parts}。 */
  public record KeyParts(String domain, String section, String itemKey) {}

  private Map<String, String> load(JsonMapper jsonMapper, String bundle) {
    ClassPathResource resource = new ClassPathResource("lang/" + bundle + ".json");
    try (InputStream in = resource.getInputStream()) {
      Map<String, Object> root = jsonMapper.readValue(in, new TypeReference<Map<String, Object>>() {});
      Map<String, String> flat = new LinkedHashMap<>();
      flatten(root, "", flat);
      return Map.copyOf(flat);
    } catch (IOException e) {
      throw new IllegalStateException("语言包加载失败（启动即失败，见 platform 卡 §3.12）：" + resource.getPath(), e);
    }
  }

  private static void flatten(Map<String, Object> node, String prefix, Map<String, String> out) {
    for (Map.Entry<String, Object> entry : node.entrySet()) {
      String key = prefix + entry.getKey();
      if (entry.getValue() instanceof Map<?, ?> nested) {
        @SuppressWarnings("unchecked")
        Map<String, Object> child = (Map<String, Object>) nested;
        flatten(child, key + ".", out);
        continue;
      }
      out.put(key, entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
    }
  }

  /** 目录键拆出的 item_key 是否装得进 lang_item.item_key（超宽 → 键非法，不可能发生，防御性断言）。 */
  static boolean fitsItemKey(String itemKey) {
    return itemKey.length() <= MAX_ITEM_KEY_LENGTH;
  }
}
