package dev.tylerpac.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RedeemFullAccessCodeRequest {

    @NotBlank(message = "access_code_required")
    @Size(max = 64, message = "invalid_access_code_format")
    private String code;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}