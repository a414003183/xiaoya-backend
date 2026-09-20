package net.zentao.quality.infra;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import java.time.Instant;

/** test_run_case 表 PO（quality 卡 §3.5 执行结果）。 */
@Table("test_run_case")
public class ResultPO {

  @Id(keyType = KeyType.Auto)
  private Long id;

  private Long testRunId;
  private Long testCaseId;
  private Integer version;
  private String assignee;
  private String result;
  private String runner;
  private Instant runAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getTestRunId() {
    return testRunId;
  }

  public void setTestRunId(Long testRunId) {
    this.testRunId = testRunId;
  }

  public Long getTestCaseId() {
    return testCaseId;
  }

  public void setTestCaseId(Long testCaseId) {
    this.testCaseId = testCaseId;
  }

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer version) {
    this.version = version;
  }

  public String getAssignee() {
    return assignee;
  }

  public void setAssignee(String assignee) {
    this.assignee = assignee;
  }

  public String getResult() {
    return result;
  }

  public void setResult(String result) {
    this.result = result;
  }

  public String getRunner() {
    return runner;
  }

  public void setRunner(String runner) {
    this.runner = runner;
  }

  public Instant getRunAt() {
    return runAt;
  }

  public void setRunAt(Instant runAt) {
    this.runAt = runAt;
  }
}
