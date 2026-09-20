package net.zentao.doc.api;

import java.util.List;

/** 版本列表载荷（contract：DocVersionList；v>=1 快照，固定 version desc，不分页）。 */
public record DocVersionList(List<DocVersionView> items, long total) {}
