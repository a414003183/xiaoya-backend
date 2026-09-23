package net.zentao.org.domain;

import java.util.List;
import java.util.Optional;

/** 账号仓储接口（domain 层，纯 Java）。roleIds 属 user_role 关联，经角色集合方法读写。 */
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

  // ── user_role 关联（账号 ↔ 角色，T23）──

  List<Long> roleIdsOf(long accountId);

  void replaceRoles(long accountId, List<Long> roleIds);

  /** 批量校验：返回不存在的角色 id。 */
  List<Long> findMissingRoleIds(List<Long> roleIds);

  /** 角色成员账号 id 集（列表筛选 filters[roleId] 展开用）。 */
  List<Long> roleMembersOf(long roleId);

  // ── 口令历史（T62 SEC-11：禁用复用）──

  /** 最近 limit 条历史口令哈希（新→旧）；limit ≤ 0 返回空（历史未启用）。 */
  List<String> recentPasswordHashes(long accountId, int limit);

  /**
   * 追加一条被替换掉的口令哈希，并把该账号历史裁剪到最新 keep 条（keep ≤ 0 = 不保留，等于关闭）。
   * 只追加、无 UPDATE 路径，故不走乐观锁协议。
   */
  void appendPasswordHistory(long accountId, String passwordHash, String actor, int keep);

  /**
   * 组合查询（DSL 编译产物）。ponytail: 参数以 Object 承载（实现层强转），
   * 避免 domain 接口泄漏 ORM 类型（A1）；升级路径 = 引入查询规约对象。
   */
  List<Account> queryPage(Object whereWrapper, int offset, int limit);

  long countByQuery(Object whereWrapper);

}
