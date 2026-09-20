package net.zentao.quality.domain;

import java.time.Instant;

/** 执行结果聚合（quality 卡 §3.5；test_run_case 行，UNIQUE(testRunId, testCaseId) 幂等 upsert）。 */
public class Result {

  private final long id;
  private final long testRunId;
  private final long testCaseId;
  private final int version;
  private String assignee;
  private String result;
  private String runner;
  private Instant runAt;

  public Result(long id, long testRunId, long testCaseId, int version, String assignee, String result,
      String runner, Instant runAt) {
    this.id = id;
    this.testRunId = testRunId;
    this.testCaseId = testCaseId;
    this.version = version;
    this.assignee = assignee;
    this.result = result;
    this.runner = runner;
    this.runAt = runAt;
  }

  /** record-result 覆写（同 testRun+case 只存最新）。 */
  public void record(String result, String runner, Instant runAt) {
    this.result = result;
    this.runner = runner;
    this.runAt = runAt;
  }

  public void assignTo(String assignee) {
    this.assignee = assignee;
  }

  public long id() {
    return id;
  }

  public long testRunId() {
    return testRunId;
  }

  public long testCaseId() {
    return testCaseId;
  }

  public int version() {
    return version;
  }

  public String assignee() {
    return assignee;
  }

  public String result() {
    return result;
  }

  public String runner() {
    return runner;
  }

  public Instant runAt() {
    return runAt;
  }
}
