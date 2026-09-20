package net.zentao.doc.app;

import java.util.List;
import net.zentao.doc.api.DocVersionList;
import net.zentao.doc.api.DocVersionView;
import net.zentao.doc.domain.Doc;
import net.zentao.doc.domain.DocVersion;
import net.zentao.doc.domain.DocVersionRepository;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import org.springframework.stereotype.Component;

/** 版本查询（doc 卡 §3.3/§5）：列表固定 version desc 不分页、仅 v>=1；v0 工作副本仅可编辑者（余者 40302）。 */
@Component
public class DocVersionQueryService {

  private final DocVersionRepository versionRepository;
  private final DocAccess access;

  public DocVersionQueryService(DocVersionRepository versionRepository, DocAccess access) {
    this.versionRepository = versionRepository;
    this.access = access;
  }

  /** 版本列表（发布快照 v>=1，固定 version desc，不分页；权限随主文档）。 */
  public DocVersionList list(SessionPrincipal principal, long docId) {
    access.requireReadableDoc(principal, docId);
    List<DocVersion> snapshots = versionRepository.findSnapshots(docId);
    return new DocVersionList(snapshots.stream().map(DocVersionView::of).toList(), snapshots.size());
  }

  /** 单版快照（version=0 取草稿工作副本，仅可编辑者，余者 40302）。 */
  public DocVersionView get(SessionPrincipal principal, long docId, int version) {
    Doc doc = access.requireReadableDoc(principal, docId);
    if (version == DocVersion.DRAFT_VERSION && !access.canEdit(doc, principal)) {
      throw ApiException.dataForbidden("无权读取该文档的草稿正文。");
    }
    DocVersion found = versionRepository.findByDocAndVersion(docId, version)
        .orElseThrow(() -> ApiException.notFound("文档版本"));
    return DocVersionView.of(found);
  }
}
