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
 * 演练事件流（只追加）。
 *
 * <p>开始、就绪、下达、确认、超时、暂停、恢复、跳过、取消、结束、处置登记、迟到尝试
 * 都写一条；包含操作者、服务端时间、动作前后状态与业务说明。
 * id 自增顺序即服务端时间顺序，时间线据此展示。
 *
 * <p>事件只在动作真正成功的事务里写入 —— 幂等命中 / 乐观锁冲突 / 校验失败都不写。
 */
@Data
@Entity
@Table(name = "drill_event")
public class DrillEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "drill_id", nullable = false)
    private Long drillId;

    /** CREATE/READY/START/ISSUE/CONFIRM/TIMEOUT/PAUSE/RESUME/SKIP/CANCEL/FINISH/DISPOSE/LATE_ATTEMPT */
    @Column(name = "event_type", nullable = false, length = 24)
    private String eventType;

    /** SESSION / CARD */
    @Column(name = "target_type", length = 16)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "from_status", length = 16)
    private String fromStatus;

    @Column(name = "to_status", length = 16)
    private String toStatus;

    @Column(name = "operator", nullable = false, length = 32)
    private String operator;

    @Column(name = "operator_role", nullable = false, length = 24)
    private String operatorRole;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
