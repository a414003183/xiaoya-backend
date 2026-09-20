package net.zentao.project.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 团队成员仓储（domain 接口；实现 infra，A1）。objectType ∈ project|execution。 */
public interface TeamMemberRepository {

  /** 对象成员表（未删行，按 sort/id 升序）——全量提交的 diff 基准。 */
  List<TeamMember> findActive(String objectType, long objectId);

  /** 新增成员；同键存在软删行时复活该行（unique 索引占键，不插新行）。 */
  TeamMember insert(TeamMember member);

  Optional<TeamMember> update(TeamMember member);

  void softDelete(long id, String actor);

  /** 某账号作为团队成员的对象 id 集（按 object_type 分组；§7 项目/执行可见性追加集）。 */
  Map<String, Set<Long>> objectsOf(String account);

  List<TeamMember> queryPage(Object whereWrapper, int offset, int limit);

  long countByQuery(Object whereWrapper);
}
