package net.zentao.platform.filters;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T56 / BE-06：用户输入进 LIKE 之前必须转义（`\` `%` `_`），且通配符只由本类产生。
 *
 * <p>本类只钉模式字符串；语义由真 SQL 往返看护——包含语义见
 * {@code net.zentao.task.TaskListTest#keywordEscapingAndFilterCaps}，
 * 前缀语义见 {@code net.zentao.doc.DocPathScopeTest}。
 */
class LikePatternsTest {

  @Test
  @DisplayName("转义 LIKE 元字符：下划线 / 百分号 / 反斜杠本体")
  void escapeMetacharacters() {
    assertEquals("a\\_b", LikePatterns.escape("a_b"));
    assertEquals("50\\%off", LikePatterns.escape("50%off"));
    assertEquals("back\\\\slash", LikePatterns.escape("back\\slash"));
    assertEquals("选中的\\_50\\%\\\\混合", LikePatterns.escape("选中的_50%\\混合"));
    assertEquals("普通文本", LikePatterns.escape("普通文本"));
    assertEquals("", LikePatterns.escape(""));
  }

  @Test
  @DisplayName("三种模式：包含 / JSON 元素边界")
  void patterns() {
    assertEquals("%a\\_b%", LikePatterns.contains("a_b"));
    assertEquals("%\\%%", LikePatterns.contains("%"));
    assertEquals("%%", LikePatterns.contains(""));
    assertEquals("%\"a\\_b\"%", LikePatterns.jsonElement("a_b"));
  }
}
