package com.fire.protect.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 指令卡前置节点（多对多有向边）。
 * 只有全部前置都已确认（CONFIRMED）的卡才允许下达；前置被跳过的卡保持阻塞并展示来源。
 */
@Data
@Entity
@Table(name = "drill_card_prereq")
public class DrillCardPrereq {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 后置卡 */
    @Column(name = "card_id", nullable = false)
    private Long cardId;

    /** 前置卡 */
    @Column(name = "prereq_card_id", nullable = false)
    private Long prereqCardId;
}
