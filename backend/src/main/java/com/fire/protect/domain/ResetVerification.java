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
 * 逐点位复位核验记录。每发起一轮复位核验，给每个点位留一行；
 * 修正后可以再次核验（产生新的一轮，历史轮次保留可追溯）。
 *
 * <p>整轮全 PASS 才允许关闭许可；任一点位 FAIL 整张许可保持「待复位」，
 * 失败原因同时汇总写到许可主表 {@code last_verify_fail_reason}。
 */
@Data
@Entity
@Table(name = "reset_verification")
public class ResetVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 同一轮核验共享一个批次号 */
    @Column(name = "batch_no", nullable = false, length = 24)
    private String batchNo;

    @Column(name = "permit_id", nullable = false)
    private Long permitId;

    @Column(name = "point_id", nullable = false)
    private Long pointId;

    /** PASS 合格 / FAIL 不合格 */
    @Column(name = "result", nullable = false, length = 8)
    private String result;

    @Column(name = "verify_desc", length = 255)
    private String verifyDesc;

    /** 不合格时必填的失败原因 */
    @Column(name = "fail_reason", length = 255)
    private String failReason;

    @Column(name = "verified_by", nullable = false, length = 32)
    private String verifiedBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
