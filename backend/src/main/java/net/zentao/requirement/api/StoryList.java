package net.zentao.requirement.api;

import java.util.List;

/** 需求分页载荷（contract：StoryList = items + total）。 */
public record StoryList(List<StoryView> items, long total) {}
