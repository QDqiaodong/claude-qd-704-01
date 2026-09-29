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
 * <p>状态机：PENDING 待下达 → WAITING 等待响应 → CONFIRMED 已确认 / TIMEOUT 已超时；
 * SKIPPED 已跳过是 PENDING / WAITING 都可进入的终态。CONFIRMED / TIMEOUT / SKIPPED 均为终态。
 *
 * <p>时间口径（剩余秒数一律由服务端计算）：
 * <ul>
 *   <li>下达：{@code deadlineAt = issuedAt + timeLimitSec}；</li>
 *   <li>暂停时记录在场次上的 {@code pausedAt}，卡本身不写，恢复后按暂停时长顺延
 *       {@code deadlineAt}（即从冻结的剩余值继续）；</li>
 *   <li>卡终态后 {@code deadlineAt} 保留，时间线可复核。</li>
 * </ul>
 *
 * <p>{@code dispositionNote}：超时 / 跳过记录的处置结论，结束演练前必须逐条补齐。
 */
@Data
@Entity
@Table(name = "drill_card")
public class DrillCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "drill_id", nullable = false)
    private Long drillId;

    /** 顺序号（链上位置），场次内唯一 */
    @Column(name = "seq_no", nullable = false)
    private int seqNo;

    @Column(name = "title", nullable = false, length = 128)
    private String title;

    @Column(name = "target_position_id", nullable = false)
    private Long targetPositionId;

    /** 响应时限（秒） */
    @Column(name = "time_limit_sec", nullable = false)
    private int timeLimitSec;

    @Column(name = "on_site_hint", length = 255)
    private String onSiteHint;

    /** PENDING / WAITING / CONFIRMED / TIMEOUT / SKIPPED */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "issued_at")
    private LocalDateTime issuedAt;

    @Column(name = "issued_by", length = 32)
    private String issuedBy;

    /** 响应截止时刻（暂停恢复后顺延） */
    @Column(name = "deadline_at")
    private LocalDateTime deadlineAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "confirmed_by", length = 32)
    private String confirmedBy;

    /** 实际动作（确认必填） */
    @Column(name = "actual_action", length = 255)
    private String actualAction;

    /** 确认备注（选填） */
    @Column(name = "confirm_remark", length = 255)
    private String confirmRemark;

    /** 终态后是否收到过迟到确认尝试（不改终态，但留在响应记录与时间线） */
    @Column(name = "late_attempt", nullable = false)
    private boolean lateAttempt;

    @Column(name = "timed_out_at")
    private LocalDateTime timedOutAt;

    @Column(name = "skipped_at")
    private LocalDateTime skippedAt;

    @Column(name = "skipped_by", length = 32)
    private String skippedBy;

    /** 跳过原因（跳过必填） */
    @Column(name = "skip_reason", length = 255)
    private String skipReason;

    /** 处置结论：超时 / 跳过记录结束演练前必填 */
    @Column(name = "disposition_note", length = 255)
    private String dispositionNote;

    @Column(name = "disposition_at")
    private LocalDateTime dispositionAt;

    @Column(name = "disposition_by", length = 32)
    private String dispositionBy;

    /** 是否因前置节点被跳过而级联阻塞跳过（阻塞来源仍在 skipReason 里写明） */
    @Column(name = "blocked_by_cascade", nullable = false)
    private boolean blockedByCascade;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
