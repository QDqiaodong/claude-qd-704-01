package com.fire.protect.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 消防演练现场指令与响应打点入参。 */
public final class DrillDtos {

    private DrillDtos() {
    }

    // ==================== 草稿编排 ====================

    @Data
    public static class PostReq {

        /** 已存在岗位带回 id；新增岗位留空 */
        private Long id;

        @NotBlank(message = "岗位编码不能为空")
        @Size(max = 32, message = "岗位编码不超过 32 个字符")
        private String postCode;

        @NotBlank(message = "岗位名称不能为空")
        @Size(max = 64, message = "岗位名称不超过 64 个字符")
        private String postName;

        @Size(max = 32, message = "岗位人员不超过 32 个字符")
        private String assignee;

        @Size(max = 32, message = "联系方式不超过 32 个字符")
        private String contact;

        private Integer sortNo;
    }

    @Data
    public static class CardReq {

        /**
         * 本次保存请求内的临时标识（前端生成，如 c0/c1）。前置节点 prereqClientIds
         * 通过它引用本单里的其它卡；保存是整单全量重建，与已存在的卡 id 无关。
         */
        @NotBlank(message = "指令卡缺少临时标识 clientId")
        private String clientId;

        private Integer seqNo;

        @NotBlank(message = "指令标题不能为空")
        @Size(max = 128, message = "指令标题不超过 128 个字符")
        private String title;

        @NotBlank(message = "指令内容不能为空")
        @Size(max = 500, message = "指令内容不超过 500 个字符")
        private String content;

        @Size(max = 500, message = "现场提示不超过 500 个字符")
        private String sceneHint;

        /** 目标岗位编码（同场岗位按编码匹配） */
        @NotBlank(message = "每张指令卡都要选择目标岗位")
        private String targetPostCode;

        /** 响应时限（秒），1~3600 */
        private Integer timeLimitSeconds;

        /** 前置节点：本单内其它指令卡的临时 clientId 列表，由服务端统一映射为持久化依赖 */
        private List<String> prereqClientIds;
    }

    /** 创建 / 保存草稿（整单全量覆盖；只有草稿允许保存） */
    @Data
    public static class SaveDrillReq {

        @NotBlank(message = "演练名称不能为空")
        @Size(max = 128, message = "演练名称不超过 128 个字符")
        private String title;

        private Long buildingId;

        @Size(max = 500, message = "场景说明不超过 500 个字符")
        private String scenario;

        private List<PostReq> posts;

        private List<CardReq> cards;
    }

    // ==================== 动作（都带客户端动作标识，用于幂等） ====================

    @Data
    public static class KeyedReq {

        /** 客户端动作标识（同一动作重复发送必须带同一个值，UUID 即可） */
        @NotBlank(message = "缺少动作标识 actionKey")
        private String actionKey;
    }

    @Data
    public static class CancelReq {

        @NotBlank(message = "缺少动作标识 actionKey")
        private String actionKey;

        @NotBlank(message = "取消演练必须填写原因")
        @Size(max = 255, message = "取消原因不超过 255 个字符")
        private String reason;
    }

    @Data
    public static class ConfirmReq {

        @NotBlank(message = "缺少动作标识 actionKey")
        private String actionKey;

        @NotBlank(message = "岗位确认必须填写实际动作")
        @Size(max = 500, message = "实际动作不超过 500 个字符")
        private String actualAction;

        @Size(max = 500, message = "备注不超过 500 个字符")
        private String remark;
    }

    @Data
    public static class SkipReq {

        @NotBlank(message = "缺少动作标识 actionKey")
        private String actionKey;

        @NotBlank(message = "跳过节点必须填写原因")
        @Size(max = 500, message = "跳过原因不超过 500 个字符")
        private String reason;
    }

    @Data
    public static class DisposeReq {

        @NotBlank(message = "缺少动作标识 actionKey")
        private String actionKey;

        @NotBlank(message = "处置结论不能为空")
        @Size(max = 500, message = "处置结论不超过 500 个字符")
        private String disposition;
    }

    // ==================== 常用脚本 ====================

    @Data
    public static class TemplateCardReq {

        private Integer seqNo;

        @NotBlank(message = "指令标题不能为空")
        private String title;

        @NotBlank(message = "指令内容不能为空")
        private String content;

        private String sceneHint;

        private String targetPostCode;

        private Integer timeLimitSeconds;

        /** 前置节点顺序号列表 */
        private List<Integer> prereqSeqs;
    }

    @Data
    public static class SaveTemplateReq {

        private Long id;

        @NotBlank(message = "脚本名称不能为空")
        @Size(max = 128, message = "脚本名称不超过 128 个字符")
        private String name;

        @Size(max = 500, message = "脚本说明不超过 500 个字符")
        private String description;

        private List<PostReq> posts;

        private List<TemplateCardReq> cards;
    }
}
