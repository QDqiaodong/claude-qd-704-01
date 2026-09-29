package com.fire.protect.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 模块三（巡检记录）入参。 */
public final class InspectionDtos {

    private InspectionDtos() {
    }

    @Data
    public static class CreateReq {

        @NotNull(message = "所属建筑不能为空")
        private Long buildingId;

        /** 可空：限定某一层，留空则覆盖整栋楼 */
        private Long floorId;

        @NotBlank(message = "巡检人不能为空")
        @Size(max = 32, message = "巡检人不超过 32 个字符")
        private String inspector;

        /** 可空：默认今天 */
        private LocalDate inspectDate;

        @NotBlank(message = "巡检类型不能为空")
        private String inspectType;

        @Size(max = 128, message = "备注不超过 128 个字符")
        private String remark;
    }

    @Data
    public static class ItemResultReq {

        @NotBlank(message = "巡检结果不能为空")
        private String result;

        @Size(max = 255, message = "异常描述不超过 255 个字符")
        private String problemDesc;
    }
}
