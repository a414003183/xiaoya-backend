package net.zentao.org.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 账号角色字典项（org 卡 §3.4；旧禅道「后台→自定义→用户→角色列表」）。
 *
 * 与 {@link Group} 的区别：Group 是权限角色（一组权限码），本聚合是**岗位角色标签**——账号资料上的
 * `role` 列存的是这里的 code。labels 是「语言码 → 角色名」映射（服务端不枚举语言，前端按当前语言取，
 * 取不到回退任一非空值），因此新增语言无需改表/改接口。
 */
public class AccountRole {

  /** 角色码上限（与 account.role 列同宽 16）：`^[a-z][a-z0-9-]*$`。 */
  public static final int CODE_MAX = 16;
  /** 单个语言的角色名上限。 */
  public static final int LABEL_MAX = 60;
  /** labels 的键（语言码）上限。 */
  private static final int LANG_KEY_MAX = 16;

  private final String code;
  private Map<String, String> labels;
  private int sort;
  private final boolean builtin;
  private String updatedBy;
  private int lockVersion;

  public AccountRole(String code, Map<String, String> labels, int sort, boolean builtin, int lockVersion) {
    this.code = code;
    this.labels = normalize(labels);
    this.sort = sort;
    this.builtin = builtin;
    this.lockVersion = lockVersion;
  }

  public void relabel(Map<String, String> newLabels) {
    this.labels = normalize(newLabels);
  }

  public void reorder(int newSort) {
    this.sort = newSort;
  }

  public void markUpdatedBy(String actor) {
    this.updatedBy = actor;
  }

  /** 角色码合法（小写字母开头，字母/数字/连字符，2–16 位）。 */
  public static boolean validCode(String code) {
    return code != null && code.matches("^[a-z][a-z0-9-]{1," + (CODE_MAX - 1) + "}$");
  }

  /** labels 规整：去空值/空白、修剪、长度与语言码校验；全空视为非法（返回后由调用方判空）。 */
  public static Map<String, String> normalize(Map<String, String> labels) {
    Map<String, String> result = new LinkedHashMap<>();
    if (labels == null) {
      return result;
    }
    labels.forEach((lang, name) -> {
      if (lang == null || name == null) {
        return;
      }
      String key = lang.trim();
      String value = name.trim();
      if (key.isEmpty() || key.length() > LANG_KEY_MAX || value.isEmpty()) {
        return;
      }
      if (value.length() > LABEL_MAX) {
        throw new IllegalArgumentException("label too long");
      }
      result.put(key, value);
    });
    return result;
  }

  public String code() {
    return code;
  }

  public Map<String, String> labels() {
    return Map.copyOf(labels);
  }

  public int sort() {
    return sort;
  }

  public boolean builtin() {
    return builtin;
  }

  public String updatedBy() {
    return updatedBy;
  }

  public int lockVersion() {
    return lockVersion;
  }
}
