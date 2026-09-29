package com.fire.protect.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fire.protect.listener.FacilityLifecycleListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Data;

/**
 * 消防设施通用字段（{@code @MappedSuperclass} 基类）。
 *
 * <p>子类 facility_point / extinguisher 通过 {@code @AttributeOverride} 改写其中部分列名，
 * 并把编码生成、到期日推算、过期回落状态交给 {@link FacilityLifecycleListener}，
 * 所以 service 里看不到任何「算下次检查日」的手写代码 —— 全部在实体生命周期里完成。
 */
@Data
@MappedSuperclass
@EntityListeners(FacilityLifecycleListener.class)
public abstract class BaseFacility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 点位编码：facility_point 里叫 fp_no，extinguisher 里叫 ext_no（由 @AttributeOverride 改写） */
    @Column(name = "point_code", nullable = false, length = 32, updatable = false)
    private String pointCode;

    /** 所属建筑 */
    @Column(name = "building_id", nullable = false)
    private Long buildingId;

    /** 所属楼层 */
    @Column(name = "floor_id")
    private Long floorId;

    /** 位置描述：子类里改写成 place_desc / store_place */
    @Column(name = "location_desc", length = 128)
    private String locationDesc;

    /** 安装日期 */
    @Column(name = "install_date")
    private LocalDate installDate;

    /** 检查周期（天），用于推算下次检查日 */
    @Column(name = "check_cycle_days")
    private Integer checkCycleDays;

    @Column(name = "last_check_date")
    private LocalDate lastCheckDate;

    @Column(name = "next_check_date")
    private LocalDate nextCheckDate;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /**
     * 叠加式「许可停用」标记：非空 = 该点位正被这张生效 / 待复位的停用许可挂起。
     * 与 {@link #status}（正常 / 故障 / 待更换 / 停用）互相独立 —— 许可生效不改原状态，
     * 关闭许可时清空，平面图 / 台账同时显示原状态与「许可停用」。
     */
    @Column(name = "active_disable_permit_id")
    private Long activeDisablePermitId;

    /** 平面图横坐标（0~1000） */
    @Column(name = "plan_x")
    private Integer planX;

    /** 平面图纵坐标（0~660） */
    @Column(name = "plan_y")
    private Integer planY;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 编码前缀：FAC / EXT */
    public abstract String codePrefix();

    /** 未按周期复检时自动回落到的状态：点位=待更换，气瓶=待充装 */
    public abstract String overdueStatus();

    /** 缺省检查周期（天） */
    public abstract int defaultCycleDays();

    /** 设施类型：决定缺省巡检周期 */
    public abstract String facilityType();
}
