package net.zentao.doc.domain;

import java.time.Instant;
import java.util.List;

/**
 * 正文快照（doc 卡 §3.3，表 doc_content）：{@code version=0} 为草稿工作副本（可覆盖写），
 * {@code version>=1} 为不可变发布快照；{@code UNIQUE(doc_id, version)}。
 */
public record DocVersion(long id, long docId, int version, String title, String content, String digest,
    List<Long> files, String createdBy, Instant createdAt, String updatedBy, Instant updatedAt) {

  /** 草稿工作副本版本号。 */
  public static final int DRAFT_VERSION = 0;
  private static final int DIGEST_LENGTH = 200;
  private static final int DIGEST_MAX = 255;

  public DocVersion {
    files = files == null ? List.of() : List.copyOf(files);
  }

  public static DocVersion draft(long docId, String title, String content, List<Long> files, String actor,
      Instant now) {
    return new DocVersion(0, docId, DRAFT_VERSION, title, content, digestOf(content), files, actor, now, null, null);
  }

  public static DocVersion snapshot(long docId, int version, String title, String content, List<Long> files,
      String actor, Instant now) {
    return new DocVersion(0, docId, version, title, content, digestOf(content), files, actor, now, null, null);
  }

  public boolean isDraft() {
    return version == DRAFT_VERSION;
  }

  /** 快照与工作副本逐字节一致判定（publish 无改动守卫 42203）。 */
  public boolean sameContentAs(DocVersion other) {
    return other != null
        && java.util.Objects.equals(title, other.title)
        && java.util.Objects.equals(content, other.content)
        && java.util.Objects.equals(files, other.files);
  }

  /** digest 缺省 = 正文去标记后前 200 字（doc 卡 §3.3/§8）。 */
  public static String digestOf(String content) {
    if (content == null || content.isBlank()) {
      return "";
    }
    String text = LINK.matcher(content).replaceAll("$1");
    text = MARKDOWN_MARKERS.matcher(text).replaceAll("");
    text = text.replaceAll("\\s+", " ").trim();
    String digest = text.length() > DIGEST_LENGTH ? text.substring(0, DIGEST_LENGTH) : text;
    return digest.length() > DIGEST_MAX ? digest.substring(0, DIGEST_MAX) : digest;
  }

  /** 链接留文字（$1），图片整段去掉。 */
  private static final java.util.regex.Pattern LINK =
      java.util.regex.Pattern.compile("\\[([^\\]]*)\\]\\([^)]*\\)");

  /** 去标记：代码块/行内代码/图片/标题/强调等符号一律去掉。 */
  private static final java.util.regex.Pattern MARKDOWN_MARKERS = java.util.regex.Pattern.compile(
      "(?s)```.*?```|`[^`]*`|!\\[[^\\]]*\\]\\([^)]*\\)|[#>*_~|\\-]+");
}
