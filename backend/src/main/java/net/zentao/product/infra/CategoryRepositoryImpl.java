package net.zentao.product.infra;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import net.zentao.product.domain.Category;
import net.zentao.product.domain.CategoryRepository;
import org.springframework.stereotype.Component;

/** 分类仓储实现（infra；级联软删子树经 Row API 一次 UPDATE）。 */
@Component
public class CategoryRepositoryImpl implements CategoryRepository {

  private static final QueryColumn DELETED_AT = new QueryColumn("deleted_at");

  private final CategoryMapper mapper;

  public CategoryRepositoryImpl(CategoryMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<Category> findActiveById(long id) {
    return Optional.ofNullable(mapper.selectOneByCondition(new QueryColumn("id").eq(id).and(DELETED_AT.isNull())))
        .map(CategoryRepositoryImpl::toDomain);
  }

  @Override
  public List<Category> findByProductAndType(long productId, String type) {
    return mapper.selectListByQuery(QueryWrapper.create()
        .where(new QueryColumn("product_id").eq(productId).and(new QueryColumn("type").eq(type)).and(DELETED_AT.isNull()))
        .orderBy(new QueryColumn("sort").asc(), new QueryColumn("id").asc())).stream() // banned-words-ok：MyBatis-Flex 构造器方法名
        .map(CategoryRepositoryImpl::toDomain)
        .toList();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<Category> queryList(Object whereWrapper) {
    return mapper.selectListByQuery((QueryWrapper) whereWrapper).stream()
        .map(CategoryRepositoryImpl::toDomain)
        .toList();
  }

  @Override
  public Category insert(Category category) {
    CategoryPO po = toPo(category);
    po.setId(null);
    po.setLockVersion(0);
    mapper.insert(po);
    return toDomain(mapper.selectOneById(po.getId()));
  }

  @Override
  public Optional<Category> update(Category category) {
    return mapper.update(toPo(category), false) > 0 ? Optional.of(category) : Optional.empty();
  }

  @Override
  public int softDeleteAll(List<Long> ids) {
    if (ids.isEmpty()) {
      return 0;
    }
    return Db.updateByCondition("category", Row.of("deleted_at", Instant.now()), new QueryColumn("id").in(ids));
  }

  private static Category toDomain(CategoryPO po) {
    return new Category(po.getId(), po.getProductId(), po.getBranchId() == null ? 0 : po.getBranchId(),
        po.getParentId() == null ? 0 : po.getParentId(), po.getType(), po.getName(), po.getOwner(),
        po.getSort() == null ? 0 : po.getSort(), po.getCreatedBy(), po.getCreatedAt(), po.getUpdatedBy(),
        po.getUpdatedAt(), po.getLockVersion() == null ? 0 : po.getLockVersion());
  }

  private static CategoryPO toPo(Category category) {
    CategoryPO po = new CategoryPO();
    po.setId(category.id() == 0 ? null : category.id());
    po.setProductId(category.productId());
    po.setBranchId(category.branchId());
    po.setParentId(category.parentId());
    po.setType(category.type());
    po.setName(category.name());
    po.setOwner(category.owner());
    po.setSort(category.sort());
    po.setCreatedBy(category.createdBy());
    po.setCreatedAt(category.createdAt());
    po.setUpdatedBy(category.updatedBy());
    po.setUpdatedAt(category.updatedAt());
    po.setLockVersion(category.lockVersion());
    return po;
  }
}
