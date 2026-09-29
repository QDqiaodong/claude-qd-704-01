package com.fire.protect.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/** 楼层（平面图切换标签条的来源）。 */
@Data
@Entity
@Table(name = "building_floor")
public class BuildingFloor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "building_id", nullable = false)
    private Long buildingId;

    /** B2 / B1 / 1F / 2F ... */
    @Column(name = "floor_code", nullable = false, length = 8)
    private String floorCode;

    @Column(name = "floor_name", length = 32)
    private String floorName;

    /** 排序号：地下在前 */
    @Column(name = "sort_no", nullable = false)
    private Integer sortNo;

    @Column(name = "area_sqm", precision = 10, scale = 2)
    private BigDecimal areaSqm;

    @Column(name = "room_count")
    private Integer roomCount;

    @Column(name = "exit_count")
    private Integer exitCount;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
