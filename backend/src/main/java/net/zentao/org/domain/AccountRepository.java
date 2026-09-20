package net.zentao.org.domain;

import java.util.List;
import java.util.Optional;

/** 账号仓储接口（domain 层，纯 Java）。groupIds 属 user_group 关联，经组集合方法读写。 */
public interface AccountRepository {

  Optional<Account> findById(long id);

  /** 未删账号。 */
  Optional<Account> findActiveById(long id);

  /** 按登录名取未删账号。 */
  Optional<Account> findByAccount(String account);

  List<Account> findAllVisible();

  Account insert(Account account);

  /** 乐观锁更新；版本不符返回空。 */
  Optional<Account> update(Account account);

  boolean existsByAccount(String account);

  boolean existsByAccountAndDepartment(String account, long departmentId);

  // ── user_group 关联 ──

  List<Long> groupIdsOf(long accountId);

  void replaceGroups(long accountId, List<Long> groupIds);

  /** 批量校验：返回不存在的组 id。 */
  List<Long> findMissingGroupIds(List<Long> groupIds);

  /**
   * 组合查询（DSL 编译产物）。ponytail: 参数以 Object 承载（实现层强转），
   * 避免 domain 接口泄漏 ORM 类型（A1）；升级路径 = 引入查询规约对象。
   */
  List<Account> queryPage(Object whereWrapper, int offset, int limit);

  long countByQuery(Object whereWrapper);

  /** 仍在使用该角色的账号数（未删口径；角色字典的 accountCount 与删除守卫共用，org 卡 §3.4）。 */
  long countByRole(String role);
}
