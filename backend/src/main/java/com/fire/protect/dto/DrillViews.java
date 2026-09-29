package com.fire.protect.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 消防演练模块出口数据形状（record，Jackson 直接序列化）。 */
public final class DrillViews {

    private DrillViews() {
    }

    /** 场次列表行 */
    public record DrillListView(
            Long id,
            String drillNo,
            String title,
            Long buildingId,
            String buildingName,
            String status,
            String statusName,
            String scenario,
            String organizerName,
            boolean chainFrozen,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            long cardTotal,
            long confirmedCount,
            long timeoutCount,
            long skippedCount,
            long waitingCount,
            long pendingCount,
            long version,
            LocalDateTime createdAt
    ) {
    }

    /** 岗位及其响应打点汇总（每个岗位独立一行） */
    public record PositionView(
            Long id,
            String posCode,
            String posName,
            String memberName,
            Long userId,
            String contact,
            int sortNo,
            long totalCards,
            long waitingCards,
            long pendingCards,
            long confirmedCards,
            long timeoutCards,
            long skippedCards,
            Long currentCardId,
            String currentCardTitle,
            boolean boundToCurrentUser
    ) {
    }

    /** 前置节点摘要 */
    public record PrereqView(
            Long cardId,
            Integer seqNo,
            String title,
            String status,
            String statusName,
            boolean satisfied,
            boolean blocking,
            boolean disposed,
            String dispositionNote
    ) {
    }

    /** 现场指令卡（含服务端计算的剩余秒数、可否下达、阻塞来源） */
    public record CardView(
            Long id,
            int seqNo,
            String title,
            Long targetPositionId,
            String targetPosCode,
            String targetPosName,
            String targetMemberName,
            int timeLimitSec,
            String onSiteHint,
            String status,
            String statusName,
            List<Long> prereqCardIds,
            List<PrereqView> prereqs,
            boolean canIssue,
            boolean prereqReady,
            boolean blocked,
            List<PrereqView> blockedBy,
            Long remainingSec,
            LocalDateTime issuedAt,
            LocalDateTime deadlineAt,
            LocalDateTime confirmedAt,
            String confirmedBy,
            String actualAction,
            String confirmRemark,
            boolean lateAttempt,
            LocalDateTime timedOutAt,
            LocalDateTime skippedAt,
            String skippedBy,
            String skipReason,
            String dispositionNote,
            LocalDateTime dispositionAt,
            String dispositionBy,
            boolean blockedByCascade
    ) {
    }

    /** 事件流（只追加） */
    public record EventView(
            Long id,
            String eventType,
            String eventTypeName,
            String targetType,
            Long targetId,
            Integer targetSeq,
            String targetTitle,
            String fromStatus,
            String fromStatusName,
            String toStatus,
            String toStatusName,
            String operator,
            String operatorRole,
            String note,
            LocalDateTime createdAt
    ) {
    }

    /** 岗位响应尝试 */
    public record ResponseView(
            Long id,
            Long cardId,
            Integer cardSeqNo,
            String cardTitle,
            Long positionId,
            String posCode,
            String posName,
            String attemptKind,
            String attemptKindName,
            String actualAction,
            String remark,
            String responder,
            LocalDateTime createdAt
    ) {
    }

    /** 完整时间线：事件 + 岗位响应尝试，按服务端时间统一排序 */
    public record TimelineItemView(
            String key,
            String kind,
            String kindName,
            LocalDateTime occurredAt,
            Integer cardSeqNo,
            String cardTitle,
            String eventType,
            String eventTypeName,
            String fromStatus,
            String fromStatusName,
            String toStatus,
            String toStatusName,
            String posCode,
            String posName,
            String attemptKind,
            String operator,
            String operatorRole,
            String note
    ) {
    }

    /** 结束演练前的缺项提示 */
    public record FinishBlockerView(
            Long cardId,
            int seqNo,
            String title,
            String status,
            String statusName,
            String reason
    ) {
    }

    /** 场次详情（执行页 / 编排页共用这一份快照） */
    public record DrillDetailView(
            Long id,
            String drillNo,
            String title,
            Long buildingId,
            String buildingName,
            String scenario,
            String organizerName,
            String status,
            String statusName,
            boolean chainFrozen,
            LocalDateTime readyAt,
            LocalDateTime startedAt,
            LocalDateTime pausedAt,
            long pausedTotalSec,
            LocalDateTime finishedAt,
            String finishedBy,
            LocalDateTime canceledAt,
            String canceledBy,
            String cancelReason,
            LocalDateTime serverTime,
            boolean timingRunning,
            long version,
            LocalDateTime createdAt,
            List<PositionView> positions,
            List<CardView> cards,
            List<EventView> events,
            List<ResponseView> responses,
            List<TimelineItemView> timeline,
            Long currentCardId,
            CardView currentCard,
            List<Integer> nextExecutableSeqs,
            long cardTotal,
            long confirmedCount,
            long timeoutCount,
            long skippedCount,
            long waitingCount,
            long pendingCount,
            boolean canFinish,
            List<FinishBlockerView> finishBlockers,
            boolean allowEdit,
            boolean canReady,
            boolean canStart,
            boolean canPause,
            boolean canResume,
            boolean canCancel
    ) {
    }

    /** 轻量时钟：服务端时间 + 当前场次状态（执行页轮询对齐倒计时） */
    public record ClockView(
            Long drillId,
            String status,
            String statusName,
            boolean timingRunning,
            LocalDateTime serverTime
    ) {
    }

    /** 脚本卡 */
    public record ScriptCardView(
            Long id,
            int seqNo,
            String title,
            String targetPosCode,
            int timeLimitSec,
            String onSiteHint,
            List<Integer> prereqSeqs
    ) {
    }

    /** 常用脚本 */
    public record ScriptView(
            Long id,
            String name,
            String scenario,
            String createdBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<ScriptCardView> cards,
            long cardCount
    ) {
    }
}
