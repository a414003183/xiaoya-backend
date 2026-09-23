package net.zentao.platform.audit;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import java.time.Instant;

/**
 * audit_log 表 PO（B1 §H3，流水表无审计四件套）。T04 起是**分级审计行**：分类/结果/失败原因/批次/
 * UA 与设备摘要/MFA（预留）/三份 JSON（changes·snapshot·extra）+ 哈希链两列。
 *
 * <p>{@code category}/{@code result} 由 {@link AuditRecorder} 按 {@link AuditCatalog} 兜底补全
 * （库侧 NOT NULL + CHECK）；{@code prevHash}/{@code hash} 只在 {@link AuditRecorder} 内写——
 * 表只有 INSERT/SELECT 授权，插入后改不了（{@code check-audit-append-only} 门禁看护）。
 */
@Table("audit_log")
public class AuditLogPO {

  @Id(keyType = KeyType.Auto)
  private Long id;

  private String account;
  private String action;
  private String category;
  private String result;
  private String reason;
  private String objectType;
  private Long objectId;
  private Long batchId;
  private String detail;
  private String changes;
  private String snapshot;
  private String extra;
  private String ip;
  private String ua;
  private String device;
  private String mfa;
  private String traceId;
  private String prevHash;
  private String hash;
  private Instant createdAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getAccount() {
    return account;
  }

  public void setAccount(String account) {
    this.account = account;
  }

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getResult() {
    return result;
  }

  public void setResult(String result) {
    this.result = result;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public String getObjectType() {
    return objectType;
  }

  public void setObjectType(String objectType) {
    this.objectType = objectType;
  }

  public Long getObjectId() {
    return objectId;
  }

  public void setObjectId(Long objectId) {
    this.objectId = objectId;
  }

  public Long getBatchId() {
    return batchId;
  }

  public void setBatchId(Long batchId) {
    this.batchId = batchId;
  }

  public String getDetail() {
    return detail;
  }

  public void setDetail(String detail) {
    this.detail = detail;
  }

  public String getChanges() {
    return changes;
  }

  public void setChanges(String changes) {
    this.changes = changes;
  }

  public String getSnapshot() {
    return snapshot;
  }

  public void setSnapshot(String snapshot) {
    this.snapshot = snapshot;
  }

  public String getExtra() {
    return extra;
  }

  public void setExtra(String extra) {
    this.extra = extra;
  }

  public String getIp() {
    return ip;
  }

  public void setIp(String ip) {
    this.ip = ip;
  }

  public String getUa() {
    return ua;
  }

  public void setUa(String ua) {
    this.ua = ua;
  }

  public String getDevice() {
    return device;
  }

  public void setDevice(String device) {
    this.device = device;
  }

  public String getMfa() {
    return mfa;
  }

  public void setMfa(String mfa) {
    this.mfa = mfa;
  }

  public String getTraceId() {
    return traceId;
  }

  public void setTraceId(String traceId) {
    this.traceId = traceId;
  }

  public String getPrevHash() {
    return prevHash;
  }

  public void setPrevHash(String prevHash) {
    this.prevHash = prevHash;
  }

  public String getHash() {
    return hash;
  }

  public void setHash(String hash) {
    this.hash = hash;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
