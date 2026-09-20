package net.zentao.platform.session;

/** 已解析的当前会话身份。 */
public record SessionPrincipal(long accountId, String account) {}
