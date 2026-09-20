package net.zentao.doc.api;

import java.util.List;

/** 文档列表载荷（contract：DocList；items 不携带 content/files）。 */
public record DocList(List<DocView> items, long total) {}
