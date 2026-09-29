package com.fire.protect.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/** 巡检明细行（一个巡检 × 一个点位一条）。 */
@Data
@Entity
@Table(name = "inspection_item")
public class InspectionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inspection_id", nullable = false)
    private Long inspectionId;

    @Column(name = "point_id", nullable = false)
    private Long pointId;

    /** PENDING 未检 / NORMAL 正常 / ABNORMAL 异常 */
    @Column(name = "result", nullable = false, length = 16)
    private String result;

    @Column(name = "problem_desc", length = 255)
    private String problemDesc;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
