package net.zentao.workspace.api;

import java.util.List;

/**
 * 待办批量逐项结果（contract：TodoBatchResult）：
 * 批量创建项带 index（成功时兼带新建 id），批量动作项带 id；逐项成败互不影响（03 §1）。
 */
public record TodoBatchResult(List<Item> results) {

  /** 逐项结果（contract：TodoBatchResultItem）。 */
  public record Item(Integer index, Long id, boolean ok, String error) {

    public static Item created(int index, long id) {
      return new Item(index, id, true, null);
    }

    public static Item failed(int index, String error) {
      return new Item(index, null, false, error);
    }

    public static Item acted(long id) {
      return new Item(null, id, true, null);
    }

    public static Item actionFailed(long id, String error) {
      return new Item(null, id, false, error);
    }
  }
}
