package net.zentao.project.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 干系人仓储（domain 接口；实现 infra，A1）。objectType ∈ program|project。 */
public interface StakeholderRepository {

  Optional<Stakeholder> findActiveById(long id);

  /** 同对象同账号的未删行（重复添加 → 42201 的判定依据）。 */
  Optional<Stakeholder> findActiveByAccount(String objectType, long objectId, String account);

  /** 新增干系人；同键存在软删行时复活该行（§3.8 软删后再添加同 account 成功）。 */
  Stakeholder insert(Stakeholder stakeholder);

  void softDelete(long id, String actor);

  /** 某账号作为干系人的对象 id 集（按 object_type 分组；§7 项目集/项目可见性追加集）。 */
  Map<String, Set<Long>> objectsOf(String account);

  List<Stakeholder> queryPage(Object whereWrapper, int offset, int limit);

  long countByQuery(Object whereWrapper);
}
