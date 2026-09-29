package com.fire.protect.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/** 巡检记录（主表）。 */
@Data
@Entity
@Table(name = "inspection")
public class Inspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inspect_no", nullable = false, length = 24, unique = true)
    private String inspectNo;

    @Column(name = "building_id", nullable = false)
    private Long buildingId;

    @Column(name = "inspector", nullable = false, length = 32)
    private String inspector;

    @Column(name = "inspect_date", nullable = false)
    private LocalDate inspectDate;

    /** DAILY 日巡 / MONTHLY 月检 / QUARTERLY 季检 / YEARLY 年检 */
    @Column(name = "inspect_type", nullable = false, length = 16)
    private String inspectType;

    @Column(name = "expected_count", nullable = false)
    private Integer expectedCount;

    @Column(name = "actual_count", nullable = false)
    private Integer actualCount;

    @Column(name = "abnormal_count", nullable = false)
    private Integer abnormalCount;

    /** PROCESSING 进行中 / COMPLETED 已完成 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "remark", length = 128)
    private String remark;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
