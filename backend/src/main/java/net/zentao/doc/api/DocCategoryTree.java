package net.zentao.doc.api;

import java.util.List;

/** 目录树载荷（contract：DocCategoryTree）。 */
public record DocCategoryTree(List<DocCategoryNode> items) {}
