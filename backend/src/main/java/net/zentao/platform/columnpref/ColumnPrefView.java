package net.zentao.platform.columnpref;

import java.util.List;

/**
 * 列设置视图（contract {@code ColumnPrefView}）：{@code columns} 为 null 表示该账号在该资源上未设置，
 * 前端用页面默认列（与空数组区分：空数组是「已设置但没有列」，本能力下不会产生）。
 */
public record ColumnPrefView(String resource, List<ColumnPrefItem> columns) {

  public static ColumnPrefView of(String resource, ColumnPref pref) {
    return new ColumnPrefView(resource, pref == null ? null : pref.columns());
  }
}
