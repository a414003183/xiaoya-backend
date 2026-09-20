package net.zentao.platform.meta;

import java.util.List;
import java.util.Map;

/** 计算字典 provider（platform 卡 §3.9）：无表，每次实时计算，缓存由前端 staleTime 控制。 */
public interface DictProvider {

  /** 字典名（accounts/departments/timezones/locales/privileges…）。 */
  String name();

  /** 字典条目，结构随 name 变化（如 {account, realName} / {id, name, parentId} / {value, label}）。 */
  List<Map<String, Object>> items();
}
