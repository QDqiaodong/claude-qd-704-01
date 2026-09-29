package com.fire.protect.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;

/**
 * 消防演练场次。
 *
 * <p>状态机：DRAFT 草稿 → READY 就绪 → RUNNING 进行中 ⇄ PAUSED 暂停 → FINISHED 已完成；
 * DRAFT / READY / RUNNING / PAUSED 可旁支到 CANCELED 已取消。
 *
 * <p>开始（START）时冻结本场指令链：此后指令卡 / 岗位 / 前置关系一律不可改，
 * 常用脚本再怎么改都与本场无关。{@code version} 乐观锁 + 动作事务内的行级悲观锁，
 * 保证多个执行页面交替操作时状态推进与事件顺序一致。
 */
@Data
@Entity
@Table(name = "drill_session")
public class DrillSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 演练编号 DR-年-四位序号 */
    @Column(name = "drill_no", nullable = false, length = 24, updatable = false)
    private String drillNo;

    @Column(name = "title", nullable = false, length = 128)
    private String title;

    @Column(name = "building_id")
    private Long buildingId;

    @Column(name = "scenario", length = 500)
    private String scenario;

    /** 演练组织者 */
    @Column(name = "organizer", nullable = false, length = 32)
    private String organizer;

    /** DRAFT / READY / RUNNING / PAUSED / FINISHED / CANCELED */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "ready_at")
    private LocalDateTime readyAt;

    /** 开始时间：此刻冻结指令链快照 */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "paused_at")
    private LocalDateTime pausedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "finished_by", length = 32)
    private String finishedBy;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "canceled_by", length = 32)
    private String canceledBy;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
