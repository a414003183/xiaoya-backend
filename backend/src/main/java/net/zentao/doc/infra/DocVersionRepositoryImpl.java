package net.zentao.doc.infra;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.List;
import java.util.Optional;
import net.zentao.doc.domain.DocVersion;
import net.zentao.doc.domain.DocVersionRepository;
import org.springframework.stereotype.Component;

/** 正文快照仓储实现（infra：doc_content 表；v0 覆盖写，v>=1 只增不改）。 */
@Component
public class DocVersionRepositoryImpl implements DocVersionRepository {

  private static final QueryColumn DOC_ID = new QueryColumn("doc_id");
  private static final QueryColumn VERSION = new QueryColumn("version");

  private final DocContentMapper mapper;
  private final DocAclJson aclJson;

  public DocVersionRepositoryImpl(DocContentMapper mapper, DocAclJson aclJson) {
    this.mapper = mapper;
    this.aclJson = aclJson;
  }

  @Override
  public Optional<DocVersion> findByDocAndVersion(long docId, int version) {
    return Optional.ofNullable(mapper.selectOneByCondition(DOC_ID.eq(docId).and(VERSION.eq(version))))
        .map(this::toDomain);
  }

  @Override
  public List<DocVersion> findSnapshots(long docId) {
    return mapper.selectListByQuery(QueryWrapper.create()
        .where(DOC_ID.eq(docId).and(VERSION.ge(DocVersion.DRAFT_VERSION + 1)))
        .orderBy(VERSION.desc())) // banned-words-ok：MyBatis-Flex 构造器方法名，非请求参数
        .stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public List<DocVersion> findSnapshotsOf(List<Long> docIds) {
    if (docIds.isEmpty()) {
      return List.of();
    }
    return mapper.selectListByCondition(DOC_ID.in(docIds).and(VERSION.ge(DocVersion.DRAFT_VERSION + 1))).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public List<DocVersion> findDrafts(List<Long> docIds) {
    if (docIds.isEmpty()) {
      return List.of();
    }
    return mapper.selectListByCondition(DOC_ID.in(docIds).and(VERSION.eq(DocVersion.DRAFT_VERSION))).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public void insert(DocVersion version) {
    DocContentPO po = toPo(version);
    po.setId(null);
    mapper.insert(po);
  }

  @Override
  public void updateDraft(DocVersion draft) {
    DocContentPO po = toPo(draft);
    po.setId(draft.id());
    mapper.update(po);
  }

  private DocVersion toDomain(DocContentPO po) {
    return new DocVersion(po.getId(), po.getDocId(), po.getVersion(), po.getTitle(), po.getContent(), po.getDigest(),
        aclJson.readIds(po.getFiles()), po.getCreatedBy(), po.getCreatedAt(), po.getUpdatedBy(), po.getUpdatedAt());
  }

  private DocContentPO toPo(DocVersion version) {
    DocContentPO po = new DocContentPO();
    po.setId(version.id() == 0 ? null : version.id());
    po.setDocId(version.docId());
    po.setVersion(version.version());
    po.setTitle(version.title());
    po.setContent(version.content());
    po.setDigest(version.digest());
    po.setFiles(aclJson.writeIds(version.files()));
    po.setCreatedBy(version.createdBy());
    po.setCreatedAt(version.createdAt());
    po.setUpdatedBy(version.updatedBy());
    po.setUpdatedAt(version.updatedAt());
    return po;
  }
}
