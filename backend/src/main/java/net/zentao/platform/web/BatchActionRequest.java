package net.zentao.platform.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/** 批量动作请求体（contract：BatchActionRequest；03 §1 `{ids, action, params}`）。 */
public record BatchActionRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Long> ids,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String action,
    Map<String, Object> params) {}
