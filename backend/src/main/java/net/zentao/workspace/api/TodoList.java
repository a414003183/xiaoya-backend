package net.zentao.workspace.api;

import java.util.List;

/** 待办分页列表（contract：TodoList）。 */
public record TodoList(List<TodoView> items, long total) {}
