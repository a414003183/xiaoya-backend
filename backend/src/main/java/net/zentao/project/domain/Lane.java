package net.zentao.project.domain;

/** 看板列聚合根（project 卡 §3.5；纯 Java，A1）。wip_limit=-1 表示不限；列归档只置 archived，不删列。 */
public class Lane {

  private final long id;
  private final long boardId;
  private String name;
  private String color;
  private int wipLimit;
  private boolean archived;
  private int sort;

  public Lane(long id, long boardId, String name, String color, int wipLimit, boolean archived, int sort) {
    this.id = id;
    this.boardId = boardId;
    this.name = name;
    this.color = color;
    this.wipLimit = wipLimit;
    this.archived = archived;
    this.sort = sort;
  }

  /** PATCH 白名单（§5：name/color/wipLimit/archived/sort；null 不改）。 */
  public void update(String name, String color, Integer wipLimit, Boolean archived, Integer sort) {
    if (name != null) {
      this.name = name;
    }
    if (color != null) {
      this.color = color;
    }
    if (wipLimit != null) {
      this.wipLimit = wipLimit;
    }
    if (archived != null) {
      this.archived = archived;
    }
    if (sort != null) {
      this.sort = sort;
    }
  }

  /** WIP 上限（-1=不限，>=0 时到达即拒卡，§3.5/§5）。 */
  public boolean limitsWip() {
    return wipLimit >= 0;
  }

  public long id() {
    return id;
  }

  public long boardId() {
    return boardId;
  }

  public String name() {
    return name;
  }

  public String color() {
    return color;
  }

  public int wipLimit() {
    return wipLimit;
  }

  public boolean archived() {
    return archived;
  }

  public int sort() {
    return sort;
  }
}
