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
 * 复位前置条件之一：许可关联点位上「未完成维保单」的处理说明。
 *
 * <p>关联维保单正常推进到已完成 / 已验收视为已处理；现场负责人也可以逐单登记说明
 * （转其它许可覆盖 / 与本停用无关 / 另案跟踪等），视为已处理。一张未处理的单都不能发起复位。
 */
@Data
@Entity
@Table(name = "permit_order_waiver")
public class PermitOrderWaiver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "permit_id", nullable = false)
    private Long permitId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "handle_note", nullable = false, length = 255)
    private String handleNote;

    @Column(name = "created_by", nullable = false, length = 32)
    private String createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
