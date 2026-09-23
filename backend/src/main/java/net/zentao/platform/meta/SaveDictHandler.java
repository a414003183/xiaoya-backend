package net.zentao.platform.meta;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import net.zentao.platform.error.ApiException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 字典类型/数据项的写入口（T16 P1-4）。
 *
 * <p>两条硬约束：code 形如 `^[a-z][a-z0-9-]*$`（字典名会被前端当查询键用），且**不能与代码注册的内置字典撞名**——
 * `DictRegistry` 的查找是「先注册表后 DB」，撞名的 DB 类型永远查不到，不如建的时候就拒绝。
 */
@Component
public class SaveDictHandler {

  private static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9-]*$");
  private static final List<String> STATUSES = List.of("active", "disabled");
  private static final int MAX_CODE = 60;
  private static final int MAX_NAME = 60;
  private static final int MAX_LABEL = 120;
  private static final int MAX_VALUE = 120;

  private final DictRepository repository;
  private final DictRegistry registry;

  public SaveDictHandler(DictRepository repository, DictRegistry registry) {
    this.repository = repository;
    this.registry = registry;
  }

  public record DictTypeRequest(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
      @Schema(allowableValues = {"active", "disabled"}) String status) {}

  public record DictTypeUpdateRequest(String name,
      @Schema(allowableValues = {"active", "disabled"}) String status) {}

  public record DictDataRequest(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String itemLabel,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String itemValue, Integer sortNo,
      @Schema(allowableValues = {"active", "disabled"}) String status) {}

  /** 更新体（字段全可选：只改传了的）。与新建体分开，否则契约的 required 与「可省」自相矛盾。 */
  public record DictDataUpdateRequest(String itemLabel, String itemValue, Integer sortNo,
      @Schema(allowableValues = {"active", "disabled"}) String status) {}

  @Transactional
  public DictQueryService.DictTypeView createType(DictTypeRequest command) {
    String code = validatedCode(command.code());
    String name = validatedName(command.name());
    String status = validatedStatus(command.status());
    if (registry.isRegistered(code) || repository.findType(code).isPresent()) {
      throw ApiException.validation(Map.of("code", "duplicate"));
    }
    DictTypePO po = new DictTypePO();
    po.setCode(code);
    po.setName(name);
    po.setStatus(status);
    repository.insertType(po);
    return new DictQueryService.DictTypeView(code, name, status);
  }

  @Transactional
  public DictQueryService.DictTypeView updateType(String code, DictTypeUpdateRequest command) {
    DictTypePO po = repository.findType(code).orElseThrow(() -> ApiException.notFound("entity.dictType"));
    if (command.name() != null) {
      po.setName(validatedName(command.name()));
    }
    if (command.status() != null) {
      po.setStatus(validatedStatus(command.status()));
    }
    repository.updateType(po);
    return new DictQueryService.DictTypeView(po.getCode(), po.getName(), po.getStatus());
  }

  /** 级联删数据项（页面确认框已写明）——类型没了，数据项就是查不到的孤儿。 */
  @Transactional
  public void deleteType(String code) {
    if (repository.deleteTypeCascade(code) == 0) {
      throw ApiException.notFound("entity.dictType");
    }
  }

  @Transactional
  public DictQueryService.DictDataView createData(String typeCode, DictDataRequest command) {
    repository.findType(typeCode).orElseThrow(() -> ApiException.notFound("entity.dictType"));
    String label = validatedItemLabel(command.itemLabel());
    String value = validatedItemValue(command.itemValue());
    Integer sortNo = command.sortNo() == null ? 0 : command.sortNo();
    String status = validatedStatus(command.status());
    requireUniqueValue(typeCode, value, null);
    DictDataPO po = new DictDataPO();
    po.setTypeCode(typeCode);
    po.setItemLabel(label);
    po.setItemValue(value);
    po.setSortNo(sortNo);
    po.setStatus(status);
    repository.insertData(po);
    return DictQueryService.DictDataView.of(po);
  }

  @Transactional
  public DictQueryService.DictDataView updateData(long id, DictDataUpdateRequest command) {
    DictDataPO po = repository.findData(id).orElseThrow(() -> ApiException.notFound("entity.dictItem"));
    if (command.itemLabel() != null) {
      po.setItemLabel(validatedItemLabel(command.itemLabel()));
    }
    if (command.itemValue() != null) {
      String value = validatedItemValue(command.itemValue());
      requireUniqueValue(po.getTypeCode(), value, id);
      po.setItemValue(value);
    }
    if (command.sortNo() != null) {
      po.setSortNo(command.sortNo());
    }
    if (command.status() != null) {
      po.setStatus(validatedStatus(command.status()));
    }
    repository.updateData(po);
    return DictQueryService.DictDataView.of(po);
  }

  @Transactional
  public void deleteData(long id) {
    if (repository.deleteData(id) == 0) {
      throw ApiException.notFound("entity.dictItem");
    }
  }

  /** 同类型下 item_value 唯一：两个数据项存同一个值，读取方按值反查标签就分不清了。 */
  private void requireUniqueValue(String typeCode, String value, Long selfId) {
    boolean taken = repository.listActiveData(typeCode).stream()
        .anyMatch(existing -> existing.getItemValue().equals(value) && !existing.getId().equals(selfId));
    if (taken) {
      throw ApiException.validation(Map.of("itemValue", "duplicate"));
    }
  }

  private static String validatedCode(String code) {
    if (code == null || !CODE.matcher(code.trim()).matches() || code.trim().length() > MAX_CODE) {
      throw ApiException.validation(Map.of("code", "pattern"));
    }
    return code.trim();
  }

  private static String validatedName(String name) {
    if (name == null || name.isBlank() || name.trim().length() > MAX_NAME) {
      throw ApiException.validation(Map.of("name", "required"));
    }
    return name.trim();
  }

  private static String validatedStatus(String status) {
    if (status == null) {
      return DictRepository.ACTIVE;
    }
    if (!STATUSES.contains(status)) {
      throw ApiException.validation(Map.of("status", "pattern"));
    }
    return status;
  }

  private static String validatedItemLabel(String label) {
    if (label == null || label.isBlank() || label.trim().length() > MAX_LABEL) {
      throw ApiException.validation(Map.of("itemLabel", "required"));
    }
    return label.trim();
  }

  private static String validatedItemValue(String value) {
    if (value == null || value.isBlank() || value.trim().length() > MAX_VALUE) {
      throw ApiException.validation(Map.of("itemValue", "required"));
    }
    return value.trim();
  }
}
