package net.zentao.org.api;

import java.util.List;

/** 工作量分页列表（contract：PersonnelWorkloadList）。 */
public record PersonnelWorkloadList(List<PersonnelWorkloadView> items, long total) {}
