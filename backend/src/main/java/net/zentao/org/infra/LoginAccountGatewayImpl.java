package net.zentao.org.infra;

import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;
import com.mybatisflex.core.query.QueryColumn;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.AccountStatus;
import net.zentao.platform.session.AccountView;
import net.zentao.platform.session.Gender;
import net.zentao.platform.session.LoginAccountGateway;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * platform 登录网关的 org 实现（T-1/T-11）：读 account 表 + BCrypt 验密 + 登录锁定。
 */
@Component
public class LoginAccountGatewayImpl implements LoginAccountGateway {

  private static final String TABLE = "account";
  private static final QueryColumn ACCOUNT = new QueryColumn("account");

  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
  private final net.zentao.org.app.PasswordActionHandler passwordActionHandler;
  private final net.zentao.org.domain.AccountRepository accountRepository;

  public LoginAccountGatewayImpl(net.zentao.org.app.PasswordActionHandler passwordActionHandler,
      net.zentao.org.domain.AccountRepository accountRepository) {
    this.passwordActionHandler = passwordActionHandler;
    this.accountRepository = accountRepository;
  }

  @Override
  public AccountView verifyLogin(String account, String rawPassword) {
    Row row = Db.selectOneByCondition(TABLE, ACCOUNT.eq(account).and(new QueryColumn("deleted_at").isNull()));
    if (row == null) {
      throw ApiException.unauthenticated("账号或密码错误。");
    }
    var domain = accountRepository.findByAccount(account).orElseThrow();
    // 锁定窗口内直接拒绝（超时自动解除，不清列——org 卡 §4）
    passwordActionHandler.requireNotLocked(domain);
    if (!passwordEncoder.matches(rawPassword, row.getString("password"))) {
      passwordActionHandler.registerFailure(domain);
      throw ApiException.unauthenticated("账号或密码错误。");
    }
    if (!"active".equals(row.getString("status"))) {
      throw ApiException.unauthenticated("账号已停用。");
    }
    passwordActionHandler.registerSuccess(domain);
    return toView(row);
  }

  @Override
  public AccountView view(long accountId) {
    Row row = Db.selectOneByCondition(TABLE, new QueryColumn("id").eq(accountId).and(new QueryColumn("deleted_at").isNull()));
    return row == null ? null : toView(row);
  }

  private AccountView toView(Row row) {
    long id = row.getLong("id");
    List<Long> groupIds = Db.selectListByCondition("user_group", new QueryColumn("account_id").eq(id)).stream()
        .map(member -> member.getLong("group_id"))
        .toList();
    return new AccountView(
        id,
        row.getString("account"),
        row.getString("real_name"),
        row.getString("nickname"),
        row.getString("role"),
        row.getLong("department_id"),
        row.getString("email"),
        row.getString("mobile"),
        row.getString("phone"),
        enumOrNull(Gender.class, row.getString("gender")),
        localDateOrNull(row, "birthday"),
        localDateOrNull(row, "joined_at"),
        row.getLong("avatar_file_id"),
        enumOrNull(AccountStatus.class, row.getString("status")),
        row.getInt("must_change_password") == 1,
        groupIds,
        row.getInt("fails"),
        instantOrNull(row, "locked_at"),
        instantOrNull(row, "last_active_at"),
        row.getString("created_by"),
        instantOrNull(row, "created_at"),
        row.getString("updated_by"),
        instantOrNull(row, "updated_at"),
        instantOrNull(row, "deleted_at"),
        row.getInt("lock_version"));
  }

  private static <E extends Enum<E>> E enumOrNull(Class<E> type, String value) {
    if (value == null) {
      return null;
    }
    try {
      return Enum.valueOf(type, value);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static Instant instantOrNull(Row row, String column) {
    var localDateTime = row.getLocalDateTime(column);
    return localDateTime == null ? null : localDateTime.atZone(ZoneId.systemDefault()).toInstant();
  }

  private static LocalDate localDateOrNull(Row row, String column) {
    java.util.Date date = row.getDate(column);
    return date == null ? null : date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
  }
}
