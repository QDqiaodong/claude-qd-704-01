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
 * 停用许可受影响点位行（许可范围）。
 *
 * <p>{@code originStatus} 是许可生效那一刻设施原状态（正常 / 故障 / 待更换 / 停用）的快照：
 * 许可停用是叠加标记，绝不覆盖原状态 —— 关闭许可后平面图 / 台账仍显示这个原状态。
 *
 * <p>{@code marked} 表示该点位当前是否被许可挂着「许可停用」标记：批准时批量置 true，
 * 关闭时在同一事务里批量置 false，保证不会出现「许可已关闭但部分点位仍显示停用」。
 */
@Data
@Entity
@Table(name = "disable_permit_point")
public class DisablePermitPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "permit_id", nullable = false)
    private Long permitId;

    @Column(name = "point_id", nullable = false)
    private Long pointId;

    /** 批准生效时的设施原状态快照 */
    @Column(name = "origin_status", length = 16)
    private String originStatus;

    /** 当前是否处于许可停用标记中 */
    @Column(name = "marked", nullable = false)
    private boolean marked;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
