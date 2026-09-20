package net.zentao.platform.session;

import java.util.List;

/** 当前会话快照（03 §7）：privileges 显隐前端只读它，真鉴权在后端；dictionaries 2026-09-19 移除（恒空无消费者）。 */
public record MeView(AccountView account, List<String> privileges) {}
