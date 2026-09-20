package net.zentao.task.api;

import java.util.List;

/** 任务分页列表（contract：TaskList；P2 决策⑫ 先例：列表载荷归 api 包，供跨域聚合复用）。 */
public record TaskList(List<TaskView> items, long total) {}
