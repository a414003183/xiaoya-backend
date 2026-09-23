package net.zentao.platform.audit;

/**
 * User-Agent 摘要（ADR-004 决策 1 的 device 列，T04）。
 *
 * <p>ponytail: 朴素识别——只认主流浏览器与操作系统的特征串，命中就写「浏览器 / 系统」，都认不出则 null。
 * 上限是识别率（新浏览器/伪装 UA 会认不出），升级路径是引 UADetector/uap-java 做完整解析；此处刻意不引依赖。
 */
final class UserAgents {

  private static final String[][] BROWSERS = {
      {"Edg/", "Edge"},
      {"OPR/", "Opera"},
      {"Chrome/", "Chrome"},
      {"Firefox/", "Firefox"},
      {"Version/", "Safari"},
      {"MSIE", "IE"},
      {"Trident", "IE"},
  };

  private UserAgents() {}

  /** UA → 「浏览器 / 系统」摘要；两个半段可各自缺失，全缺则 null。 */
  static String device(String userAgent) {
    if (userAgent == null || userAgent.isBlank()) {
      return null;
    }
    String[] halves = {browser(userAgent), system(userAgent)};
    StringBuilder summary = null;
    for (String half : halves) {
      if (half != null) {
        summary = summary == null ? new StringBuilder(half) : summary.append(" / ").append(half);
      }
    }
    return summary == null ? null : summary.toString();
  }

  /** UA 原文入库前截断（库列 255，浏览器 UA 可以很长）。 */
  static String truncate(String userAgent) {
    if (userAgent == null) {
      return null;
    }
    String trimmed = userAgent.strip();
    return trimmed.isEmpty() ? null : trimmed.substring(0, Math.min(255, trimmed.length()));
  }

  private static String browser(String ua) {
    for (String[] known : BROWSERS) {
      if (ua.contains(known[0])) {
        return known[1];
      }
    }
    return null;
  }

  private static String system(String ua) {
    if (ua.contains("Android")) {
      return "Android";
    }
    if (ua.contains("iPhone") || ua.contains("iPad") || ua.contains("iOS")) {
      return "iOS";
    }
    if (ua.contains("Windows")) {
      return "Windows";
    }
    if (ua.contains("Mac OS X") || ua.contains("macOS")) {
      return "macOS";
    }
    if (ua.contains("Linux")) {
      return "Linux";
    }
    return null;
  }
}
