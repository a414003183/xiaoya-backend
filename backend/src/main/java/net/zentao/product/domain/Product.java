package net.zentao.product.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 产品聚合根（product 卡 §3.1；纯 Java，A1）。 */
public class Product {

  private final long id;
  private long programId;
  private String name;
  private String code;
  private String type;
  private String status;
  private String description;
  private String po;
  private String qd;
  private String rd;
  private String acl;
  private List<String> whitelist;
  private int sort;
  private Map<String, Object> customFields;
  private String createdBy;
  private Instant createdAt;
  private String updatedBy;
  private Instant updatedAt;
  private Instant closedAt;
  private int lockVersion;

  public Product(long id, long programId, String name, String code, String type, String status, String description,
      String po, String qd, String rd, String acl, List<String> whitelist, int sort, Map<String, Object> customFields,
      String createdBy, Instant createdAt, String updatedBy, Instant updatedAt, Instant closedAt, int lockVersion) {
    this.id = id;
    this.programId = programId;
    this.name = name;
    this.code = code;
    this.type = type;
    this.status = status;
    this.description = description;
    this.po = po;
    this.qd = qd;
    this.rd = rd;
    this.acl = acl;
    this.whitelist = whitelist == null ? List.of() : List.copyOf(whitelist);
    this.sort = sort;
    this.customFields = customFields == null ? Map.of() : Map.copyOf(customFields);
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.updatedBy = updatedBy;
    this.updatedAt = updatedAt;
    this.closedAt = closedAt;
    this.lockVersion = lockVersion;
  }

  /** PATCH 白名单字段（product 卡 §5：name/code/type/programId/po/qd/rd/acl/whitelist/description/sort/customFields）；null 不改。 */
  public void update(String name, String code, String type, Long programId, String po, String qd, String rd,
      String acl, List<String> whitelist, String description, Integer sort, Map<String, Object> customFields) {
    if (name != null) {
      this.name = name;
    }
    if (code != null) {
      this.code = code;
    }
    if (type != null) {
      this.type = type;
    }
    if (programId != null) {
      this.programId = programId;
    }
    if (po != null) {
      this.po = po;
    }
    if (qd != null) {
      this.qd = qd;
    }
    if (rd != null) {
      this.rd = rd;
    }
    if (acl != null) {
      this.acl = acl;
    }
    if (whitelist != null) {
      this.whitelist = List.copyOf(whitelist);
    }
    if (description != null) {
      this.description = description;
    }
    if (sort != null) {
      this.sort = sort;
    }
    if (customFields != null) {
      this.customFields = Map.copyOf(customFields);
    }
  }

  /** 状态机落状态（product.yml close/activate → WorkflowEngine.applyStatus）。 */
  public void applyStatus(String status) {
    this.status = status;
  }

  /** fieldSet 副作用入口（product.yml：close 落 closedAt、activate 清 closedAt）。 */
  public void setField(String field, Object value) {
    switch (field) {
      case "closedAt" -> this.closedAt = value == null ? null : toInstant(value);
      case "status" -> this.status = value == null ? this.status : String.valueOf(value);
      default -> throw new IllegalArgumentException("产品未声明的 fieldSet 字段：" + field);
    }
  }

  private static Instant toInstant(Object value) {
    if (value instanceof Instant instant) {
      return instant;
    }
    return Instant.parse(String.valueOf(value));
  }

  public void markUpdatedBy(String actor) {
    this.updatedBy = actor;
    this.updatedAt = Instant.now();
  }

  public long id() {
    return id;
  }

  public long programId() {
    return programId;
  }

  public String name() {
    return name;
  }

  public String code() {
    return code;
  }

  public String type() {
    return type;
  }

  public String status() {
    return status;
  }

  public String description() {
    return description;
  }

  public String po() {
    return po;
  }

  public String qd() {
    return qd;
  }

  public String rd() {
    return rd;
  }

  public String acl() {
    return acl;
  }

  public List<String> whitelist() {
    return whitelist;
  }

  public int sort() {
    return sort;
  }

  public Map<String, Object> customFields() {
    return customFields;
  }

  public String createdBy() {
    return createdBy;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public String updatedBy() {
    return updatedBy;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public Instant closedAt() {
    return closedAt;
  }

  public int lockVersion() {
    return lockVersion;
  }
}
