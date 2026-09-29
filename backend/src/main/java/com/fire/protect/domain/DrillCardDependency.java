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
 * 指令卡前置节点（多对多）。只有全部前置节点都已确认的卡才能下达；
 * 前置超时 / 跳过时，依赖卡明确显示阻塞来源，不能悄悄越过。
 * 场次开始后整张依赖链冻结。
 */
@Data
@Entity
@Table(name = "drill_card_dependency")
public class DrillCardDependency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "card_id", nullable = false)
    private Long cardId;

    @Column(name = "prereq_id", nullable = false)
    private Long prereqId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
