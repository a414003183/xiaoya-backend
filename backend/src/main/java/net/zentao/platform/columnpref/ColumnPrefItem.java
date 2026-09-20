package net.zentao.platform.columnpref;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 列设置的一项（platform「列设置」能力；contract {@code ColumnPrefItem}）。
 *
 * <p>{@code key} 是列的稳定身份（与前端列 key/dataIndex 同值），数组顺序即用户排定的展示顺序；
 * {@code fixed} 为固定方向，null = 不固定（antd Table 的 left/right 固定位）。
 */
public record ColumnPrefItem(
    String key, boolean visible, @Schema(allowableValues = {"left", "right"}) String fixed) {

  public static final String FIXED_LEFT = "left";
  public static final String FIXED_RIGHT = "right";

  public static ColumnPrefItem of(String key, boolean visible, String fixed) {
    return new ColumnPrefItem(key, visible, fixed);
  }
}
