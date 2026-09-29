package com.fire.protect.domain;

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
 * 消防设施点位（继承 {@link BaseFacility}，并改写三个列名）。
 *
 * <p>写入路径上不再手写任何日期计算：
 * <ul>
 *   <li>新增 → {@link FacilityLifecycleListener} 的 {@code @PrePersist} 自动生成 FAC-&lt;建筑编号&gt;-&lt;四位序号&gt; 并按周期算下次检查日；</li>
 *   <li>标记已检 / 巡检完成 → {@code @PreUpdate} 按上次检查日重算下次检查日，过期则自动回落「待更换」。</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "facility_point")
@EntityListeners(FacilityLifecycleListener.class)
@AttributeOverrides({
        @AttributeOverride(name = "pointCode", column = @Column(name = "fp_no", nullable = false, length = 32, updatable = false)),
        @AttributeOverride(name = "locationDesc", column = @Column(name = "place_desc", length = 128)),
        @AttributeOverride(name = "installDate", column = @Column(name = "installed_on"))
})
public class FacilityPoint extends BaseFacility {

    /** 设施类型：EXTINGUISHER / HYDRANT / SPRINKLER / SMOKE / ALARM / LIGHT / DOOR / PUMP */
    @Column(name = "facility_type", nullable = false, length = 16)
    private String facilityType;

    /** 规格型号，如 MFZ/ABC4、SN65 */
    @Column(name = "spec", length = 64)
    private String spec;

    @Override
    public String codePrefix() {
        return FireDict.FACILITY_CODE_PREFIX;
    }

    @Override
    public String overdueStatus() {
        return "REPLACE";
    }

    @Override
    public int defaultCycleDays() {
        return FireDict.cycleDays(facilityType);
    }

    @Override
    public String facilityType() {
        return facilityType;
    }
}
