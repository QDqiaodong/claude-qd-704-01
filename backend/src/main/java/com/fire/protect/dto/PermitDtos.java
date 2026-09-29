package com.fire.protect.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 停用许可与复位闭环入参。 */
public final class PermitDtos {

    private PermitDtos() {
    }

    @Data
    public static class MeasureReq {

        /** SPARE_EXT / EXTRA_PATROL / TEMP_BARRIER / OTHER */
        @NotBlank(message = "补偿措施类别不能为空")
        private String measureType;

        @NotBlank(message = "补偿措施内容不能为空")
        @Size(max = 255, message = "措施内容不超过 255 个字符")
        private String content;

        @Size(max = 128, message = "规格/频次不超过 128 个字符")
        private String specDesc;
    }

    /** 创建草稿 / 编辑草稿共用 */
    @Data
    public static class SaveReq {

        private Long buildingId;

        @Size(max = 255, message = "停用原因不超过 255 个字符")
        private String reason;

        private LocalDate planStart;

        private LocalDate planEnd;

        /** 现场负责人用户 id（必须是现场负责人角色） */
        private Long siteOwnerId;

        /** 受影响点位 id 清单（范围锁定后后端会拒绝变更） */
        private List<Long> pointIds;

        private List<MeasureReq> measures;
    }

    @Data
    public static class RejectReq {

        @NotBlank(message = "拒绝时必须填写意见")
        @Size(max = 255, message = "拒绝意见不超过 255 个字符")
        private String reason;
    }

    @Data
    public static class RevokeReq {

        @NotBlank(message = "撤销时必须填写原因")
        @Size(max = 255, message = "撤销原因不超过 255 个字符")
        private String reason;
    }

    @Data
    public static class MeasureConfirmReq {

        @NotNull(message = "缺少补偿措施 id")
        private Long measureId;

        @Size(max = 255, message = "确认说明不超过 255 个字符")
        private String remark;
    }

    @Data
    public static class OrderWaiverReq {

        @NotNull(message = "缺少维保单 id")
        private Long orderId;

        @NotBlank(message = "必须填写未完成维保单的处理说明")
        @Size(max = 255, message = "处理说明不超过 255 个字符")
        private String handleNote;
    }

    @Data
    public static class ResetItemReq {

        @NotNull(message = "缺少点位 id")
        private Long pointId;

        /** PASS / FAIL */
        @NotBlank(message = "核验结果不能为空")
        private String result;

        @Size(max = 255)
        private String verifyDesc;

        @Size(max = 255, message = "失败原因不超过 255 个字符")
        private String failReason;
    }

    /** 发起 / 重新发起复位核验：必须逐个点位给结论 */
    @Data
    public static class ResetVerifyReq {

        @NotNull(message = "必须带上许可版本号")
        private Long version;

        @Size(max = 255)
        private String remark;

        private List<ResetItemReq> items;
    }

    /** 关闭许可 */
    @Data
    public static class CloseReq {

        @NotNull(message = "必须带上许可版本号")
        private Long version;

        @Size(max = 255)
        private String remark;
    }
}
