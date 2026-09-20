package net.zentao.platform.web;

import java.util.List;

/** 批量动作逐项结果（contract：BatchActionResult；部分成功语义）。 */
public record BatchActionResult(List<Item> results) {

  public record Item(long id, boolean ok, String error) {}

  public static Item ok(long id) {
    return new Item(id, true, null);
  }

  public static Item failed(long id, String error) {
    return new Item(id, false, error);
  }
}
