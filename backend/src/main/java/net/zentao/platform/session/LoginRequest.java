package net.zentao.platform.session;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 登录请求体（contract/openapi.yaml LoginRequest）。 */
public record LoginRequest(
    @NotBlank @Size(max = 64) String account, @NotBlank @Size(max = 64) String password) {}
