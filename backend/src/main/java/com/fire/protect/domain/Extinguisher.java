package com.fire.protect.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fire.protect.listener.FacilityLifecycleListener;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 灭火器 / 气瓶台账（同样继承 {@link BaseFacility}，列名改写与点位表不同）。
 *
 * <p>巡检周期固定按 30 天（月检）；一旦超期未检，{@code @PreUpdate} 会把状态落回「待充装」；
 * 到报废日期则直接置「报废」。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "extinguisher")
@EntityListeners(FacilityLifecycleListener.class)
@AttributeOverrides({
        @AttributeOverride(name = "pointCode", column = @Column(name = "ext_no", nullable = false, length = 32, updatable = false)),
        @AttributeOverride(name = "locationDesc", column = @Column(name = "store_place", length = 128)),
        @AttributeOverride(name = "installDate", column = @Column(name = "installed_on")),
        @AttributeOverride(name = "checkCycleDays", column = @Column(name = "patrol_cycle_days")),
        @AttributeOverride(name = "lastCheckDate", column = @Column(name = "last_patrol_on")),
        @AttributeOverride(name = "nextCheckDate", column = @Column(name = "next_patrol_on"))
})
public class Extinguisher extends BaseFacility {

    /** 钢印编号（唯一） */
    @Column(name = "steel_no", nullable = false, length = 32, unique = true)
    private String steelNo;

    /** 类型：POWDER 干粉 / CO2 二氧化碳 / WATER 水基 / FOAM 泡沫 */
    @Column(name = "cyl_type", nullable = false, length = 16)
    private String cylType;

    /** 充装量（kg / L） */
    @Column(name = "charge_kg", nullable = false, precision = 6, scale = 2)
    private BigDecimal chargeKg;

    @Column(name = "produce_date")
    private LocalDate produceDate;

    /** 上次充装日 */
    @Column(name = "last_fill_date")
    private LocalDate lastFillDate;

    /** 水压试验到期日 */
    @Column(name = "hydro_test_due")
    private LocalDate hydroTestDue;

    /** 报废日期 */
    @Column(name = "scrap_date")
    private LocalDate scrapDate;

    /** 存放点位（指向 facility_point 的灭火器点位） */
    @Column(name = "point_id")
    private Long pointId;

    @Override
    public String codePrefix() {
        return FireDict.EXTINGUISHER_CODE_PREFIX;
    }

    @Override
    public String overdueStatus() {
        return "REFILL";
    }

    @Override
    public int defaultCycleDays() {
        return 30;
    }

    @Override
    public String facilityType() {
        return "EXTINGUISHER";
    }
}
