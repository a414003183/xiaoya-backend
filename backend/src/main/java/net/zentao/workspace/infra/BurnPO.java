package net.zentao.workspace.infra;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/** burn 表 PO（workspace 卡 §3.3；task_id=0 为执行级日行，无软删无乐观锁）。 */
@Table("burn")
public class BurnPO {

  @Id(keyType = KeyType.Auto)
  private Long id;

  private Long executionId;
  private LocalDate burnDate;
  private Long taskId;
  private BigDecimal estimateHours;
  private BigDecimal consumedHours;
  private BigDecimal leftHours;
  private BigDecimal storyPoint;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getExecutionId() {
    return executionId;
  }

  public void setExecutionId(Long executionId) {
    this.executionId = executionId;
  }

  public LocalDate getBurnDate() {
    return burnDate;
  }

  public void setBurnDate(LocalDate burnDate) {
    this.burnDate = burnDate;
  }

  public Long getTaskId() {
    return taskId;
  }

  public void setTaskId(Long taskId) {
    this.taskId = taskId;
  }

  public BigDecimal getEstimateHours() {
    return estimateHours;
  }

  public void setEstimateHours(BigDecimal estimateHours) {
    this.estimateHours = estimateHours;
  }

  public BigDecimal getConsumedHours() {
    return consumedHours;
  }

  public void setConsumedHours(BigDecimal consumedHours) {
    this.consumedHours = consumedHours;
  }

  public BigDecimal getLeftHours() {
    return leftHours;
  }

  public void setLeftHours(BigDecimal leftHours) {
    this.leftHours = leftHours;
  }

  public BigDecimal getStoryPoint() {
    return storyPoint;
  }

  public void setStoryPoint(BigDecimal storyPoint) {
    this.storyPoint = storyPoint;
  }
}
