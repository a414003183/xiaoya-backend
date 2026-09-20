package net.zentao.doc.infra;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.List;
import java.util.Optional;
import net.zentao.doc.domain.DocCategory;
import net.zentao.doc.domain.DocCategoryRepository;
import org.springframework.stereotype.Component;

/** 库内目录仓储实现（infra：doc_category 表，真实删除）。 */
@Component
public class DocCategoryRepositoryImpl implements DocCategoryRepository {

  private static final QueryColumn ID = new QueryColumn("id");
  private static final QueryColumn DOC_SPACE_ID = new QueryColumn("doc_space_id");
  private static final QueryColumn PARENT_ID = new QueryColumn("parent_id");

  private final DocCategoryMapper mapper;

  public DocCategoryRepositoryImpl(DocCategoryMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<DocCategory> findById(long id) {
    return Optional.ofNullable(mapper.selectOneById(id)).map(this::toDomain);
  }

  @Override
  public List<DocCategory> findBySpace(long docSpaceId) {
    return mapper.selectListByQuery(QueryWrapper.create()
        .where(DOC_SPACE_ID.eq(docSpaceId))
        .orderBy(new QueryColumn("sort").asc(), ID.asc())).stream() // banned-words-ok：MyBatis-Flex 构造器方法名
        .map(this::toDomain)
        .toList();
  }

  @Override
  public DocCategory insert(DocCategory category) {
    DocCategoryPO po = toPo(category);
    po.setId(null);
    po.setLockVersion(0);
    mapper.insert(po);
    return toDomain(mapper.selectOneById(po.getId()));
  }

  @Override
  public Optional<DocCategory> update(DocCategory category) {
    if (mapper.update(toPo(category), false) <= 0) {
      return Optional.empty();
    }
    return findById(category.id());
  }

  @Override
  public void delete(long id) {
    mapper.deleteById(id);
  }

  @Override
  public long countChildren(long parentId) {
    return mapper.selectCountByQuery(QueryWrapper.create().where(PARENT_ID.eq(parentId)));
  }

  private DocCategory toDomain(DocCategoryPO po) {
    return new DocCategory(po.getId(), po.getDocSpaceId(), po.getParentId() == null ? 0 : po.getParentId(),
        po.getName(), po.getSort() == null ? 0 : po.getSort(), po.getCreatedBy(), po.getCreatedAt(),
        po.getUpdatedBy(), po.getUpdatedAt(), po.getLockVersion() == null ? 0 : po.getLockVersion());
  }

  private DocCategoryPO toPo(DocCategory category) {
    DocCategoryPO po = new DocCategoryPO();
    po.setId(category.id() == 0 ? null : category.id());
    po.setDocSpaceId(category.docSpaceId());
    po.setParentId(category.parentId());
    po.setName(category.name());
    po.setSort(category.sort());
    po.setCreatedBy(category.createdBy());
    po.setCreatedAt(category.createdAt());
    po.setUpdatedBy(category.updatedBy());
    po.setUpdatedAt(category.updatedAt());
    po.setLockVersion(category.lockVersion());
    return po;
  }
}
