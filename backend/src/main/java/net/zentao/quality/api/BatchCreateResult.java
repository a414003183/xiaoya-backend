package net.zentao.quality.api;

import java.util.List;

/** 批量创建通用结果（contract：BatchCreateResult = {results:[{index,ok,id,error}]}；bug/testCase 共用）。 */
public record BatchCreateResult(List<Item> results) {

  public record Item(int index, boolean ok, Long id, String error) {

    public static Item ok(int index, long id) {
      return new Item(index, true, id, null);
    }

    public static Item failed(int index, String error) {
      return new Item(index, false, null, error);
    }
  }
}
