package com.fire.protect.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/** 维保单：待派工 → 已派工 → 处理中 → 已完成 → 已验收。 */
@Data
@Entity
@Table(name = "maintenance_order")
public class MaintenanceOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, length = 24, unique = true)
    private String orderNo;

    /** REPAIR 维修 / REPLACE 更换 / REFILL 充装 / TEST 检测 */
    @Column(name = "order_type", nullable = false, length = 16)
    private String orderType;

    /** 关联消防设施点位（与 ext_id 二选一） */
    @Column(name = "point_id")
    private Long pointId;

    /** 关联气瓶台账 */
    @Column(name = "ext_id")
    private Long extId;

    @Column(name = "fault_desc", length = 255)
    private String faultDesc;

    @Column(name = "solution", length = 255)
    private String solution;

    @Column(name = "material_fee", precision = 10, scale = 2)
    private BigDecimal materialFee;

    @Column(name = "labor_fee", precision = 10, scale = 2)
    private BigDecimal laborFee;

    /** material_fee + labor_fee */
    @Column(name = "total_fee", precision = 10, scale = 2)
    private BigDecimal totalFee;

    @Column(name = "vendor", length = 64)
    private String vendor;

    @Column(name = "owner_person", length = 32)
    private String ownerPerson;

    /** PENDING 待派工 / ASSIGNED 已派工 / WORKING 处理中 / DONE 已完成 / ACCEPTED 已验收 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "finish_date")
    private LocalDate finishDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
