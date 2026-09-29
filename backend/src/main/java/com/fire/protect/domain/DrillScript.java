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
 * 演练常用脚本。脚本是独立模板：应用到草稿场次时整体复制一份为该场指令链，
 * 之后脚本再被修改 / 删除，已经开始（甚至已就绪冻结）的场次都不受影响。
 */
@Data
@Entity
@Table(name = "drill_script")
public class DrillScript {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "scenario", length = 255)
    private String scenario;

    @Column(name = "created_by", nullable = false, length = 32)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
