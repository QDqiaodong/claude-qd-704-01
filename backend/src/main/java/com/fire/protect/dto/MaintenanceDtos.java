package com.fire.protect.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 模块四（维保单）入参。 */
public final class MaintenanceDtos {

    private MaintenanceDtos() {
    }

    @Data
    public static class CreateReq {

        @NotBlank(message = "维保类型不能为空")
        private String orderType;

        /** 关联点位 / 气瓶二选一 */
        private Long pointId;

        private Long extId;

        @Size(max = 255, message = "故障描述不超过 255 个字符")
        private String faultDesc;

        @Size(max = 64, message = "服务商不超过 64 个字符")
        private String vendor;

        @Size(max = 32, message = "责任人不超过 32 个字符")
        private String ownerPerson;

        private BigDecimal materialFee;

        private BigDecimal laborFee;
    }

    @Data
    public static class StatusReq {

        /** 目标状态：WORKING / DONE / ACCEPTED */
        @Size(max = 16, message = "目标状态不合法")
        private String target;

        @Size(max = 255, message = "处理措施不超过 255 个字符")
        private String solution;

        private BigDecimal materialFee;

        private BigDecimal laborFee;
    }

    @Data
    public static class AssignReq {

        @NotBlank(message = "服务商不能为空")
        @Size(max = 64, message = "服务商不超过 64 个字符")
        private String vendor;

        @NotBlank(message = "责任人不能为空")
        @Size(max = 32, message = "责任人不超过 32 个字符")
        private String ownerPerson;
    }

}
