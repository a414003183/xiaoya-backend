package net.zentao.org.domain;

import java.util.List;
import java.util.Optional;

/** 账号角色字典仓储接口（domain 层）。 */
public interface AccountRoleRepository {

  Optional<AccountRole> findByCode(String code);

  boolean existsByCode(String code);

  /** 全量按 sort 升序（字典级小列表，不分页）。 */
  List<AccountRole> findAll();

  AccountRole insert(AccountRole role);

  /** 乐观锁更新（lockVersion 不符返回 empty，由 app 层转 40901）。 */
  Optional<AccountRole> update(AccountRole role);

  void delete(String code);

  /** 下一个排序位（新建缺省排到末尾）。 */
  int nextSort();
}
