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
 * 演练参与岗位。岗位在指令链冻结前由组织者编排；
 * 每个岗位拥有独立的响应记录（{@link DrillResponse}），互不干扰。
 */
@Data
@Entity
@Table(name = "drill_position")
public class DrillPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "drill_id", nullable = false)
    private Long drillId;

    /** 岗位代号（场次内唯一），脚本应用时按代号映射 */
    @Column(name = "pos_code", nullable = false, length = 32)
    private String posCode;

    @Column(name = "pos_name", nullable = false, length = 64)
    private String posName;

    /** 岗位人员姓名（确认人提示） */
    @Column(name = "member_name", length = 32)
    private String memberName;

    /** 绑定的系统用户；非空时只有该用户能确认本岗位的卡 */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "contact", length = 32)
    private String contact;

    @Column(name = "sort_no", nullable = false)
    private int sortNo;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
