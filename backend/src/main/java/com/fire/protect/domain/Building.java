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

/** 建筑（维保服务对象）。 */
@Data
@Entity
@Table(name = "building")
public class Building {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 建筑编号，用于点位编码：B02 → FAC-B02-0007 */
    @Column(name = "code", nullable = false, length = 16, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "address", length = 128)
    private String address;

    /** RESIDENTIAL 住宅 / COMMERCIAL 商业 / FACTORY 厂房 / OFFICE 办公 / WAREHOUSE 仓储 */
    @Column(name = "building_type", nullable = false, length = 16)
    private String buildingType;

    @Column(name = "floors_above", nullable = false)
    private Integer floorsAbove;

    @Column(name = "floors_below", nullable = false)
    private Integer floorsBelow;

    @Column(name = "area_sqm", precision = 10, scale = 2)
    private BigDecimal areaSqm;

    @Column(name = "fire_manager", length = 32)
    private String fireManager;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
