package net.zentao.project.infra;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.zentao.project.domain.AclEntryRepository;
import net.zentao.project.domain.Project;
import net.zentao.project.domain.ProjectRepository;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** 项目仓储实现（infra：PO ↔ 领域对象；白名单经 acl_entry 单源装配）。 */
@Component
public class ProjectRepositoryImpl implements ProjectRepository {

  private static final QueryColumn DELETED_AT = new QueryColumn("deleted_at");
  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

  private final ProjectMapper mapper;
  private final AclEntryRepository aclEntryRepository;
  private final JsonMapper jsonMapper;

  public ProjectRepositoryImpl(ProjectMapper mapper, AclEntryRepository aclEntryRepository, JsonMapper jsonMapper) {
    this.mapper = mapper;
    this.aclEntryRepository = aclEntryRepository;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Optional<Project> findActiveById(long id) {
    return Optional.ofNullable(mapper.selectOneByCondition(new QueryColumn("id").eq(id).and(DELETED_AT.isNull())))
        .map(po -> toDomain(po, aclEntryRepository.accounts(po.getType(), po.getId())));
  }

  @Override
  public List<Project> findActiveByIds(List<Long> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return withWhitelists(mapper.selectListByCondition(new QueryColumn("id").in(ids).and(DELETED_AT.isNull())));
  }

  @Override
  public List<Project> findAllActive() {
    return withWhitelists(mapper.selectListByCondition(DELETED_AT.isNull()));
  }

  @Override
  public Project insert(Project project) {
    ProjectPO po = toPo(project);
    po.setId(null);
    po.setLockVersion(0);
    mapper.insert(po);
    return findActiveById(po.getId()).orElseThrow();
  }

  @Override
  public Optional<Project> update(Project project) {
    // 全量覆盖（含 null 字段）：聚合持有完整状态，清空字段（如 activate 清 closedAt）必须落库。
    // 回读行：lockVersion 由库内自增，前端下一次 PATCH 要用最新值（旧实现回内存聚合会给出过期版本）。
    if (mapper.update(toPo(project), false) <= 0) {
      return Optional.empty();
    }
    return findActiveById(project.id());
  }

  @Override
  public void updatePath(long id, String path, int grade) {
    // 免乐观锁路径（创建后 id 回填）：Flex 的实体 update 要求带 lockVersion，故走 Row 条件更新
    Db.updateByCondition("project", Row.of("path", path).set("grade", grade), new QueryColumn("id").eq(id));
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<Project> queryPage(Object whereWrapper, int offset, int limit) {
    return withWhitelists(mapper.selectListByQuery(((QueryWrapper) whereWrapper).limit(offset, limit)));
  }

  @Override
  public long countByQuery(Object whereWrapper) {
    return mapper.selectCountByQuery((QueryWrapper) whereWrapper);
  }

  @Override
  public long countActiveChildren(long parentId) {
    return mapper.selectCountByQuery(QueryWrapper.create()
        .where(new QueryColumn("parent_id").eq(parentId).and(DELETED_AT.isNull())));
  }

  @Override
  public void softDelete(long id) {
    Db.updateByCondition("project", Row.of("deleted_at", Instant.now()), new QueryColumn("id").eq(id));
  }

  private List<Project> withWhitelists(List<ProjectPO> rows) {
    if (rows.isEmpty()) {
      return List.of();
    }
    Map<Long, List<String>> byProject = aclEntryRepository.accountsOf("project",
        rows.stream().filter(po -> "project".equals(po.getType())).map(ProjectPO::getId).toList());
    Map<Long, List<String>> byProgram = aclEntryRepository.accountsOf("program",
        rows.stream().filter(po -> "program".equals(po.getType())).map(ProjectPO::getId).toList());
    Map<Long, List<String>> byExecution = aclEntryRepository.accountsOf("execution",
        rows.stream().filter(po -> !"program".equals(po.getType()) && !"project".equals(po.getType()))
            .map(ProjectPO::getId).toList());
    return rows.stream()
        .map(po -> toDomain(po, switch (po.getType()) {
          case "program" -> byProgram.getOrDefault(po.getId(), List.of());
          case "project" -> byProject.getOrDefault(po.getId(), List.of());
          default -> byExecution.getOrDefault(po.getId(), List.of());
        }))
        .toList();
  }

  private Project toDomain(ProjectPO po, List<String> whitelist) {
    return new Project(po.getId(), po.getType(), po.getParentId() == null ? 0 : po.getParentId(), po.getPath(),
        po.getGrade() == null ? 1 : po.getGrade(), po.getName(), po.getCode(), po.getModel(), po.getStatus(),
        po.getPriority() == null ? 1 : po.getPriority(), po.getBeginDate(), po.getEndDate(), po.getFirstEndDate(),
        po.getRealBeganDate(), po.getRealEndDate(), po.getDays() == null ? 0 : po.getDays(), po.getBudget(),
        po.getBudgetUnit(), po.getDescription(), po.getPm(),
        po.getPo(), po.getQd(), po.getRd(), po.getProgress() == null ? 0 : po.getProgress(),
        po.getEstimateHours(), po.getConsumedHours(), po.getLeftHours(),
        po.getIsMilestone() != null && po.getIsMilestone() == 1, po.getAcl(), whitelist,
        po.getSort() == null ? 0 : po.getSort(), readMap(po.getCustomFields()), po.getCreatedBy(), po.getCreatedAt(),
        po.getUpdatedBy(), po.getUpdatedAt(), po.getClosedBy(), po.getClosedAt(),
        po.getLockVersion() == null ? 0 : po.getLockVersion());
  }

  private ProjectPO toPo(Project project) {
    ProjectPO po = new ProjectPO();
    po.setId(project.id() == 0 ? null : project.id());
    po.setType(project.type());
    po.setParentId(project.parentId());
    po.setPath(project.path());
    po.setGrade(project.grade());
    po.setName(project.name());
    po.setCode(project.code());
    po.setModel(project.model());
    po.setStatus(project.status());
    po.setPriority(project.priority());
    po.setBeginDate(project.beginDate());
    po.setEndDate(project.endDate());
    po.setFirstEndDate(project.firstEndDate());
    po.setRealBeganDate(project.realBeganDate());
    po.setRealEndDate(project.realEndDate());
    po.setDays(project.days());
    po.setBudget(project.budget());
    po.setBudgetUnit(project.budgetUnit());
    po.setDescription(project.description());
    po.setPm(project.pm());
    po.setPo(project.po());
    po.setQd(project.qd());
    po.setRd(project.rd());
    po.setProgress(project.progress());
    po.setEstimateHours(project.estimateHours());
    po.setConsumedHours(project.consumedHours());
    po.setLeftHours(project.leftHours());
    po.setIsMilestone(project.isMilestone() ? 1 : 0);
    po.setAcl(project.acl());
    po.setSort(project.sort());
    po.setCustomFields(writeMap(project.customFields()));
    po.setCreatedBy(project.createdBy());
    po.setCreatedAt(project.createdAt());
    po.setUpdatedBy(project.updatedBy());
    po.setUpdatedAt(project.updatedAt());
    po.setClosedBy(project.closedBy());
    po.setClosedAt(project.closedAt());
    po.setLockVersion(project.lockVersion());
    return po;
  }

  private Map<String, Object> readMap(String json) {
    return json == null || json.isBlank() ? Map.of() : jsonMapper.readValue(json, MAP);
  }

  private String writeMap(Map<String, Object> value) {
    return value == null || value.isEmpty() ? null : jsonMapper.writeValueAsString(value);
  }
}
