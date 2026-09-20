package net.zentao.product.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** link/unlink 统一请求体（contract：LinkRequest；plan/release/build 三族同构）。 */
public record LinkRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"story", "bug"}) String objectType,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Long> ids) {}
