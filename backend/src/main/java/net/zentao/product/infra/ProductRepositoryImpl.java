package net.zentao.product.infra;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.zentao.platform.persistence.SoftDeletes;
import net.zentao.product.domain.Product;
import net.zentao.product.domain.ProductRepository;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** 产品仓储实现（infra：PO ↔ 领域对象；whitelist/custom_fields 为 JSON 文本列）。 */
@Component
public class ProductRepositoryImpl implements ProductRepository {

  private static final QueryColumn DELETED_AT = new QueryColumn("deleted_at");
  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

  private final ProductMapper mapper;
  private final JsonMapper jsonMapper;

  public ProductRepositoryImpl(ProductMapper mapper, JsonMapper jsonMapper) {
    this.mapper = mapper;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Optional<Product> findActiveById(long id) {
    return Optional.ofNullable(mapper.selectOneByCondition(new QueryColumn("id").eq(id).and(DELETED_AT.isNull())))
        .map(this::toDomain);
  }

  @Override
  public List<Product> findAllActive() {
    return mapper.selectListByCondition(DELETED_AT.isNull()).stream().map(this::toDomain).toList();
  }

  @Override
  public List<Product> findActiveByIds(List<Long> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return mapper.selectListByCondition(new QueryColumn("id").in(ids).and(DELETED_AT.isNull())).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public Product insert(Product product) {
    ProductPO po = toPo(product);
    po.setId(null);
    po.setLockVersion(0);
    mapper.insert(po);
    return toDomain(mapper.selectOneById(po.getId()));
  }

  @Override
  public Optional<Product> update(Product product) {
    // 全量覆盖（含 null 字段）：聚合持有完整状态，清空字段（如 activate 清 closed_at）必须落库
    ProductPO po = toPo(product);
    return mapper.update(po, false) > 0 ? Optional.of(product) : Optional.empty();
  }

  @Override
  public void softDelete(long id) {
    SoftDeletes.apply("product", Row.of("deleted_at", Instant.now()), new QueryColumn("id").eq(id));
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<Product> queryPage(Object whereWrapper, int offset, int limit) {
    return mapper.selectListByQuery(((QueryWrapper) whereWrapper).limit(offset, limit)).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public long countByQuery(Object whereWrapper) {
    return mapper.selectCountByQuery((QueryWrapper) whereWrapper);
  }

  private Product toDomain(ProductPO po) {
    return new Product(po.getId(), po.getProgramId() == null ? 0 : po.getProgramId(), po.getName(), po.getCode(),
        po.getType(), po.getStatus(), po.getDescription(), po.getPo(), po.getQd(), po.getRd(), po.getAcl(),
        readStringList(po.getWhitelist()), po.getSort() == null ? 0 : po.getSort(), readMap(po.getCustomFields()),
        po.getCreatedBy(), po.getCreatedAt(), po.getUpdatedBy(), po.getUpdatedAt(), po.getClosedAt(),
        po.getLockVersion() == null ? 0 : po.getLockVersion());
  }

  private ProductPO toPo(Product product) {
    ProductPO po = new ProductPO();
    po.setId(product.id() == 0 ? null : product.id());
    po.setProgramId(product.programId());
    po.setName(product.name());
    po.setCode(product.code());
    po.setType(product.type());
    po.setStatus(product.status());
    po.setDescription(product.description());
    po.setPo(product.po());
    po.setQd(product.qd());
    po.setRd(product.rd());
    po.setAcl(product.acl());
    po.setWhitelist(write(product.whitelist()));
    po.setSort(product.sort());
    po.setCustomFields(write(product.customFields()));
    po.setCreatedBy(product.createdBy());
    po.setCreatedAt(product.createdAt());
    po.setUpdatedBy(product.updatedBy());
    po.setUpdatedAt(product.updatedAt());
    po.setClosedAt(product.closedAt());
    po.setLockVersion(product.lockVersion());
    return po;
  }

  private List<String> readStringList(String json) {
    return json == null || json.isBlank() ? List.of() : jsonMapper.readValue(json, STRING_LIST);
  }

  private Map<String, Object> readMap(String json) {
    return json == null || json.isBlank() ? Map.of() : jsonMapper.readValue(json, MAP);
  }

  private String write(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof List<?> list && list.isEmpty()) {
      return null;
    }
    if (value instanceof Map<?, ?> map && map.isEmpty()) {
      return null;
    }
    return jsonMapper.writeValueAsString(value);
  }
}
