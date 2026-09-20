package net.zentao.quality.infra;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import net.zentao.quality.domain.Report;
import net.zentao.quality.domain.ReportRepository;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** 测试报告仓储实现（infra：PO ↔ 领域对象；test_run_ids JSON 文本列）。 */
@Component
public class ReportRepositoryImpl implements ReportRepository {

  private static final QueryColumn DELETED_AT = new QueryColumn("deleted_at");
  private static final TypeReference<List<Long>> LONG_LIST = new TypeReference<>() {};

  private final ReportMapper mapper;
  private final JsonMapper jsonMapper;

  public ReportRepositoryImpl(ReportMapper mapper, JsonMapper jsonMapper) {
    this.mapper = mapper;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Optional<Report> findActiveById(long id) {
    return Optional.ofNullable(mapper.selectOneByCondition(new QueryColumn("id").eq(id).and(DELETED_AT.isNull())))
        .map(this::toDomain);
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<Report> queryPage(Object whereWrapper, int offset, int limit) {
    return mapper.selectListByQuery(((QueryWrapper) whereWrapper).limit(offset, limit)).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public long countByQuery(Object whereWrapper) {
    return mapper.selectCountByQuery((QueryWrapper) whereWrapper);
  }

  @Override
  public Report insert(Report report) {
    ReportPO po = toPo(report);
    po.setId(null);
    po.setLockVersion(0);
    mapper.insert(po);
    return toDomain(mapper.selectOneById(po.getId()));
  }

  @Override
  public void softDelete(long id, String actor, Instant at) {
    Db.updateByCondition("test_report", Row.of("deleted_at", at).set("updated_by", actor),
        new QueryColumn("id").eq(id).and(DELETED_AT.isNull()));
  }

  @Override
  public Optional<Report> update(Report report) {
    // 全量覆盖（含 null）；回读行取库内自增后的 lockVersion
    if (mapper.update(toPo(report), false) <= 0) {
      return Optional.empty();
    }
    return findActiveById(report.id());
  }

  private Report toDomain(ReportPO po) {
    return new Report(
        po.getId(),
        po.getExecutionId(),
        po.getProjectId() == null ? 0 : po.getProjectId(),
        po.getProductId() == null ? 0 : po.getProductId(),
        po.getTitle(),
        readLongList(po.getTestRunIds()),
        po.getBeginDate(),
        po.getEndDate(),
        po.getOwner(),
        po.getContent(),
        po.getCreatedBy(),
        po.getCreatedAt(),
        po.getUpdatedBy(),
        po.getUpdatedAt(),
        po.getLockVersion() == null ? 0 : po.getLockVersion());
  }

  private ReportPO toPo(Report report) {
    ReportPO po = new ReportPO();
    po.setId(report.id() == 0 ? null : report.id());
    po.setExecutionId(report.executionId());
    po.setProjectId(report.projectId());
    po.setProductId(report.productId());
    po.setTitle(report.title());
    po.setTestRunIds(report.testRunIds() == null || report.testRunIds().isEmpty()
        ? null : jsonMapper.writeValueAsString(report.testRunIds()));
    po.setBeginDate(report.beginDate());
    po.setEndDate(report.endDate());
    po.setOwner(report.owner());
    po.setContent(report.content());
    po.setCreatedBy(report.createdBy());
    po.setCreatedAt(report.createdAt());
    po.setUpdatedBy(report.updatedBy());
    po.setUpdatedAt(report.updatedAt());
    po.setLockVersion(report.lockVersion());
    return po;
  }

  private List<Long> readLongList(String json) {
    return json == null || json.isBlank() ? List.of() : jsonMapper.readValue(json, LONG_LIST);
  }
}
