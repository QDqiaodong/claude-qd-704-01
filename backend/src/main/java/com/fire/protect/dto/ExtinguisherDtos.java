package com.fire.protect.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 模块五（灭火器与气瓶台账）入参。 */
public final class ExtinguisherDtos {

    private ExtinguisherDtos() {
    }

    @Data
    public static class CreateReq {

        @NotBlank(message = "钢印编号不能为空")
        @Size(max = 32, message = "钢印编号不超过 32 个字符")
        private String steelNo;

        @NotBlank(message = "灭火器类型不能为空")
        private String cylType;

        @NotNull(message = "充装量不能为空")
        private BigDecimal chargeKg;

        @NotNull(message = "所属建筑不能为空")
        private Long buildingId;

        private Long floorId;

        /** 存放点位（facility_point 里类型为灭火器的点位） */
        private Long pointId;

        @Size(max = 128, message = "存放位置不超过 128 个字符")
        private String storePlace;

        private LocalDate produceDate;

        private LocalDate lastFillDate;

        private LocalDate hydroTestDue;

        private LocalDate scrapDate;

        private Integer planX;

        private Integer planY;
    }
}
