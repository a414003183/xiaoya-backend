package net.zentao.product.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.zentao.platform.session.SessionPrincipal;

/** 产品域对外接口（A2：requirement 等域经此读产品可见性；product 卡 §7）。 */
public interface ProductApi {

  /**
   * 当前账号的产品可见集（03 §7.2：对象自身 ACL ∪ 所属各组 acl.products）。
   * {@code visibleToAll=true} 表示不受限（超管），调用方不加过滤条件。
   */
  record ProductScope(boolean visibleToAll, Set<Long> productIds) {}

  ProductScope visibleScope(SessionPrincipal principal);

  default List<Long> visibleProductIds(SessionPrincipal principal) {
    return List.copyOf(visibleScope(principal).productIds());
  }

  Optional<ProductView> findById(long productId);

  /** 数据权限判定（不抛异常；写路径在 40401 之后调用）。 */
  default boolean canAccess(SessionPrincipal principal, long productId) {
    ProductScope scope = visibleScope(principal);
    return scope.visibleToAll() || scope.productIds().contains(productId);
  }

  /** 详情/动作前置守卫：不存在 → 40401；存在但不可见 → 40302（先于 40401 防探测，product 卡 §7）。 */
  ProductView requireVisible(SessionPrincipal principal, long productId);

  /**
   * 按 id 集合的产品分页列表（project 域「项目/项目集关联产品」跨域读）：DSL 与 DataScope 均按本域规则，
   * 可见集之外的 id 静默剔除；空集合 → 空列表。
   */
  ProductList pageByIds(List<Long> productIds, SessionPrincipal principal, Map<String, String[]> params);
}
