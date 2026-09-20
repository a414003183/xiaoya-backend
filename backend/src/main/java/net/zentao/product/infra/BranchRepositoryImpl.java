package net.zentao.product.infra;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import net.zentao.product.domain.Branch;
import net.zentao.product.domain.BranchRepository;
import org.springframework.stereotype.Component;

/** 分支仓储实现（infra）。 */
@Component
public class BranchRepositoryImpl implements BranchRepository {

  private static final QueryColumn DELETED_AT = new QueryColumn("deleted_at");

  private final BranchMapper mapper;

  public BranchRepositoryImpl(BranchMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<Branch> findActiveById(long id) {
    return Optional.ofNullable(mapper.selectOneByCondition(new QueryColumn("id").eq(id).and(DELETED_AT.isNull())))
        .map(BranchRepositoryImpl::toDomain);
  }

  @Override
  public List<Branch> findByProductId(long productId) {
    return mapper.selectListByCondition(new QueryColumn("product_id").eq(productId).and(DELETED_AT.isNull())).stream()
        .map(BranchRepositoryImpl::toDomain)
        .toList();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<Branch> queryPage(Object whereWrapper, int offset, int limit) {
    return mapper.selectListByQuery(((QueryWrapper) whereWrapper).limit(offset, limit)).stream()
        .map(BranchRepositoryImpl::toDomain)
        .toList();
  }

  @Override
  public long countByQuery(Object whereWrapper) {
    return mapper.selectCountByQuery((QueryWrapper) whereWrapper);
  }

  @Override
  public Branch insert(Branch branch) {
    BranchPO po = toPo(branch);
    po.setId(null);
    po.setLockVersion(0);
    mapper.insert(po);
    return toDomain(mapper.selectOneById(po.getId()));
  }

  @Override
  public Optional<Branch> update(Branch branch) {
    return mapper.update(toPo(branch), false) > 0 ? Optional.of(branch) : Optional.empty();
  }

  @Override
  public boolean existsByNameInProduct(long productId, String name, long excludeId) {
    return mapper.selectCountByCondition(new QueryColumn("product_id").eq(productId)
        .and(new QueryColumn("name").eq(name))
        .and(new QueryColumn("id").ne(excludeId))
        .and(DELETED_AT.isNull())) > 0;
  }

  @Override
  public int clearDefaultExcept(long productId, long keepId) {
    return Db.updateByCondition("branch",
        Row.of("is_default", 0),
        new QueryColumn("product_id").eq(productId).and(new QueryColumn("id").ne(keepId)).and(DELETED_AT.isNull()));
  }

  @Override
  public void softDelete(long id) {
    Db.updateByCondition("branch", Row.of("deleted_at", Instant.now()), new QueryColumn("id").eq(id));
  }

  @Override
  public Optional<Branch> findDefault(long productId) {
    List<Branch> branches = findByProductId(productId);
    return branches.stream().filter(Branch::isDefault).findFirst()
        .or(() -> branches.stream().filter(branch -> "active".equals(branch.status())).findFirst());
  }

  private static Branch toDomain(BranchPO po) {
    return new Branch(po.getId(), po.getProductId(), po.getName(),
        po.getIsDefault() != null && po.getIsDefault() == 1, po.getStatus(), po.getDescription(),
        po.getSort() == null ? 0 : po.getSort(), po.getCreatedBy(), po.getCreatedAt(), po.getUpdatedBy(),
        po.getUpdatedAt(), po.getClosedAt(), po.getLockVersion() == null ? 0 : po.getLockVersion());
  }

  private static BranchPO toPo(Branch branch) {
    BranchPO po = new BranchPO();
    po.setId(branch.id() == 0 ? null : branch.id());
    po.setProductId(branch.productId());
    po.setName(branch.name());
    po.setIsDefault(branch.isDefault() ? 1 : 0);
    po.setStatus(branch.status());
    po.setDescription(branch.description());
    po.setSort(branch.sort());
    po.setCreatedBy(branch.createdBy());
    po.setCreatedAt(branch.createdAt());
    po.setUpdatedBy(branch.updatedBy());
    po.setUpdatedAt(branch.updatedAt());
    po.setClosedAt(branch.closedAt());
    po.setLockVersion(branch.lockVersion());
    return po;
  }
}
