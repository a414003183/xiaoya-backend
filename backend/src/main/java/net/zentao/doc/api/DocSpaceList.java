package net.zentao.doc.api;

import java.util.List;

/** 文档库列表载荷（contract：DocSpaceList）。 */
public record DocSpaceList(List<DocSpaceView> items, long total) {}
