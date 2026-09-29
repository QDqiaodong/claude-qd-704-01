package com.fire.protect.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 消防演练模块统一出口的数据形状（record，Jackson 直接序列化）。 */
public final class DrillViews {

    private DrillViews() {
    }

    /** 场次列表行 */
    public record DrillListItem(
            Long id,
            String drillNo,
            String title,
            Long buildingId,
            String buildingName,
            String scenario,
            String organizer,
            String status,
            String statusName,
            long postCount,
            long cardCount,
            long waitingCount,
            long confirmedCount,
            long timeoutCount,
            long skippedCount,
            long undisposedCount,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            LocalDateTime createdAt
    ) {
    }

    /** 阻塞来源：前置节点未确认的具体原因 */
    public record BlockerView(
            Long prereqId,
            Integer seqNo,
            String title,
            String targetPostName,
            String status,
            String statusName,
            String reason
    ) {
    }

    /** 岗位响应尝试（含迟到尝试） */
    public record AttemptView(
            Long id,
            Long cardId,
            Integer cardSeqNo,
            String cardTitle,
            Long postId,
            String result,
            String resultName,
            String actualAction,
            String remark,
            String operator,
            LocalDateTime createdAt,
            boolean late
    ) {
    }

    /** 指令卡（执行页主体） */
    public record CardView(
            Long id,
            int seqNo,
            String title,
            String content,
            String sceneHint,
            Long targetPostId,
            String targetPostCode,
            String targetPostName,
            int timeLimitSeconds,
            String status,
            String statusName,
            boolean terminal,
            LocalDateTime issuedAt,
            String issuedBy,
            LocalDateTime deadline,
            Integer remainingSeconds,
            Integer remainingFrozenSeconds,
            LocalDateTime confirmedAt,
            String confirmedBy,
            String actualAction,
            String confirmRemark,
            LocalDateTime timedOutAt,
            LocalDateTime skippedAt,
            String skippedBy,
            String skipReason,
            String disposition,
            LocalDateTime dispositionAt,
            String dispositionBy,
            boolean disposed,
            List<Integer> prereqSeqs,
            List<BlockerView> blockers,
            boolean prerequisitesMet,
            /** 当前能否下达：场次进行中 + 待下达 + 前置全部确认 */
            boolean issuable,
            /** 下一可执行节点标记（待下达且前置满足中顺序最靠前） */
            boolean nextExecutable,
            List<AttemptView> attempts
    ) {
    }

    /** 岗位维度的响应记录 */
    public record PostView(
            Long id,
            String postCode,
            String postName,
            String assignee,
            String contact,
            int sortNo,
            long totalCards,
            long waitingCount,
            long confirmedCount,
            long timeoutCount,
            long skippedCount,
            long pendingCount,
            List<CardView> cards,
            List<AttemptView> attempts
    ) {
    }

    /** 时间线事件 */
    public record EventView(
            Long id,
            String action,
            String actionName,
            String targetType,
            Long targetId,
            Integer cardSeqNo,
            String cardTitle,
            String fromStatus,
            String fromStatusName,
            String toStatus,
            String toStatusName,
            String operator,
            String note,
            LocalDateTime createdAt
    ) {
    }

    /** 场次详情：卡列表 + 岗位记录 + 时间线 + 结束缺项，一次拉全 */
    public record DrillDetailView(
            Long id,
            String drillNo,
            String title,
            Long buildingId,
            String buildingName,
            String scenario,
            String organizer,
            String status,
            String statusName,
            boolean editable,
            boolean readyMarked,
            LocalDateTime readyAt,
            LocalDateTime startedAt,
            LocalDateTime pausedAt,
            LocalDateTime finishedAt,
            String finishedBy,
            LocalDateTime canceledAt,
            String canceledBy,
            String cancelReason,
            long version,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            /** 服务端当前时间（截到秒），前端据此校时后显示剩余秒数 */
            LocalDateTime serverTime,
            List<CardView> cards,
            List<Integer> currentSeqs,
            List<Integer> nextExecutableSeqs,
            List<PostView> posts,
            List<EventView> events,
            /** 结束演练的具体缺项（空表示可以结束） */
            List<String> finishBlockers
    ) {
    }

    public record TemplatePostView(
            String postCode,
            String postName,
            int sortNo
    ) {
    }

    public record TemplateCardView(
            int seqNo,
            String title,
            String content,
            String sceneHint,
            String targetPostCode,
            int timeLimitSeconds,
            List<Integer> prereqSeqs
    ) {
    }

    /** 脚本列表行 */
    public record TemplateListItem(
            Long id,
            String name,
            String description,
            String createdBy,
            long postCount,
            long cardCount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    /** 脚本详情（导入 / 编辑用） */
    public record TemplateDetailView(
            Long id,
            String name,
            String description,
            String createdBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<TemplatePostView> posts,
            List<TemplateCardView> cards
    ) {
    }
}
