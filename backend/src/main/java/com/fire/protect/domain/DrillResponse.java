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
 * 岗位响应尝试：每张指令卡对应岗位的独立打点记录。
 * 按时确认 {@code attemptKind=ON_TIME}；终态（超时/跳过/确认）后到达的确认记 LATE，
 * 不改变卡终态，但保留在岗位记录与时间线中。
 */
@Data
@Entity
@Table(name = "drill_response")
public class DrillResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "drill_id", nullable = false)
    private Long drillId;

    @Column(name = "card_id", nullable = false)
    private Long cardId;

    @Column(name = "position_id", nullable = false)
    private Long positionId;

    /** ON_TIME 按时确认 / LATE 迟到尝试 */
    @Column(name = "attempt_kind", nullable = false, length = 16)
    private String attemptKind;

    @Column(name = "actual_action", nullable = false, length = 255)
    private String actualAction;

    @Column(name = "remark", length = 255)
    private String remark;

    @Column(name = "responder", nullable = false, length = 32)
    private String responder;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
