package net.zentao.workspace.api;

/** 地盘首页计数（contract：MySummaryView；workspace 卡 §3.4 固定 role=assignee 口径）。 */
public record MySummaryView(long todoCount, long taskCount, long bugCount, long storyCount) {}
