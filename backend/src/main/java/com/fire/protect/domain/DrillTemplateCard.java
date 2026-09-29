package com.fire.protect.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/** 脚本指令卡。前置节点用顺序号逗号串表达（如 "1,2"），导入时按顺序号映射成场次卡依赖。 */
@Data
@Entity
@Table(name = "drill_template_card")
public class DrillTemplateCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "seq_no", nullable = false)
    private int seqNo;

    @Column(name = "title", nullable = false, length = 128)
    private String title;

    @Column(name = "content", nullable = false, length = 500)
    private String content;

    @Column(name = "scene_hint", length = 500)
    private String sceneHint;

    @Column(name = "target_post_code", length = 32)
    private String targetPostCode;

    @Column(name = "time_limit_seconds", nullable = false)
    private int timeLimitSeconds;

    /** 前置节点顺序号，逗号分隔（如 1,2）；空表示首张无前置卡 */
    @Column(name = "prereq_seqs", length = 128)
    private String prereqSeqs;
}
