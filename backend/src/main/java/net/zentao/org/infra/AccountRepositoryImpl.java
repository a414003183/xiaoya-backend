package net.zentao.org.infra;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.zentao.org.domain.Account;
import net.zentao.org.domain.AccountRepository;
import org.springframework.stereotype.Component;

/** 账号仓储实现（infra：PO ↔ 领域对象；user_role 关联经 Row API 批量读写）。 */
@Component
public class AccountRepositoryImpl implements AccountRepository {

  private final AccountMapper mapper;

  public AccountRepositoryImpl(AccountMapper mapper) {
    this.mapper = mapper;
  }

  private static final QueryColumn DELETED_AT = new QueryColumn("deleted_at");

  @Override
  public Optional<Account> findById(long id) {
    return Optional.ofNullable(mapper.selectOneById(id)).map(AccountRepositoryImpl::toDomain);
  }

  @Override
  public Optional<Account> findActiveById(long id) {
    return Optional.ofNullable(mapper.selectOneByCondition(
        new QueryColumn("id").eq(id).and(DELETED_AT.isNull()))).map(AccountRepositoryImpl::toDomain);
  }

  @Override
  public Optional<Account> findByAccount(String account) {
    return Optional.ofNullable(mapper.selectOneByCondition(
            new QueryColumn("account").eq(account).and(DELETED_AT.isNull())))
        .map(AccountRepositoryImpl::toDomain);
  }

  @Override
  public List<Account> findAllVisible() {
    return mapper.selectListByCondition(DELETED_AT.isNull()).stream().map(AccountRepositoryImpl::toDomain).toList();
  }

  @Override
  public Account insert(Account account) {
    AccountPO po = toPo(account);
    po.setId(null);
    po.setFails(0);
    po.setLockVersion(0);
    mapper.insert(po);
    return toDomain(mapper.selectOneById(po.getId()));
  }

  @Override
  public Optional<Account> update(Account account) {
    AccountPO po = toPo(account);
    // 全量覆盖（含 null 字段）：聚合持有完整状态，清空字段（如解锁清 locked_at）必须落库
    return mapper.update(po, false) > 0 ? Optional.of(account) : Optional.empty();
  }

  @Override
  public boolean existsByAccount(String account) {
    return mapper.selectCountByCondition(new QueryColumn("account").eq(account)) > 0;
  }

  @Override
  public boolean existsByAccountAndDepartment(String account, long departmentId) {
    return mapper.selectCountByCondition(new QueryColumn("account").eq(account)
        .and(new QueryColumn("department_id").eq(departmentId))) > 0;
  }

  @Override
  public List<Long> roleIdsOf(long accountId) {
    return Db.selectListByCondition("user_role", new QueryColumn("account_id").eq(accountId)).stream()
        .map(row -> row.getLong("role_id"))
        .toList();
  }

  @Override
  public void replaceRoles(long accountId, List<Long> roleIds) {
    Db.deleteByCondition("user_role", new QueryColumn("account_id").eq(accountId));
    for (Long roleId : roleIds.stream().distinct().toList()) {
      Db.insert("user_role", Row.of("account_id", accountId).set("role_id", roleId));
    }
  }

  @Override
  public List<Long> findMissingRoleIds(List<Long> roleIds) {
    if (roleIds.isEmpty()) {
      return List.of();
    }
    List<Long> unique = roleIds.stream().distinct().toList();
    List<Row> rows = Db.selectListByCondition("role", new QueryColumn("id").in(unique));
    List<Long> found = rows.stream().map(row -> row.getLong("id")).toList();
    List<Long> missing = new ArrayList<>(unique);
    missing.removeAll(found);
    return missing;
  }

  @Override
  public List<Long> roleMembersOf(long roleId) {
    return Db.selectListByCondition("user_role", new QueryColumn("role_id").eq(roleId)).stream()
        .map(row -> row.getLong("account_id"))
        .toList();
  }

  @Override
  public List<String> recentPasswordHashes(long accountId, int limit) {
    if (limit < 1) {
      return List.of();
    }
    return Db.selectListBySql(
            "SELECT password FROM password_history WHERE account_id = ? ORDER BY id DESC LIMIT " + limit, accountId)
        .stream().map(row -> row.getString("password")).toList();
  }

  @Override
  public void appendPasswordHistory(long accountId, String passwordHash, String actor, int keep) {
    Row row = Row.of("account_id", accountId).set("password", passwordHash);
    if (actor != null) {
      row.set("created_by", actor);
    }
    Db.insert("password_history", row);
    long stale = Db.selectCountByCondition("password_history", new QueryColumn("account_id").eq(accountId))
        - Math.max(keep, 0);
    if (stale <= 0) {
      return;
    }
    // 先取要丢的 id 再按 id 删：同一张表不做自引用子查询（MySQL/H2 对它的求值规则不同）
    List<Long> staleIds = Db.selectListBySql(
            "SELECT id FROM password_history WHERE account_id = ? ORDER BY id ASC LIMIT " + stale, accountId)
        .stream().map(staleRow -> staleRow.getLong("id")).toList();
    Db.deleteByCondition("password_history", new QueryColumn("id").in(staleIds));
  }

  private static Account toDomain(AccountPO po) {
    return new Account(po.getId(), po.getAccount(), po.getPassword(), po.getRealName(), po.getNickname(),
        po.getDepartmentId(), po.getEmail(), po.getMobile(), po.getPhone(), po.getGender(),
        po.getBirthday(), po.getJoinedAt(), po.getAvatarFileId(), po.getStatus(),
        Boolean.TRUE.equals(po.getMustChangePassword()),
        po.getFails() == null ? 0 : po.getFails(), po.getLockedAt(), po.getLastActiveAt(),
        po.getCreatedBy(), po.getCreatedAt(), po.getUpdatedBy(), po.getUpdatedAt(), po.getDeletedAt(),
        po.getLockVersion() == null ? 0 : po.getLockVersion());
  }

  private static AccountPO toPo(Account account) {
    AccountPO po = new AccountPO();
    po.setId(account.id() == 0 ? null : account.id());
    po.setAccount(account.account());
    po.setPassword(account.passwordHash());
    po.setRealName(account.realName());
    po.setNickname(account.nickname());
    po.setDepartmentId(account.departmentId());
    po.setEmail(account.email());
    po.setMobile(account.mobile());
    po.setPhone(account.phone());
    po.setGender(account.gender());
    po.setBirthday(account.birthday());
    po.setJoinedAt(account.joinedAt());
    po.setAvatarFileId(account.avatarFileId());
    po.setStatus(account.status());
    po.setMustChangePassword(account.mustChangePassword());
    po.setFails(account.fails());
    po.setLockedAt(account.lockedAt());
    po.setLastActiveAt(account.lastActiveAt());
    po.setCreatedBy(account.createdBy());
    po.setCreatedAt(account.createdAt());
    po.setUpdatedBy(account.updatedBy());
    po.setUpdatedAt(account.updatedAt());
    po.setDeletedAt(account.deletedAt());
    po.setLockVersion(account.lockVersion());
    return po;
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<Account> queryPage(Object whereWrapper, int offset, int limit) {
    return mapper.selectListByQuery(((QueryWrapper) whereWrapper).limit(offset, limit))
        .stream().map(AccountRepositoryImpl::toDomain).toList();
  }

  @Override
  public long countByQuery(Object whereWrapper) {
    return mapper.selectCountByQuery((QueryWrapper) whereWrapper);
  }

}
