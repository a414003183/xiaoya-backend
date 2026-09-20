package net.zentao.org.api;

import java.util.List;

/** 成员分页列表（contract：PersonnelMemberList）。 */
public record PersonnelMemberList(List<PersonnelMemberView> items, long total) {}
