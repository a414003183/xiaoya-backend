package net.zentao.project.domain;

import java.util.List;
import java.util.Map;

/** 白名单仓储（project 卡 §2：acl_entry 是各实体白名单唯一真源）。 */
public interface AclEntryRepository {

  List<String> accounts(String objectType, long objectId);

  /** 批量取白名单（列表页避免 N+1）。 */
  Map<Long, List<String>> accountsOf(String objectType, List<Long> objectIds);

  /** 全量替换（diff 落库：新增缺的、删掉多的）。 */
  void replace(String objectType, long objectId, List<String> accounts);
}
