package net.zentao.platform.web;

import java.util.List;

/** 列表信封（03 §2）：{"data":{"items":[…],"total":n}}；游标分页时间线由各域卡另行定义。 */
public record ListEnvelope<T>(List<T> items, long total) {

  public static <T> ListEnvelope<T> of(List<T> items, long total) {
    return new ListEnvelope<>(items, total);
  }
}
