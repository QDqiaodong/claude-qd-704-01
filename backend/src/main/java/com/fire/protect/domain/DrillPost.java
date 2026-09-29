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
 * 演练参与岗位。每个岗位有独立的岗位响应记录（执行页按岗位聚合状态与响应明细）。
 * 草稿期可增删改；场次开始后冻结，指令卡上的目标岗位改存姓名/编码快照。
 */
@Data
@Entity
@Table(name = "drill_post")
public class DrillPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /** 岗位编码，本场内唯一；指令卡/脚本都按编码引用岗位 */
    @Column(name = "post_code", nullable = false, length = 32)
    private String postCode;

    @Column(name = "post_name", nullable = false, length = 64)
    private String postName;

    /** 岗位人员姓名 */
    @Column(name = "assignee", length = 32)
    private String assignee;

    @Column(name = "contact", length = 32)
    private String contact;

    @Column(name = "sort_no", nullable = false)
    private int sortNo;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
