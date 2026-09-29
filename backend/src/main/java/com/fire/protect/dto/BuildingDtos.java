package com.fire.protect.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 模块一（建筑与楼层）入参。 */
public final class BuildingDtos {

    private BuildingDtos() {
    }

    @Data
    public static class CreateReq {

        @NotBlank(message = "建筑编号不能为空")
        @Size(max = 16, message = "建筑编号不超过 16 个字符")
        @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "建筑编号只能由字母、数字与短横线组成（用于生成点位编码）")
        private String code;

        @NotBlank(message = "建筑名称不能为空")
        @Size(max = 64, message = "建筑名称不超过 64 个字符")
        private String name;

        @Size(max = 128, message = "地址不超过 128 个字符")
        private String address;

        @NotBlank(message = "建筑类型不能为空")
        private String buildingType;

        @NotNull(message = "地上层数不能为空")
        @PositiveOrZero(message = "地上层数不能为负")
        private Integer floorsAbove;

        @NotNull(message = "地下层数不能为空")
        @PositiveOrZero(message = "地下层数不能为负")
        private Integer floorsBelow;

        private BigDecimal areaSqm;

        @Size(max = 32, message = "消防负责人不超过 32 个字符")
        private String fireManager;

        @Size(max = 20, message = "联系电话不超过 20 个字符")
        private String contactPhone;
    }
}
