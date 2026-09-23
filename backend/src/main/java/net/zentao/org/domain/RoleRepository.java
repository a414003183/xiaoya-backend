package net.zentao.org.domain;

import java.util.List;
import java.util.Optional;

/** 角色仓储接口（domain 层；权限码与成员子表经实现层读写）。 */
public interface RoleRepository {

  Optional<Role> findById(long id);

  Optional<Role> findByName(String name);

  List<Role> findAll();

  Role insert(Role role);

  Optional<Role> update(Role role);

  void delete(long id);

  List<Long> memberIdsOf(long roleId);

  void replaceMembers(long roleId, List<Long> accountIds);

  List<String> privCodesOf(long roleId);

  void replacePrivCodes(long roleId, List<String> codes);

  /** 角色 id → 权限码并集（登录会话要一次算出账号的全部权限码）。 */
  List<String> privCodesOfRoles(List<Long> roleIds);

  /** 角色 id → 权限码数量（列表页的「权限数」列，避免 N+1 次子查询）。 */
  long countPrivCodes(long roleId);

  /** 角色 id → 成员数。 */
  long countMembers(long roleId);
}
