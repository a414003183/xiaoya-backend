package net.zentao.platform.langimport;

/**
 * 语言包上传结果（platform 卡 §3.12；列存规范码，界面文案走 i18n）。
 * 枚举常量刻意小写：名字即 wire 值与 DB 列值（同 {@code net.zentao.platform.session.AccountStatus} 口径）。
 */
public enum LangImportStatus {
  success,
  failed
}
