package net.zentao.org.api;

/** 成员视图（contract：PersonnelMemberView；org 卡 §5 Personnel 节，无表只读聚合）。 */
public record PersonnelMemberView(
    String account,
    String realName,
    Long departmentId,
    java.util.List<Long> roleIds,
    long openTaskCount,
    long unresolvedBugCount) {}
