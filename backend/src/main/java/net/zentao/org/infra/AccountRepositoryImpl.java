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

/** 账号仓储实现（infra：PO ↔ 领域对象；user_group 关联经 Row API 批量读写）。 */
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
  public List<Long> groupIdsOf(long accountId) {
    return Db.selectListByCondition("user_group", new QueryColumn("account_id").eq(accountId)).stream()
        .map(row -> row.getLong("group_id"))
        .toList();
  }

  @Override
  public void replaceGroups(long accountId, List<Long> groupIds) {
    Db.deleteByCondition("user_group", new QueryColumn("account_id").eq(accountId));
    for (Long groupId : groupIds.stream().distinct().toList()) {
      Db.insert("user_group", Row.of("account_id", accountId).set("group_id", groupId));
    }
  }

  @Override
  public List<Long> findMissingGroupIds(List<Long> groupIds) {
    if (groupIds.isEmpty()) {
      return List.of();
    }
    List<Long> unique = groupIds.stream().distinct().toList();
    List<Row> rows = Db.selectListByCondition("auth_group", new QueryColumn("id").in(unique));
    List<Long> found = rows.stream().map(row -> row.getLong("id")).toList();
    List<Long> missing = new ArrayList<>(unique);
    missing.removeAll(found);
    return missing;
  }

  private static Account toDomain(AccountPO po) {
    return new Account(po.getId(), po.getAccount(), po.getPassword(), po.getRealName(), po.getNickname(),
        po.getRole(), po.getDepartmentId(), po.getEmail(), po.getMobile(), po.getPhone(), po.getGender(),
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
    po.setRole(account.role());
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

  @Override
  public long countByRole(String role) {
    // 未删口径（deleted_at is null）：软删账号不再占用角色名，与 findAllVisible 同源
    return mapper.selectCountByCondition(
        new QueryColumn("role").eq(role).and(new QueryColumn("deleted_at").isNull()));
  }
}
