package net.zentao.org.api;

import java.util.List;
import java.util.Optional;
import net.zentao.platform.session.AccountView;

/** 账号域对外接口（A2：跨域只经本包；platform 经网关方向，业务域经此读账号信息）。 */
public interface AccountApi {

  Optional<AccountView> view(long accountId);

  List<AccountView> viewsByIds(List<Long> accountIds);

  /** 账号存在性校验（各域 po/qd/rd/assignee/reviewers/whitelist 等引用字段）：返回其中不存在的账号。 */
  List<String> missingAccounts(List<String> accounts);

  /**
   * 启用账号的登录名列表（org 卡 §5 Personnel 节取数）：{@code departmentId} 非空时含其后代部门；
   * 停用/软删账号不返回。
   */
  List<String> enabledAccountsOf(Long departmentId);
}
