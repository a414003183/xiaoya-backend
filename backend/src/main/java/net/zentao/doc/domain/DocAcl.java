package net.zentao.doc.domain;

import java.util.List;

/** 库/文档白名单载荷（doc 卡 §3.1/§3.2：accounts + groupIds，各 ≤50，三处同构）。 */
public record DocAcl(List<String> accounts, List<Long> groupIds) {

  public static final DocAcl EMPTY = new DocAcl(List.of(), List.of());

  public DocAcl {
    accounts = accounts == null ? List.of() : List.copyOf(accounts);
    groupIds = groupIds == null ? List.of() : List.copyOf(groupIds);
  }

  /** 命中判定（doc 卡 §7）：账号直接命中或所属组命中；组关系按判定时刻的成员关系生效，不做快照。 */
  public boolean matches(String account, List<Long> accountGroups) {
    if (account != null && accounts.contains(account)) {
      return true;
    }
    return accountGroups != null && accountGroups.stream().anyMatch(groupIds::contains);
  }
}
