package net.zentao.project.infra;

import com.mybatisflex.core.query.QueryColumn;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.zentao.project.domain.AclEntryRepository;
import org.springframework.stereotype.Component;

/** 白名单仓储实现（acl_entry 单源；entry_type=whitelist，project 卡 §2）。 */
@Component
public class AclEntryRepositoryImpl implements AclEntryRepository {

  private static final String WHITELIST = "whitelist";

  private final AclEntryMapper mapper;

  public AclEntryRepositoryImpl(AclEntryMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public List<String> accounts(String objectType, long objectId) {
    return mapper.selectListByCondition(condition(objectType, objectId)).stream()
        .map(AclEntryPO::getAccount)
        .toList();
  }

  @Override
  public Map<Long, List<String>> accountsOf(String objectType, List<Long> objectIds) {
    if (objectIds.isEmpty()) {
      return Map.of();
    }
    Map<Long, List<String>> byObject = new LinkedHashMap<>();
    for (AclEntryPO po : mapper.selectListByCondition(
        new QueryColumn("object_type").eq(objectType).and(new QueryColumn("object_id").in(objectIds)))) {
      byObject.computeIfAbsent(po.getObjectId(), id -> new java.util.ArrayList<>()).add(po.getAccount());
    }
    return byObject;
  }

  @Override
  public void replace(String objectType, long objectId, List<String> accounts) {
    Set<String> target = new LinkedHashSet<>(accounts);
    List<AclEntryPO> existing = mapper.selectListByCondition(condition(objectType, objectId));
    for (AclEntryPO po : existing) {
      if (!target.contains(po.getAccount())) {
        mapper.deleteById(po.getId());
      }
    }
    Set<String> kept = existing.stream().map(AclEntryPO::getAccount).collect(java.util.stream.Collectors.toSet());
    for (String account : target) {
      if (!kept.contains(account)) {
        AclEntryPO po = new AclEntryPO();
        po.setObjectType(objectType);
        po.setObjectId(objectId);
        po.setAccount(account);
        po.setEntryType(WHITELIST);
        mapper.insert(po);
      }
    }
  }

  private static com.mybatisflex.core.query.QueryCondition condition(String objectType, long objectId) {
    return new QueryColumn("object_type").eq(objectType)
        .and(new QueryColumn("object_id").eq(objectId))
        .and(new QueryColumn("entry_type").eq(WHITELIST));
  }
}
