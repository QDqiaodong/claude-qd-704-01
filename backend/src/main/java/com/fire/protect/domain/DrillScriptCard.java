package com.fire.protect.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 脚本指令卡。{@code prereqSeqs} 以前置卡顺序号逗号分隔存储（如 "1,2"），
 * 应用到场次时按顺序号映射为场次内卡片 id 的前置边。
 */
@Data
@Entity
@Table(name = "drill_script_card")
public class DrillScriptCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "script_id", nullable = false)
    private Long scriptId;

    @Column(name = "seq_no", nullable = false)
    private int seqNo;

    @Column(name = "title", nullable = false, length = 128)
    private String title;

    @Column(name = "target_pos_code", nullable = false, length = 32)
    private String targetPosCode;

    @Column(name = "time_limit_sec", nullable = false)
    private int timeLimitSec;

    @Column(name = "on_site_hint", length = 255)
    private String onSiteHint;

    /** 前置顺序号逗号分隔 */
    @Column(name = "prereq_seqs", length = 64)
    private String prereqSeqs;
}
