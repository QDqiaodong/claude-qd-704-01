package com.fire.protect.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 消防演练模块入参。
 *
 * <p>所有会推进状态的动作都带 {@code clientActionId}（客户端生成的动作标识）：
 * 同一标识重复送达时服务端直接返回当前详情，不产生第二条事件、不重复推进。
 */
public final class DrillDtos {

    private DrillDtos() {
    }

    // ==================== 编排 ====================

    @Data
    public static class PositionReq {

        /** 编辑已存在岗位时带 id；新增为空 */
        private Long id;

        @NotBlank(message = "岗位代号不能为空")
        @Size(max = 32, message = "岗位代号不超过 32 个字符")
        private String posCode;

        @NotBlank(message = "岗位名称不能为空")
        @Size(max = 64, message = "岗位名称不超过 64 个字符")
        private String posName;

        @Size(max = 32, message = "岗位人员不超过 32 个字符")
        private String memberName;

        /** 绑定系统用户（SITE_OWNER），可空 */
        private Long userId;

        @Size(max = 32, message = "联系方式不超过 32 个字符")
        private String contact;
    }

    @Data
    public static class CardReq {

        /** 编辑已存在卡时带 id；新增为空 */
        private Long id;

        @NotNull(message = "请填写指令顺序号")
        @Positive(message = "顺序号必须为正整数")
        private Integer seqNo;

        @NotBlank(message = "指令标题不能为空")
        @Size(max = 128, message = "指令标题不超过 128 个字符")
        private String title;

        /** 目标岗位：可传岗位 id，或草稿期用岗位代号 */
        private Long targetPositionId;

        private String targetPosCode;

        @NotNull(message = "请填写响应时限（秒）")
        @Positive(message = "响应时限必须为正整数")
        private Integer timeLimitSec;

        @Size(max = 255, message = "现场提示不超过 255 个字符")
        private String onSiteHint;

        /** 前置卡：可传卡 id 列表，或草稿期用顺序号列表 */
        private List<Long> prereqCardIds;

        private List<Integer> prereqSeqs;
    }

    /** 创建场次（可同时带一份初始编排，也可只建表头） */
    @Data
    public static class CreateDrillReq {

        @NotBlank(message = "演练名称不能为空")
        @Size(max = 128, message = "演练名称不超过 128 个字符")
        private String title;

        private Long buildingId;

        @Size(max = 255, message = "场景说明不超过 255 个字符")
        private String scenario;

        /** 直接套用常用脚本 */
        private Long scriptId;

        private List<PositionReq> positions;

        private List<CardReq> cards;
    }

    /** 草稿整体保存（岗位 + 卡片 + 前置关系整体替换；顺序号即前端编排结果） */
    @Data
    public static class SaveDraftReq {

        private String clientActionId;

        private List<PositionReq> positions;

        private List<CardReq> cards;
    }

    // ==================== 状态动作 ====================

    /** 就绪 / 开始 / 暂停 / 恢复 */
    @Data
    public static class DrillActionReq {

        /** 客户端动作标识（幂等键） */
        private String clientActionId;
    }

    @Data
    public static class IssueCardReq {

        private String clientActionId;
    }

    @Data
    public static class ConfirmCardReq {

        private String clientActionId;

        @NotBlank(message = "确认时必须填写实际动作")
        @Size(max = 255, message = "实际动作不超过 255 个字符")
        private String actualAction;

        @Size(max = 255, message = "备注不超过 255 个字符")
        private String remark;
    }

    @Data
    public static class SkipCardReq {

        private String clientActionId;

        @NotBlank(message = "跳过节点必须填写原因")
        @Size(max = 255, message = "跳过原因不超过 255 个字符")
        private String reason;

        /** 是否把因该节点被跳过而阻塞的下游待下达卡一并级联跳过 */
        private boolean cascadeBlocked;
    }

    /** 为超时 / 跳过记录补处置结论（不推进状态） */
    @Data
    public static class DispositionReq {

        private String clientActionId;

        @NotBlank(message = "处置结论不能为空")
        @Size(max = 255, message = "处置结论不超过 255 个字符")
        private String note;
    }

    @Data
    public static class CancelDrillReq {

        private String clientActionId;

        @NotBlank(message = "取消演练必须填写原因")
        @Size(max = 255, message = "取消原因不超过 255 个字符")
        private String reason;
    }

    @Data
    public static class FinishDrillReq {

        private String clientActionId;

        @Size(max = 255, message = "结束说明不超过 255 个字符")
        private String remark;
    }

    // ==================== 常用脚本 ====================

    @Data
    public static class ScriptCardReq {

        @NotNull(message = "请填写脚本卡顺序号")
        @Positive(message = "顺序号必须为正整数")
        private Integer seqNo;

        @NotBlank(message = "指令标题不能为空")
        @Size(max = 128, message = "指令标题不超过 128 个字符")
        private String title;

        @NotBlank(message = "请选择目标岗位代号")
        @Size(max = 32, message = "岗位代号不超过 32 个字符")
        private String targetPosCode;

        @NotNull(message = "请填写响应时限（秒）")
        @Positive(message = "响应时限必须为正整数")
        private Integer timeLimitSec;

        @Size(max = 255, message = "现场提示不超过 255 个字符")
        private String onSiteHint;

        private List<Integer> prereqSeqs;
    }

    @Data
    public static class SaveScriptReq {

        @NotBlank(message = "脚本名称不能为空")
        @Size(max = 128, message = "脚本名称不超过 128 个字符")
        private String name;

        @Size(max = 255, message = "适用场景不超过 255 个字符")
        private String scenario;

        private List<ScriptCardReq> cards;
    }

    /** 把脚本应用到草稿场次（岗位按代号补建，卡片整体复制，前置按顺序号映射） */
    @Data
    public static class ApplyScriptReq {

        @NotNull(message = "请选择要套用的脚本")
        private Long scriptId;

        private String clientActionId;
    }
}
