package net.zentao.doc.domain;

import java.util.List;
import java.util.Optional;

/** 正文快照仓储（doc 卡 §3.3）：v0 覆盖写，v>=1 只增不改。 */
public interface DocVersionRepository {

  Optional<DocVersion> findByDocAndVersion(long docId, int version);

  /** 某文档的发布快照（v>=1），固定 version desc，不分页。 */
  List<DocVersion> findSnapshots(long docId);

  /** 批量取发布快照（列表派生 hasDraft 用，避免 N+1）。 */
  List<DocVersion> findSnapshotsOf(List<Long> docIds);

  /** 批量取工作副本（列表派生 hasDraft 用，避免 N+1）。 */
  List<DocVersion> findDrafts(List<Long> docIds);

  void insert(DocVersion version);

  /** 覆盖写 v0 工作副本（不产生新行）。 */
  void updateDraft(DocVersion draft);
}
