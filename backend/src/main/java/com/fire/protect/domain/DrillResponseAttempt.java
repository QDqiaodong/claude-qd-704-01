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
 * 岗位响应尝试（只追加）。按时确认记 CONFIRMED 并推进指令卡；
 * 指令卡已超时 / 已跳过后到达的确认记 LATE 迟到尝试 —— 不改变终态，
 * 但完整保留在岗位响应记录与时间线里。
 */
@Data
@Entity
@Table(name = "drill_response_attempt")
public class DrillResponseAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "card_id", nullable = false)
    private Long cardId;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    /** CONFIRMED 按时确认 / LATE 迟到尝试 */
    @Column(name = "result", nullable = false, length = 16)
    private String result;

    @Column(name = "actual_action", nullable = false, length = 500)
    private String actualAction;

    @Column(name = "remark", length = 500)
    private String remark;

    @Column(name = "operator", nullable = false, length = 32)
    private String operator;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
