package net.zentao.platform.filters;

/**
 * LIKE 模式构造（T56/BE-06）：用户输入先转义，再拼通配符。**转义是正确性问题**——不转义时
 * 搜 `a_b` 会命中 `axb`、搜 `%` 会命中全表（`_`/`%` 是 LIKE 元字符，见 {@code DocAccess} 的旧注释）。
 *
 * <p><b>为什么不用 {@code ESCAPE} 子句</b>：MySQL 8.4 与 H2 2.4.240(MODE=MySQL) 的 LIKE 默认转义符都是 {@code \}
 * （2026-09-22 双方言实测：模式 {@code %a\%_b%} 命中字面 {@code a_b}、不命中 {@code axb}），且模式值走绑定参数、
 * 不经字符串字面量解析，故转义符直接可用。显式写 {@code ESCAPE '\'} 反而要在 SQL 里放字面量反斜杠——
 * 两种方言的字符串字面量转义规则不同（MySQL 需 `\\`，H2 需 `\`），是自找的方言分叉。
 *
 * <p><b>必须配 {@code QueryColumn#likeRaw}</b>：MyBatis-Flex 1.11.8 的 {@code like(v)} 会自行再包一层 {@code %}
 * 且不转义（{@code like(v)} → {@code '%v%'}、{@code likeLeft(v)} → {@code 'v%'}、{@code likeRaw(v)} → 原样 {@code 'v'}），
 * 用它等于 {@code '%%…%%'}。服务器自己生成的前缀（数字/斜杠的 path）走 {@code likeLeft} 即可，不经本类。
 */
public final class LikePatterns {

  private LikePatterns() {}

  /** 包含匹配：{@code %转义后%}。配 {@code likeRaw} 用。 */
  public static String contains(String value) {
    return "%" + escape(value) + "%";
  }

  /** JSON 字符串数组列（文档 ACL、评审人）的元素边界匹配：{@code %"转义后"%}。配 {@code likeRaw} 用。 */
  public static String jsonElement(String value) {
    return "%\"" + escape(value) + "\"%";
  }

  /** 转义 LIKE 元字符 {@code \} {@code %} {@code _}（前加默认转义符 {@code \}）。 */
  public static String escape(String value) {
    StringBuilder escaped = new StringBuilder(value.length() + 8);
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '\\' || c == '%' || c == '_') {
        escaped.append('\\');
      }
      escaped.append(c);
    }
    return escaped.toString();
  }
}
