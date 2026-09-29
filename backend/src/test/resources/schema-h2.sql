-- H2（MySQL 兼容模式）专用：只建演练模块 + 安全过滤器需要的表，用于集成测试
SET MODE MySQL;

CREATE TABLE sys_user (
    id           BIGINT       NOT NULL,
    user_name    VARCHAR(32)  NOT NULL,
    display_name VARCHAR(32)  NOT NULL,
    role         VARCHAR(24)  NOT NULL,
    phone        VARCHAR(20),
    created_at   TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (user_name)
);

INSERT INTO sys_user (id, user_name, display_name, role, phone, created_at) VALUES
    (1, 'fire_lead', '陈默', 'FIRE_MANAGER', '13900000001', '2026-01-08 09:00:00'),
    (2, 'site_zhou', '周大勇', 'SITE_OWNER', '13900000002', '2026-01-08 09:00:00'),
    (3, 'site_wu', '吴迪', 'SITE_OWNER', '13900000003', '2026-01-08 09:00:00');

CREATE TABLE building (
    id BIGINT NOT NULL PRIMARY KEY,
    code VARCHAR(16), name VARCHAR(64), address VARCHAR(128),
    building_type VARCHAR(16), floors_above INT, floors_below INT,
    area_sqm DECIMAL(10,2), fire_manager VARCHAR(32), contact_phone VARCHAR(20),
    created_at TIMESTAMP, updated_at TIMESTAMP
);

CREATE TABLE drill_session (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    drill_no       VARCHAR(24)  NOT NULL UNIQUE,
    title          VARCHAR(128) NOT NULL,
    building_id    BIGINT,
    scenario       VARCHAR(500),
    organizer      VARCHAR(32)  NOT NULL,
    status         VARCHAR(16)  NOT NULL,
    ready_at       TIMESTAMP NULL,
    started_at     TIMESTAMP NULL,
    paused_at      TIMESTAMP NULL,
    finished_at    TIMESTAMP NULL,
    finished_by    VARCHAR(32),
    canceled_at    TIMESTAMP NULL,
    canceled_by    VARCHAR(32),
    cancel_reason  VARCHAR(255),
    created_at     TIMESTAMP NOT NULL,
    updated_at     TIMESTAMP NOT NULL,
    version        BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_drill_status ON drill_session (status);

CREATE TABLE drill_post (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    post_code VARCHAR(32) NOT NULL,
    post_name VARCHAR(64) NOT NULL,
    assignee VARCHAR(32),
    contact VARCHAR(32),
    sort_no INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    UNIQUE (session_id, post_code)
);

CREATE TABLE drill_card (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    seq_no INT NOT NULL,
    title VARCHAR(128) NOT NULL,
    content VARCHAR(500) NOT NULL,
    scene_hint VARCHAR(500),
    target_post_id BIGINT,
    target_post_code VARCHAR(32),
    target_post_name VARCHAR(64),
    time_limit_seconds INT NOT NULL DEFAULT 60,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    issued_at TIMESTAMP NULL,
    issued_by VARCHAR(32),
    deadline TIMESTAMP NULL,
    remaining_frozen_seconds INT,
    confirmed_at TIMESTAMP NULL,
    confirmed_by VARCHAR(32),
    actual_action VARCHAR(500),
    confirm_remark VARCHAR(500),
    timed_out_at TIMESTAMP NULL,
    skipped_at TIMESTAMP NULL,
    skipped_by VARCHAR(32),
    skip_reason VARCHAR(500),
    disposition VARCHAR(500),
    disposition_at TIMESTAMP NULL,
    disposition_by VARCHAR(32),
    created_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_card_session ON drill_card (session_id, seq_no);

CREATE TABLE drill_card_dependency (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    card_id BIGINT NOT NULL,
    prereq_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    UNIQUE (card_id, prereq_id)
);

CREATE TABLE drill_response_attempt (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    card_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    result VARCHAR(16) NOT NULL,
    actual_action VARCHAR(500) NOT NULL,
    remark VARCHAR(500),
    operator VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE drill_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    action VARCHAR(24) NOT NULL,
    target_type VARCHAR(16),
    target_id BIGINT,
    action_key VARCHAR(64),
    from_status VARCHAR(16),
    to_status VARCHAR(16),
    operator VARCHAR(32) NOT NULL,
    note VARCHAR(1000),
    created_at TIMESTAMP NOT NULL,
    UNIQUE (session_id, action_key)
);

CREATE TABLE drill_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL UNIQUE,
    description VARCHAR(500),
    created_by VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE drill_template_post (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_id BIGINT NOT NULL,
    post_code VARCHAR(32) NOT NULL,
    post_name VARCHAR(64) NOT NULL,
    sort_no INT NOT NULL DEFAULT 0
);

CREATE TABLE drill_template_card (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_id BIGINT NOT NULL,
    seq_no INT NOT NULL,
    title VARCHAR(128) NOT NULL,
    content VARCHAR(500) NOT NULL,
    scene_hint VARCHAR(500),
    target_post_code VARCHAR(32),
    time_limit_seconds INT NOT NULL DEFAULT 60,
    prereq_seqs VARCHAR(128)
);
