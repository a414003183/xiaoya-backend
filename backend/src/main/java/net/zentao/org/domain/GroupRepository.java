package net.zentao.org.domain;

import java.util.List;
import java.util.Optional;

/** 权限组仓储接口（domain 层；矩阵与成员子表经实现层读写）。 */
public interface GroupRepository {

  Optional<Group> findById(long id);

  Optional<Group> findByName(String name);

  List<Group> findAll();

  Group insert(Group group);

  Optional<Group> update(Group group);

  void delete(long id);

  List<Long> memberIdsOf(long groupId);

  void replaceMembers(long groupId, List<Long> accountIds);

  List<String> privCodesOf(long groupId);

  void replacePrivCodes(long groupId, List<String> codes);
}
