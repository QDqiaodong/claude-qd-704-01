package com.fire.protect.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/** 脚本岗位（导入场次时按岗位编码对应到场次岗位） */
@Data
@Entity
@Table(name = "drill_template_post")
public class DrillTemplatePost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "post_code", nullable = false, length = 32)
    private String postCode;

    @Column(name = "post_name", nullable = false, length = 64)
    private String postName;

    @Column(name = "sort_no", nullable = false)
    private int sortNo;
}
