package net.zentao.project.infra;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import java.time.Instant;

/** board_lane 表 PO（project 卡 §3.5；LaneView 只暴露 id/boardId/name/color/wipLimit/archived/sort）。 */
@Table("board_lane")
public class LanePO {

  @Id(keyType = KeyType.Auto)
  private Long id;

  private Long boardId;
  private String name;
  private String color;
  private Integer wipLimit;
  private Integer archived;
  private Integer sort;
  private Instant deletedAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getBoardId() {
    return boardId;
  }

  public void setBoardId(Long boardId) {
    this.boardId = boardId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getColor() {
    return color;
  }

  public void setColor(String color) {
    this.color = color;
  }

  public Integer getWipLimit() {
    return wipLimit;
  }

  public void setWipLimit(Integer wipLimit) {
    this.wipLimit = wipLimit;
  }

  public Integer getArchived() {
    return archived;
  }

  public void setArchived(Integer archived) {
    this.archived = archived;
  }

  public Integer getSort() {
    return sort;
  }

  public void setSort(Integer sort) {
    this.sort = sort;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(Instant deletedAt) {
    this.deletedAt = deletedAt;
  }
}
