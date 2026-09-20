package net.zentao.platform.filters;

import java.util.Set;

/**
 * 域字段白名单（01 §2.4）：每域声明可过滤/可排序/可搜索字段。
 * 未注册字段过滤 → 40001，注入防线。
 */
public record FieldRegistry(Set<String> filterable, Set<String> sortable, Set<String> searchable) {

  public static FieldRegistry allowing(Set<String> filterable, Set<String> sortable, Set<String> searchable) {
    return new FieldRegistry(Set.copyOf(filterable), Set.copyOf(sortable), Set.copyOf(searchable));
  }

  public boolean isFilterable(String field) {
    return filterable.contains(field);
  }

  public boolean isSortable(String field) {
    return sortable.contains(field);
  }

  public boolean isSearchable(String field) {
    return searchable.contains(field);
  }
}
