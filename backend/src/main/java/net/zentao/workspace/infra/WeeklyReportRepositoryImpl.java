package net.zentao.workspace.infra;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.zentao.workspace.domain.WeeklyReport;
import net.zentao.workspace.domain.WeeklyReportRepository;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** 周报快照仓储实现（infra：PO ↔ 领域对象；workload 为 JSON 文本列，UNIQUE 兜底 upsert 幂等）。 */
@Component
public class WeeklyReportRepositoryImpl implements WeeklyReportRepository {

  private static final TypeReference<Map<String, BigDecimal>> WORKLOAD = new TypeReference<>() {};

  private final WeeklyReportMapper mapper;
  private final JsonMapper jsonMapper;

  public WeeklyReportRepositoryImpl(WeeklyReportMapper mapper, JsonMapper jsonMapper) {
    this.mapper = mapper;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Optional<WeeklyReport> find(long projectId, LocalDate weekStart) {
    return Optional.ofNullable(mapper.selectOneByCondition(
        new QueryColumn("project_id").eq(projectId).and(new QueryColumn("week_start").eq(weekStart))))
        .map(this::toDomain);
  }

  @Override
  public WeeklyReport upsert(WeeklyReport report) {
    WeeklyReportPO existing = mapper.selectOneByCondition(
        new QueryColumn("project_id").eq(report.projectId()).and(new QueryColumn("week_start").eq(report.weekStart())));
    WeeklyReportPO po = toPo(report);
    if (existing == null) {
      po.setId(null);
      mapper.insert(po);
    } else {
      po.setId(existing.getId());
      mapper.update(po, false);
    }
    return find(report.projectId(), report.weekStart()).orElseThrow();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<WeeklyReport> queryPage(Object whereWrapper, int offset, int limit) {
    return mapper.selectListByQuery(((QueryWrapper) whereWrapper).limit(offset, limit)).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public long countByQuery(Object whereWrapper) {
    return mapper.selectCountByQuery((QueryWrapper) whereWrapper);
  }

  private WeeklyReport toDomain(WeeklyReportPO po) {
    return new WeeklyReport(po.getId(), po.getProjectId(), po.getWeekStart(), po.getPv(), po.getEv(), po.getAc(),
        po.getSv(), po.getCv(), po.getStaff() == null ? 0 : po.getStaff(), readWorkload(po.getWorkload()),
        po.getUpdatedAt());
  }

  private WeeklyReportPO toPo(WeeklyReport report) {
    WeeklyReportPO po = new WeeklyReportPO();
    po.setProjectId(report.projectId());
    po.setWeekStart(report.weekStart());
    po.setPv(report.pv());
    po.setEv(report.ev());
    po.setAc(report.ac());
    po.setSv(report.sv());
    po.setCv(report.cv());
    po.setStaff(report.staff());
    po.setWorkload(report.workload().isEmpty() ? null : jsonMapper.writeValueAsString(report.workload()));
    po.setUpdatedAt(report.updatedAt() == null ? Instant.now() : report.updatedAt());
    return po;
  }

  private Map<String, BigDecimal> readWorkload(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    return new LinkedHashMap<>(jsonMapper.readValue(json, WORKLOAD));
  }
}
