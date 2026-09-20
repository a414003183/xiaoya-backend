package net.zentao.project.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 阶段类型字典聚合根（project 卡 §3.2；纯 Java，A1）。
 * percent 为同 projectModel 下累计权重，超限判定在 {@link net.zentao.project.app.ManageStageHandler}。
 */
public class Stage {

  private final long id;
  private String name;
  private BigDecimal percent;
  private String type;
  private String projectModel;
  private int sort;
  private String createdBy;
  private Instant createdAt;
  private String updatedBy;
  private Instant updatedAt;

  public Stage(long id, String name, BigDecimal percent, String type, String projectModel, int sort,
      String createdBy, Instant createdAt, String updatedBy, Instant updatedAt) {
    this.id = id;
    this.name = name;
    this.percent = percent;
    this.type = type;
    this.projectModel = projectModel;
    this.sort = sort;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.updatedBy = updatedBy;
    this.updatedAt = updatedAt;
  }

  /** PATCH 白名单（§5：name/percent/type/sort；null 不改）；projectModel 创建后不可改。 */
  public void update(String name, BigDecimal percent, String type, Integer sort) {
    if (name != null) {
      this.name = name;
    }
    if (percent != null) {
      this.percent = percent;
    }
    if (type != null) {
      this.type = type;
    }
    if (sort != null) {
      this.sort = sort;
    }
  }

  public void markUpdatedBy(String actor) {
    this.updatedBy = actor;
    this.updatedAt = Instant.now();
  }

  public long id() {
    return id;
  }

  public String name() {
    return name;
  }

  public BigDecimal percent() {
    return percent;
  }

  public String type() {
    return type;
  }

  public String projectModel() {
    return projectModel;
  }

  public int sort() {
    return sort;
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
}
