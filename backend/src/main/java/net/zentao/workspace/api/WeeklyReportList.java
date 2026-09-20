package net.zentao.workspace.api;

import java.util.List;

/** 周报分页列表（contract：WeeklyReportList）。 */
public record WeeklyReportList(List<WeeklyReportView> items, long total) {}
