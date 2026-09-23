package net.zentao.platform.audit;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryWrapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import net.zentao.platform.filters.FieldRegistry;
import net.zentao.platform.filters.FilterPredicate;
import net.zentao.platform.filters.Filters;
import net.zentao.platform.filters.LikePatterns;
import org.springframework.stereotype.Component;

/**
 * 审计日志查询（B1 §H3 读侧；列表 DSL 见 03 §3）：流水只读，缺省 created_at/id 倒序。
 * 字段取值：account/action/objectType/objectId/category/result/batchId 等值或逗号 IN，createdAt 区间（含当日），
 * q 关键词 LIKE account/action；未注册字段过滤/排序 → 40001。
 *
 * <p>T04 加两件事：单行详情（带 changes/snapshot/extra，列表不带这三者——体积）与**哈希链校验**
 * （{@link #verify}，逐行重算，返回首处断链 id）。
 */
@Component
public class AuditLogQueryService {

  /** 单次链校验的扫描上限（默认 10000，硬上限 50000）：审计表是长的，工具端点不能无限扫。 */
  private static final int DEFAULT_VERIFY_ROWS = 10000;
  private static final int MAX_VERIFY_ROWS = 50000;

  /** filterable/sortable/searchable 白名单（契约同集声明，门禁三方对齐）。 */
  private static final FieldRegistry REGISTRY = FieldRegistry.allowing(
      Set.of("account", "action", "objectType", "objectId", "category", "result", "batchId", "createdAt"),
      Set.of("id", "createdAt"),
      Set.of("account", "action"));

  private static final Map<String, String> COLUMNS = Map.of(
      "id", "id",
      "account", "account",
      "action", "action",
      "category", "category",
      "result", "result",
      "batchId", "batch_id",
      "objectType", "object_type",
      "objectId", "object_id",
      "createdAt", "created_at");

  private static final QueryColumn ID = new QueryColumn("id");
  private static final QueryColumn ACCOUNT = new QueryColumn("account");
  private static final QueryColumn ACTION = new QueryColumn("action");
  private static final QueryColumn CREATED_AT = new QueryColumn("created_at");

  private final AuditLogRepository repository;
  private final AuditDiffer differ;

  public AuditLogQueryService(AuditLogRepository repository, AuditDiffer differ) {
    this.repository = repository;
    this.differ = differ;
  }

  /**
   * AuditLogView（契约同构）：列表行。category/result 是 V43 起的必填列（历史行已在迁移里回填），
   * 故契约把它们列为必填；changes/snapshot/extra 只在 {@link AuditLogDetail} 返回。
   */
  public record AuditLogView(
      long id,
      String account,
      String action,
      @Schema(allowableValues = {"auth", "perm", "config", "business", "batch", "export", "sensitive", "query",
          "approve"}) String category,
      @Schema(allowableValues = {"success", "fail", "denied"}) String result,
      String reason,
      String objectType,
      Long objectId,
      Long batchId,
      String detail,
      String ip,
      String ua,
      String device,
      String mfa,
      String traceId,
      Instant createdAt) {

    static AuditLogView of(AuditLogPO po) {
      return new AuditLogView(
          po.getId() == null ? 0 : po.getId(),
          po.getAccount(),
          po.getAction(),
          po.getCategory(),
          po.getResult(),
          po.getReason(),
          po.getObjectType(),
          po.getObjectId(),
          po.getBatchId(),
          po.getDetail(),
          po.getIp(),
          po.getUa(),
          po.getDevice(),
          po.getMfa(),
          po.getTraceId(),
          po.getCreatedAt());
    }
  }

  /** AuditLogList 载荷（items + total）。 */
  public record AuditLogList(List<AuditLogView> items, long total) {}

  /** AuditLogDetail（T04 详情端点）：列表行 + changes/snapshot/extra + 链哈希两列。 */
  public record AuditLogDetail(
      long id,
      String account,
      String action,
      @Schema(allowableValues = {"auth", "perm", "config", "business", "batch", "export", "sensitive", "query",
          "approve"}) String category,
      @Schema(allowableValues = {"success", "fail", "denied"}) String result,
      String reason,
      String objectType,
      Long objectId,
      Long batchId,
      String detail,
      List<AuditDiffer.AuditChange> changes,
      String snapshot,
      String extra,
      String ip,
      String ua,
      String device,
      String mfa,
      String traceId,
      String prevHash,
      String hash,
      Instant createdAt) {}

  /** AuditVerifyResult（T04 校验端点）：valid=false 时 brokenId 是首处断链行。 */
  public record AuditVerifyResult(
      boolean valid,
      long scanned,
      long unhashedPrefix,
      Long brokenId,
      Long checkedFrom,
      Long checkedTo,
      boolean scanLimitReached) {}

  public AuditLogList page(Map<String, String[]> params) {
    Filters filters = Filters.parse(params, REGISTRY);
    QueryCondition keyword = keywordCondition(filters.q());
    QueryWrapper query = FilterPredicate.compile(filters, COLUMNS::get, value -> Optional.empty(), keyword);
    if (filters.sortKeys().isEmpty()) {
      query = query.orderBy(CREATED_AT.desc(), ID.desc());  // banned-words-ok：MyBatis-Flex 构造器方法名，非请求参数
    }
    List<AuditLogView> items =
        repository.page(query, filters.offset(), filters.limit()).stream().map(AuditLogView::of).toList();
    // count 与 page 同条件（去 order/limit）
    QueryWrapper countQuery =
        FilterPredicate.compile(filters.forCount(), COLUMNS::get, value -> Optional.empty(), keyword);
    return new AuditLogList(items, repository.countByQuery(countQuery));
  }

  /** 单行详情；不存在 → 40401（与兄弟端点的 notFound 口径一致）。 */
  public AuditLogDetail detail(String auditId) {
    AuditLogPO po = repository.findById(positiveId(auditId, "auditId"))
        .orElseThrow(() -> ApiException.notFound("审计日志"));
    return new AuditLogDetail(
        po.getId(), po.getAccount(), po.getAction(), po.getCategory(), po.getResult(), po.getReason(),
        po.getObjectType(), po.getObjectId(), po.getBatchId(), po.getDetail(), differ.parse(po.getChanges()),
        po.getSnapshot(), po.getExtra(), po.getIp(), po.getUa(), po.getDevice(), po.getMfa(), po.getTraceId(),
        po.getPrevHash(), po.getHash(), po.getCreatedAt());
  }

  /**
   * 哈希链校验（ADR-004 决策 4）：按 id 升序重算 {@code sha256(prev_hash + 规范序列化)} 并比对，
   * 同时校验相邻行的 prev_hash 衔接。
   *
   * <p>两个刻意口径：① **扫描区间内的首行是锚点**——它的 prev_hash 不校验（可能指向保留策略清理掉的行，
   * 或 V43 之前的无哈希前缀）；② 无哈希的行（V43 之前写入）一律跳过只计数（unhashedPrefix）。
   * 校验按 id 分段扫描，maxRows 截断时 scanLimitReached=true，续扫用 checkedTo+1 当 fromId。
   */
  public AuditVerifyResult verify(String fromId, String toId, String maxRows) {
    int limit = maxRows == null || maxRows.isBlank() ? DEFAULT_VERIFY_ROWS : boundedInt("maxRows", maxRows);
    Long from = fromId == null || fromId.isBlank() ? null : positiveId(fromId, "fromId");
    Long to = toId == null || toId.isBlank() ? null : positiveId(toId, "toId");
    List<AuditLogPO> scanned = repository.hashedRange(from, to, limit + 1);
    boolean truncated = scanned.size() > limit;
    List<AuditLogPO> rows = truncated ? scanned.subList(0, limit) : scanned;
    long unhashedPrefix = repository.countUnhashed();
    if (rows.isEmpty()) {
      return new AuditVerifyResult(true, 0, unhashedPrefix, null, null, null, truncated);
    }
    Long anchor = rows.getFirst().getId();
    String previousHash = null;
    for (int index = 0; index < rows.size(); index++) {
      AuditLogPO row = rows.get(index);
      boolean hashMatches = row.getHash().equals(AuditHasher.hash(row.getPrevHash(), row));
      boolean linkMatches = index == 0 || row.getPrevHash().equals(previousHash);
      if (!hashMatches || !linkMatches) {
        return new AuditVerifyResult(false, index + 1L, unhashedPrefix, row.getId(), anchor, row.getId(), truncated);
      }
      previousHash = row.getHash();
    }
    return new AuditVerifyResult(true, rows.size(), unhashedPrefix, null, anchor, rows.getLast().getId(), truncated);
  }

  /** q 关键词：操作人或动作名包含匹配（与 /accounts 的 q 同口径）。 */
  private QueryCondition keywordCondition(String q) {
    if (q == null || q.isBlank()) {
      return null;
    }
    String like = LikePatterns.contains(q);
    return ACCOUNT.likeRaw(like).or(ACTION.likeRaw(like));
  }

  /** 正整数参数：非正整数 → 40001（与 FilterPredicate 的整数口径同一句文案）。 */
  private static long positiveId(String value, String name) {
    try {
      long id = Long.parseLong(value.trim());
      if (id < 1) {
        throw new NumberFormatException(value);
      }
      return id;
    } catch (NumberFormatException notPositive) {
      throw ApiException.keyed(ErrorCode.BAD_REQUEST, "filters.param.integer", name);
    }
  }

  private static int boundedInt(String name, String value) {
    int parsed;
    try {
      parsed = Integer.parseInt(value.trim());
    } catch (NumberFormatException notInteger) {
      throw ApiException.keyed(ErrorCode.BAD_REQUEST, "filters.param.integer", name);
    }
    if (parsed < 1 || parsed > MAX_VERIFY_ROWS) {
      throw ApiException.keyed(ErrorCode.BAD_REQUEST, "filters.param.max", name, MAX_VERIFY_ROWS);
    }
    return parsed;
  }
}
