package com.fire.protect.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 现场指令卡。
 *
 * <p>状态机：PENDING 待下达 → WAITING 等待响应 → CONFIRMED 已确认；
 * WAITING 可由服务端计时判定 TIMEOUT 已超时；PENDING / WAITING 可被 SKIPPED 已跳过。
 * CONFIRMED / TIMEOUT / SKIPPED 均为终态，终态不再改写（迟到确认只追加尝试与事件）。
 *
 * <p>计时：{@link #deadline} = 下达时刻 + 响应时限；暂停时把剩余秒数冻结到
 * {@link #remainingFrozenSeconds} 并清空 deadline（冻结期间永不超时），恢复时
 * deadline = 当前服务端时间 + 冻结剩余。因此刷新页面 / 服务重启后剩余秒数仍一致。
 */
@Data
@Entity
@Table(name = "drill_card")
public class DrillCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "seq_no", nullable = false)
    private int seqNo;

    @Column(name = "title", nullable = false, length = 128)
    private String title;

    @Column(name = "content", nullable = false, length = 500)
    private String content;

    /** 现场提示（执行页随指令一起展示给岗位） */
    @Column(name = "scene_hint", length = 500)
    private String sceneHint;

    /** 目标岗位行 id（草稿期引用；开始后仍保留用于聚合，展示以快照为准） */
    @Column(name = "target_post_id")
    private Long targetPostId;

    @Column(name = "target_post_code", length = 32)
    private String targetPostCode;

    @Column(name = "target_post_name", length = 64)
    private String targetPostName;

    /** 响应时限（秒） */
    @Column(name = "time_limit_seconds", nullable = false)
    private int timeLimitSeconds;

    /** PENDING / WAITING / CONFIRMED / TIMEOUT / SKIPPED */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "issued_at")
    private LocalDateTime issuedAt;

    @Column(name = "issued_by", length = 32)
    private String issuedBy;

    /** 响应截止时间（仅 WAITING 且场次 RUNNING 时非空） */
    @Column(name = "deadline")
    private LocalDateTime deadline;

    /** 暂停时冻结的剩余秒数（WAITING 且场次 PAUSED 时非空） */
    @Column(name = "remaining_frozen_seconds")
    private Integer remainingFrozenSeconds;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "confirmed_by", length = 32)
    private String confirmedBy;

    /** 实际动作（岗位确认必填） */
    @Column(name = "actual_action", length = 500)
    private String actualAction;

    @Column(name = "confirm_remark", length = 500)
    private String confirmRemark;

    @Column(name = "timed_out_at")
    private LocalDateTime timedOutAt;

    @Column(name = "skipped_at")
    private LocalDateTime skippedAt;

    @Column(name = "skipped_by", length = 32)
    private String skippedBy;

    /** 跳过原因（跳过必填） */
    @Column(name = "skip_reason", length = 500)
    private String skipReason;

    /** 处置结论（TIMEOUT / SKIPPED 结束前必填） */
    @Column(name = "disposition", length = 500)
    private String disposition;

    @Column(name = "disposition_at")
    private LocalDateTime dispositionAt;

    @Column(name = "disposition_by", length = 32)
    private String dispositionBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
