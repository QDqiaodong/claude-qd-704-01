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
 * 消防演练场次主表。
 *
 * <p>状态机：DRAFT 草稿 → READY 就绪 → RUNNING 进行中 ⇄ PAUSED 暂停 → COMPLETED 已完成；
 * CANCELED 已取消是任意未终结状态的旁支。
 *
 * <p>{@code chainFrozen} 指令链冻结标志：置为就绪即冻结（开始时再次确认），
 * 冻结后排版（岗位/指令卡/前置关系）只读；常用脚本之后的任何修改都不会回写本场。
 *
 * <p>{@code version} 乐观锁：同一场次并发动作只有一个能成功。
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

    @Column(name = "scenario", length = 255)
    private String scenario;

    @Column(name = "organizer_name", nullable = false, length = 32)
    private String organizerName;

    /** DRAFT / READY / RUNNING / PAUSED / COMPLETED / CANCELED */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 指令链冻结：就绪即冻结，开始后不可改 */
    @Column(name = "chain_frozen", nullable = false)
    private boolean chainFrozen;

    @Column(name = "ready_at")
    private LocalDateTime readyAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "paused_at")
    private LocalDateTime pausedAt;

    /** 累计暂停秒数（每次恢复累加），全场时间轴据此扣除暂停时长 */
    @Column(name = "paused_total_sec", nullable = false)
    private long pausedTotalSec;

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
