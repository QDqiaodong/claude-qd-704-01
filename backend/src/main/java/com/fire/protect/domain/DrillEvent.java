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
 * 演练事件记录（只追加）。开始 / 就绪 / 下达 / 确认 / 迟到确认 / 超时 / 暂停 / 恢复 /
 * 跳过 / 处置 / 取消 / 结束，每次都写一条：操作者、服务端时间、动作前后状态与业务说明。
 *
 * <p>{@code action_key} 为客户端动作标识：同一场次内唯一（库级约束兜底），
 * 客户端重复发送同一标识不会产生第二条事件、不会重复推进状态；
 * 服务端定时扫描产生的 TIMEOUT 事件 action_key 为 NULL。
 */
@Data
@Entity
@Table(name = "drill_event")
public class DrillEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /** CREATE / READY / START / ISSUE / CONFIRM / LATE_CONFIRM / TIMEOUT / PAUSE / RESUME / SKIP / DISPOSE / CANCEL / FINISH */
    @Column(name = "action", nullable = false, length = 24)
    private String action;

    /** SESSION / CARD */
    @Column(name = "target_type", length = 16)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    /** 客户端幂等动作标识（TIMEOUT 为服务端产生，为空） */
    @Column(name = "action_key", length = 64)
    private String actionKey;

    @Column(name = "from_status", length = 16)
    private String fromStatus;

    @Column(name = "to_status", length = 16)
    private String toStatus;

    @Column(name = "operator", nullable = false, length = 32)
    private String operator;

    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
