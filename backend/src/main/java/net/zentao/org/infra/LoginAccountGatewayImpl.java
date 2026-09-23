package net.zentao.org.infra;

import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;
import com.mybatisflex.core.query.QueryColumn;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
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

  private final BCryptPasswordEncoder passwordEncoder;

  /**
   * 未知账号的替身哈希（T58 SEC-12）：口令校验一律先跑满一次 BCrypt——「账号不存在」不再表现为一次快失败
   * （旧路径直接 401 省掉的那几十毫秒，正是可测的账号枚举计时差）。启动时现算一条随机口令的哈希，
   * 强度与真实口令同源（同一 bean，T62 起单实例），避免把魔法哈希字面量写进源码。
   */
  private final String absentAccountHash;

  private final net.zentao.org.app.PasswordActionHandler passwordActionHandler;
  private final net.zentao.org.domain.AccountRepository accountRepository;

  public LoginAccountGatewayImpl(net.zentao.org.app.PasswordActionHandler passwordActionHandler,
      net.zentao.org.domain.AccountRepository accountRepository, BCryptPasswordEncoder passwordEncoder) {
    this.passwordActionHandler = passwordActionHandler;
    this.accountRepository = accountRepository;
    this.passwordEncoder = passwordEncoder;
    this.absentAccountHash = passwordEncoder.encode(UUID.randomUUID().toString());
  }

  @Override
  public AccountView verifyLogin(String account, String rawPassword) {
    Row row = Db.selectOneByCondition(TABLE, ACCOUNT.eq(account).and(new QueryColumn("deleted_at").isNull()));
    // T58 SEC-12：先付 BCrypt（未知账号用替身哈希），再回答账号在不在——顺序一旦倒过来，
    // 「快失败」和下面「锁定文案」两处各自都是账号枚举预言机。
    boolean passwordMatches = passwordEncoder.matches(
        rawPassword, row == null ? absentAccountHash : row.getString("password"));
    if (row == null) {
      throw ApiException.keyed(ErrorCode.UNAUTHENTICATED, "account.login.invalid");
    }
    var domain = accountRepository.findByAccount(account).orElseThrow();
    if (!passwordMatches) {
      passwordActionHandler.registerFailure(domain);
      throw ApiException.keyed(ErrorCode.UNAUTHENTICATED, "account.login.invalid");
    }
    // 口令正确才回答锁定/停用（超时自动解除，不清列——org 卡 §4）：不对着口令错的人泄露账号状态
    passwordActionHandler.requireNotLocked(domain);
    if (!"active".equals(row.getString("status"))) {
      throw ApiException.keyed(ErrorCode.UNAUTHENTICATED, "account.login.disabled");
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
    List<Long> roleIds = Db.selectListByCondition("user_role", new QueryColumn("account_id").eq(id)).stream()
        .map(member -> member.getLong("role_id"))
        .toList();
    return new AccountView(
        id,
        row.getString("account"),
        row.getString("real_name"),
        row.getString("nickname"),
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
        roleIds,
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
