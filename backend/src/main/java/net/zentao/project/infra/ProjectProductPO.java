package net.zentao.project.infra;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

/** project_product 表 PO（项目↔产品关联）。 */
@Table("project_product")
public class ProjectProductPO {

  @Id(keyType = KeyType.Auto)
  private Long id;

  private Long projectId;
  private Long productId;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getProjectId() {
    return projectId;
  }

  public void setProjectId(Long projectId) {
    this.projectId = projectId;
  }

  public Long getProductId() {
    return productId;
  }

  public void setProductId(Long productId) {
    this.productId = productId;
  }
}
