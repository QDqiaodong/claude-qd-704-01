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
 * 补偿措施（逐项登记）：替代灭火器 / 加密巡查 / 临时警戒 / 其它。
 *
 * <p>两阶段收口：{@code confirmedAt} 为现场负责人逐项确认时间 ——
 * 发起复位核验的前置条件之一是全部措施已确认收口。
 */
@Data
@Entity
@Table(name = "disable_measure")
public class DisableMeasure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "permit_id", nullable = false)
    private Long permitId;

    /** SPARE_EXT 替代灭火器 / EXTRA_PATROL 加密巡查 / TEMP_BARRIER 临时警戒 / OTHER 其它 */
    @Column(name = "measure_type", nullable = false, length = 24)
    private String measureType;

    @Column(name = "content", nullable = false, length = 255)
    private String content;

    /** 数量 / 频次等，如「4 具 4kg 干粉」「每 2 小时一次」 */
    @Column(name = "spec_desc", length = 128)
    private String specDesc;

    @Column(name = "created_by", nullable = false, length = 32)
    private String createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** 现场负责人逐项确认收口的时间；null 表示未完成 */
    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "confirmed_by", length = 32)
    private String confirmedBy;

    @Column(name = "confirm_remark", length = 255)
    private String confirmRemark;
}
