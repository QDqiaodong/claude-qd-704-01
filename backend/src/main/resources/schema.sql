-- ============================================================================
-- 消防设施维保管理系统 · 建表 + 演示数据
-- 库名 fire_protect，由 mysql 镜像的 docker-entrypoint 在【镜像构建期】执行一次。
--
-- 五个业务模块：
--   1. building / building_floor        建筑与楼层
--   2. facility_point                   消防设施点位（继承 BaseFacility）
--   3. inspection / inspection_item     巡检记录与明细行
--   4. maintenance_order                维保单（待派工→已派工→处理中→已完成→已验收）
--   5. extinguisher                     灭火器与气瓶台账（同样继承 BaseFacility）
--
-- 数据访问主线：@MappedSuperclass + @AttributeOverride + JPA 生命周期回调。
--   facility_point 用 @AttributeOverride 把 point_code→fp_no、location_desc→place_desc、
--   install_date→installed_on；extinguisher 改写成 ext_no / store_place / patrol_* 。
--   @PrePersist：自动生成 FAC-<建筑编号>-<四位序号> 并按检查周期算下次检查日；
--   @PreUpdate：按上次检查日重算下次检查日，过期自动回落「待更换 / 待充装」。
--
-- 本次演示数据围绕主场景建筑 B02「中心商业广场 A 座」（地下 2 层 + 地上 6 层）铺开：
--   [平面图] B02 每层都有 3~5 个点位，带 plan_x/plan_y 坐标，六层有一处停用点位
--   [状态色] NORMAL 正常 / REPLACE 待更换 / FAULT 故障 / DISABLED 停用，四种状态都有样本
--   [过期]   FAC-B02-0003 / 0018 / 0024 下次检查日已过 → 待更换
--   [巡检]   INS-2026-0011 / 0012 进行中，其余已完成，明细行全部带结论
--   [维保]   五种状态齐活：待派工 3 / 已派工 2 / 处理中 3 / 已完成 3 / 已验收 3
--   [气瓶]   水压到期 DANGER（MO-2026-0004 在检）、报废 SCRAP、临期 WARN 各若干
-- ============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS fire_protect DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE fire_protect;

DROP TABLE IF EXISTS permit_order_waiver;
DROP TABLE IF EXISTS reset_verification;
DROP TABLE IF EXISTS permit_action_log;
DROP TABLE IF EXISTS disable_measure;
DROP TABLE IF EXISTS disable_permit_point;
DROP TABLE IF EXISTS disable_permit;
DROP TABLE IF EXISTS sys_user;
DROP TABLE IF EXISTS maintenance_order;
DROP TABLE IF EXISTS inspection_item;
DROP TABLE IF EXISTS inspection;
DROP TABLE IF EXISTS extinguisher;
DROP TABLE IF EXISTS facility_point;
DROP TABLE IF EXISTS building_floor;
DROP TABLE IF EXISTS building;

-- ----------------------------------------------------------------------------
-- 模块一：建筑与楼层
-- ----------------------------------------------------------------------------
CREATE TABLE building (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    code          VARCHAR(16)   NOT NULL COMMENT '建筑编号 B01/B02...，用于点位编码',
    name          VARCHAR(64)   NOT NULL COMMENT '建筑名称',
    address       VARCHAR(128)           COMMENT '详细地址',
    building_type VARCHAR(16)   NOT NULL COMMENT 'RESIDENTIAL 住宅 / COMMERCIAL 商业 / FACTORY 厂房 / OFFICE 办公 / WAREHOUSE 仓储',
    floors_above  INT           NOT NULL DEFAULT 0 COMMENT '地上层数',
    floors_below  INT           NOT NULL DEFAULT 0 COMMENT '地下层数',
    area_sqm      DECIMAL(10,2)          COMMENT '建筑面积（平方米）',
    fire_manager  VARCHAR(32)            COMMENT '消防安全负责人',
    contact_phone VARCHAR(20)            COMMENT '联系电话',
    created_at    DATETIME               COMMENT '建档时间',
    updated_at    DATETIME               COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_building_code (code),
    UNIQUE KEY uk_building_name (name),
    KEY idx_building_type (building_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '建筑';

CREATE TABLE building_floor (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    building_id BIGINT        NOT NULL COMMENT '所属建筑',
    floor_code  VARCHAR(8)    NOT NULL COMMENT '楼层代号 B2/B1/1F/2F...',
    floor_name  VARCHAR(32)   NOT NULL COMMENT '楼层名称',
    sort_no     INT           NOT NULL DEFAULT 0 COMMENT '排序号，地下在前',
    area_sqm    DECIMAL(10,2)          COMMENT '单层面积（平方米）',
    room_count  INT                    COMMENT '房间/防火分区数',
    exit_count  INT                    COMMENT '安全出口数',
    created_at  DATETIME               COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_floor_building (building_id, sort_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '楼层';

INSERT INTO building (id, code, name, address, building_type, floors_above, floors_below, area_sqm, fire_manager, contact_phone, created_at, updated_at) VALUES
    (1, 'B01', '望江名苑 3 号楼', '江岸区沿江大道 118 号', 'RESIDENTIAL', 18, 1, 26800.00, '李国栋', '13907120001', '2026-01-08 09:12:00', '2026-09-01 09:12:00'),
    (2, 'B02', '中心商业广场 A 座', '江汉区建设大道 568 号', 'COMMERCIAL', 6, 2, 52400.00, '王海燕', '13907120002', '2026-01-08 09:12:00', '2026-09-18 15:30:00'),
    (3, 'B03', '云浦智造产业园 2 号厂房', '云浦工业园五号路 12 号', 'FACTORY', 3, 0, 18600.00, '陈立仓', '13907120003', '2026-01-08 09:12:00', '2026-08-11 10:05:00'),
    (4, 'B04', '通达集团研发办公楼', '光谷三路 77 号', 'OFFICE', 12, 2, 31200.00, '赵明', '13907120004', '2026-01-08 09:12:00', '2026-07-22 11:20:00'),
    (5, 'B05', '港龙物流 A 库', '临空港物流园 6 号库', 'WAREHOUSE', 2, 0, 24500.00, '孙德柱', '13907120005', '2026-01-08 09:12:00', '2026-06-30 16:40:00');

INSERT INTO building_floor (id, building_id, floor_code, floor_name, sort_no, area_sqm, room_count, exit_count, created_at) VALUES
    (1,  1, '1F', '一层',   1, 1488.00, 4, 3, '2026-01-08 09:13:00'),
    (2,  1, '2F', '二层',   2, 1488.00, 6, 2, '2026-01-08 09:13:00'),
    (3,  1, '3F', '三层',   3, 1488.00, 6, 2, '2026-01-08 09:13:00'),
    (4,  2, 'B2', '地下二层', 1, 6550.00, 3, 2, '2026-01-08 09:13:00'),
    (5,  2, 'B1', '地下一层', 2, 6550.00, 3, 2, '2026-01-08 09:13:00'),
    (6,  2, '1F', '一层',   3, 6550.00, 4, 3, '2026-01-08 09:13:00'),
    (7,  2, '2F', '二层',   4, 6550.00, 6, 2, '2026-01-08 09:13:00'),
    (8,  2, '3F', '三层',   5, 6550.00, 6, 2, '2026-01-08 09:13:00'),
    (9,  2, '4F', '四层',   6, 6550.00, 6, 2, '2026-01-08 09:13:00'),
    (10, 2, '5F', '五层',   7, 6550.00, 6, 2, '2026-01-08 09:13:00'),
    (11, 2, '6F', '六层',   8, 6550.00, 4, 2, '2026-01-08 09:13:00'),
    (12, 3, '1F', '一层',   1, 6200.00, 4, 4, '2026-01-08 09:13:00'),
    (13, 3, '2F', '二层',   2, 6200.00, 3, 2, '2026-01-08 09:13:00'),
    (14, 4, 'B1', '地下一层', 1, 2228.00, 3, 2, '2026-01-08 09:13:00'),
    (15, 4, '1F', '一层',   2, 2228.00, 4, 3, '2026-01-08 09:13:00'),
    (16, 4, '2F', '二层',   3, 2228.00, 6, 2, '2026-01-08 09:13:00'),
    (17, 5, '1F', '一层',   1, 12250.00, 3, 4, '2026-01-08 09:13:00'),
    (18, 5, '2F', '二层',   2, 12250.00, 3, 2, '2026-01-08 09:13:00');

-- ----------------------------------------------------------------------------
-- 模块二：消防设施点位
-- 列名由 BaseFacility 的 @AttributeOverride 改写：fp_no / place_desc / installed_on
-- ----------------------------------------------------------------------------
CREATE TABLE facility_point (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    building_id      BIGINT       NOT NULL COMMENT '所属建筑',
    floor_id         BIGINT                COMMENT '所属楼层',
    fp_no            VARCHAR(32)  NOT NULL COMMENT '点位编号 FAC-<建筑编号>-<四位序号>（@PrePersist 生成）',
    facility_type    VARCHAR(16)  NOT NULL COMMENT 'EXTINGUISHER 灭火器 / HYDRANT 消火栓 / SPRINKLER 喷淋头 / SMOKE 烟感 / ALARM 报警按钮 / LIGHT 应急照明 / DOOR 防火门 / PUMP 消防泵',
    spec             VARCHAR(64)           COMMENT '规格型号',
    place_desc       VARCHAR(128)          COMMENT '位置描述',
    installed_on     DATE                  COMMENT '安装日期',
    check_cycle_days INT                   COMMENT '检查周期（天）：灭火器与消防泵 30，一般 90，喷淋头 180',
    last_check_date  DATE                  COMMENT '上次检查日',
    next_check_date  DATE                  COMMENT '下次检查日（@PrePersist / @PreUpdate 推算）',
    status           VARCHAR(16)  NOT NULL COMMENT 'NORMAL 正常 / REPLACE 待更换 / FAULT 故障 / DISABLED 停用（原状态始终保留）',
    active_disable_permit_id BIGINT COMMENT '叠加标记：正被哪张停用许可挂起（许可停用），关闭许可时清空，不覆盖 status',
    plan_x           INT                   COMMENT '平面图横坐标 0~1000',
    plan_y           INT                   COMMENT '平面图纵坐标 0~660',
    created_at       DATETIME              COMMENT '创建时间',
    updated_at       DATETIME              COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_point_fp_no (fp_no),
    KEY idx_point_building (building_id),
    KEY idx_point_floor (floor_id),
    KEY idx_point_status (status),
    KEY idx_point_permit (active_disable_permit_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '消防设施点位';

INSERT INTO facility_point (id, building_id, floor_id, fp_no, facility_type, spec, place_desc, installed_on, check_cycle_days, last_check_date, next_check_date, status, plan_x, plan_y, created_at, updated_at) VALUES
    -- ===== B02 · 地下二层 =====
    (1,  2,  4, 'FAC-B02-0001', 'PUMP',       'XBD8.0/40',     '地下二层消防泵房',        '2021-06-18', 30,  '2026-09-05', '2026-10-05', 'NORMAL',   860, 540, '2026-01-08 10:00:00', '2026-09-05 10:00:00'),
    (2,  2,  4, 'FAC-B02-0002', 'HYDRANT',    'SN65',          '地下二层车道东侧消火栓箱', '2021-06-18', 90,  '2026-07-20', '2026-10-18', 'NORMAL',   700, 340, '2026-01-08 10:00:00', '2026-07-20 10:00:00'),
    (3,  2,  4, 'FAC-B02-0003', 'SMOKE',      'JTY-GD-3002B',  '地下二层排烟机房顶部',     '2021-06-18', 90,  '2026-06-15', '2026-09-13', 'REPLACE',  400, 210, '2026-01-08 10:00:00', '2026-06-15 10:00:00'),
    -- ===== B02 · 地下一层 =====
    (4,  2,  5, 'FAC-B02-0004', 'HYDRANT',    'SN65',          '地下一层超市入口西侧',     '2021-06-18', 90,  '2026-08-30', '2026-11-28', 'NORMAL',   300, 340, '2026-01-08 10:00:00', '2026-08-30 10:00:00'),
    (5,  2,  5, 'FAC-B02-0005', 'EXTINGUISHER','MFZ/ABC4',     '地下一层配电间门口',       '2022-03-11', 30,  '2026-09-18', '2026-10-18', 'NORMAL',   170, 240, '2026-01-08 10:00:00', '2026-09-18 10:00:00'),
    (6,  2,  5, 'FAC-B02-0006', 'LIGHT',      'ZY8811',        '地下一层疏散通道',         '2021-06-18', 90,  '2026-06-30', '2026-09-28', 'NORMAL',   520, 340, '2026-01-08 10:00:00', '2026-06-30 10:00:00'),
    -- ===== B02 · 一层 =====
    (7,  2,  6, 'FAC-B02-0007', 'HYDRANT',    'SN65',          '一层中庭东侧消火栓箱',     '2021-06-18', 90,  '2026-09-10', '2026-12-09', 'NORMAL',   120, 340, '2026-01-08 10:00:00', '2026-09-10 10:00:00'),
    (8,  2,  6, 'FAC-B02-0008', 'EXTINGUISHER','MFZ/ABC4',     '一层总服务台后侧',         '2022-03-11', 30,  '2026-09-20', '2026-10-20', 'NORMAL',   210, 110, '2026-01-08 10:00:00', '2026-09-20 10:00:00'),
    (9,  2,  6, 'FAC-B02-0009', 'ALARM',      'J-SAP-M-03',    '一层主出入口旁',           '2021-06-18', 90,  '2026-08-15', '2026-11-13', 'NORMAL',    95, 320, '2026-01-08 10:00:00', '2026-08-15 10:00:00'),
    (10, 2,  6, 'FAC-B02-0010', 'SMOKE',      'JTY-GD-3002B',  '一层西侧商铺顶部',         '2021-06-18', 90,  '2026-05-12', '2026-08-10', 'FAULT',    300, 110, '2026-01-08 10:00:00', '2026-05-12 10:00:00'),
    (11, 2,  6, 'FAC-B02-0011', 'DOOR',       'FM甲-1021',     '一层楼梯间前室防火门',     '2021-06-18', 90,  '2026-09-01', '2026-11-30', 'NORMAL',   760, 320, '2026-01-08 10:00:00', '2026-09-01 10:00:00'),
    -- ===== B02 · 二层 =====
    (12, 2,  7, 'FAC-B02-0012', 'HYDRANT',    'SN65',          '二层东侧消火栓箱',         '2021-06-18', 90,  '2026-09-09', '2026-12-08', 'NORMAL',   120, 340, '2026-01-08 10:00:00', '2026-09-09 10:00:00'),
    (13, 2,  7, 'FAC-B02-0013', 'EXTINGUISHER','MFZ/ABC5',     '二层服装区北柱位',         '2022-05-20', 30,  '2026-09-19', '2026-10-19', 'NORMAL',   400, 110, '2026-01-08 10:00:00', '2026-09-19 10:00:00'),
    (14, 2,  7, 'FAC-B02-0014', 'SPRINKLER',  'T-ZSTX15-68℃', '二层吊顶中央喷淋头',       '2021-06-18', 180, '2026-04-10', '2026-10-07', 'NORMAL',   560, 340, '2026-01-08 10:00:00', '2026-04-10 10:00:00'),
    (15, 2,  7, 'FAC-B02-0015', 'SMOKE',      'JTY-GD-3002B',  '二层餐饮区顶部',           '2021-06-18', 90,  '2026-08-20', '2026-11-18', 'NORMAL',   640, 110, '2026-01-08 10:00:00', '2026-08-20 10:00:00'),
    (16, 2,  7, 'FAC-B02-0016', 'LIGHT',      'ZY8811',        '二层疏散指示灯',           '2021-06-18', 90,  '2026-07-01', '2026-09-29', 'NORMAL',   880, 320, '2026-01-08 10:00:00', '2026-07-01 10:00:00'),
    -- ===== B02 · 三层 =====
    (17, 2,  8, 'FAC-B02-0017', 'HYDRANT',    'SN65',          '三层西侧消火栓箱',         '2021-06-18', 90,  '2026-09-08', '2026-12-07', 'NORMAL',   860, 340, '2026-01-08 10:00:00', '2026-09-08 10:00:00'),
    (18, 2,  8, 'FAC-B02-0018', 'EXTINGUISHER','MFZ/ABC4',     '三层影院售票厅门口',       '2022-05-20', 30,  '2026-08-24', '2026-09-23', 'REPLACE',  300, 240, '2026-01-08 10:00:00', '2026-08-24 10:00:00'),
    (19, 2,  8, 'FAC-B02-0019', 'SPRINKLER',  'T-ZSTX15-68℃', '三层走廊吊顶喷淋头',       '2021-06-18', 180, '2026-03-30', '2026-09-26', 'NORMAL',   400, 340, '2026-01-08 10:00:00', '2026-03-30 10:00:00'),
    (20, 2,  8, 'FAC-B02-0020', 'ALARM',      'J-SAP-M-03',    '三层安全出口旁',           '2021-06-18', 90,  '2026-08-18', '2026-11-16', 'NORMAL',   930, 320, '2026-01-08 10:00:00', '2026-08-18 10:00:00'),
    (21, 2,  8, 'FAC-B02-0021', 'DOOR',       'FM乙-1521',     '三层防火分区隔断门',       '2021-06-18', 90,  '2026-05-05', '2026-08-03', 'FAULT',    520, 300, '2026-01-08 10:00:00', '2026-05-05 10:00:00'),
    -- ===== B02 · 四层 =====
    (22, 2,  9, 'FAC-B02-0022', 'HYDRANT',    'SN65',          '四层环廊北侧消火栓箱',     '2021-06-18', 90,  '2026-09-12', '2026-12-11', 'NORMAL',   500, 340, '2026-01-08 10:00:00', '2026-09-12 10:00:00'),
    (23, 2,  9, 'FAC-B02-0023', 'SMOKE',      'JTY-GD-3002B',  '四层办公区顶部',           '2021-06-18', 90,  '2026-09-02', '2026-12-01', 'NORMAL',   160, 110, '2026-01-08 10:00:00', '2026-09-02 10:00:00'),
    (24, 2,  9, 'FAC-B02-0024', 'LIGHT',      'ZY8811',        '四层疏散楼梯口应急照明',   '2021-06-18', 90,  '2026-06-20', '2026-09-18', 'REPLACE',  160, 470, '2026-01-08 10:00:00', '2026-06-20 10:00:00'),
    (25, 2,  9, 'FAC-B02-0025', 'EXTINGUISHER','MFZ/ABC3',     '四层弱电井旁',             '2023-02-08', 30,  '2026-09-15', '2026-10-15', 'NORMAL',   640, 240, '2026-01-08 10:00:00', '2026-09-15 10:00:00'),
    -- ===== B02 · 五层 =====
    (26, 2, 10, 'FAC-B02-0026', 'HYDRANT',    'SN65',          '五层餐饮区东侧消火栓箱',   '2021-06-18', 90,  '2026-09-11', '2026-12-10', 'NORMAL',   240, 340, '2026-01-08 10:00:00', '2026-09-11 10:00:00'),
    (27, 2, 10, 'FAC-B02-0027', 'SPRINKLER',  'T-ZSTX15-93℃', '五层厨房区域灶台上方',     '2021-06-18', 180, '2026-04-05', '2026-10-02', 'NORMAL',   660, 340, '2026-01-08 10:00:00', '2026-04-05 10:00:00'),
    (28, 2, 10, 'FAC-B02-0028', 'EXTINGUISHER','MFZ/ABC4',     '五层影院放映厅门口',       '2022-05-20', 30,  '2026-09-16', '2026-10-16', 'NORMAL',   880, 110, '2026-01-08 10:00:00', '2026-09-16 10:00:00'),
    -- ===== B02 · 六层 =====
    (29, 2, 11, 'FAC-B02-0029', 'HYDRANT',    'SN65',          '六层屋面设备层消火栓箱',   '2021-06-18', 90,  '2026-09-13', '2026-12-12', 'NORMAL',   400, 340, '2026-01-08 10:00:00', '2026-09-13 10:00:00'),
    (30, 2, 11, 'FAC-B02-0030', 'SMOKE',      'JTY-GD-3002B',  '六层影音室顶部',           '2021-06-18', 90,  '2026-08-05', '2026-11-03', 'NORMAL',   640, 470, '2026-01-08 10:00:00', '2026-08-05 10:00:00'),
    (31, 2, 11, 'FAC-B02-0031', 'DOOR',       'FM甲-0921',     '六层设备平台防火门(停用)', '2021-06-18', 90,  '2025-11-20', '2026-02-18', 'DISABLED', 280, 300, '2026-01-08 10:00:00', '2025-11-20 10:00:00'),
    -- ===== 其它在管建筑 =====
    (32, 1,  1, 'FAC-B01-0001', 'EXTINGUISHER','MFZ/ABC4',     '住宅塔楼一层大堂',         '2023-06-13', 30,  '2026-09-18', '2026-10-18', 'NORMAL',   200, 340, '2026-01-08 10:00:00', '2026-09-18 10:00:00'),
    (33, 1,  2, 'FAC-B01-0002', 'HYDRANT',    'SN65',          '住宅二层电梯厅消火栓箱',   '2021-09-16', 90,  '2026-07-11', '2026-10-09', 'NORMAL',   400, 340, '2026-01-08 10:00:00', '2026-07-11 10:00:00'),
    (34, 3, 12, 'FAC-B03-0001', 'HYDRANT',    'SN65',          '厂房一层柱位消火栓箱',     '2022-04-22', 90,  '2026-08-25', '2026-11-23', 'NORMAL',   240, 340, '2026-01-08 10:00:00', '2026-08-25 10:00:00'),
    (35, 3, 13, 'FAC-B03-0002', 'SPRINKLER',  'T-ZSTX15-68℃', '厂房二层吊顶喷淋头',       '2022-04-22', 180, '2026-04-01', '2026-09-28', 'NORMAL',   560, 340, '2026-01-08 10:00:00', '2026-04-01 10:00:00'),
    (36, 4, 14, 'FAC-B04-0001', 'PUMP',       'XBD6.0/30',     '办公楼地下一层消防泵房',   '2021-11-05', 30,  '2026-09-20', '2026-10-20', 'NORMAL',   860, 540, '2026-01-08 10:00:00', '2026-09-20 10:00:00'),
    (37, 5, 17, 'FAC-B05-0001', 'HYDRANT',    'SN65',          '库区主通道消火栓箱',       '2022-10-19', 90,  '2026-09-06', '2026-12-05', 'NORMAL',   400, 340, '2026-01-08 10:00:00', '2026-09-06 10:00:00'),
    (38, 5, 18, 'FAC-B05-0002', 'EXTINGUISHER','MFZ/ABC8',     '库区二层东侧疏散口',       '2022-10-19', 30,  '2026-09-21', '2026-10-21', 'NORMAL',   880, 110, '2026-01-08 10:00:00', '2026-09-21 10:00:00');

-- ----------------------------------------------------------------------------
-- 模块三：巡检记录 + 明细行
-- ----------------------------------------------------------------------------
CREATE TABLE inspection (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    inspect_no     VARCHAR(24) NOT NULL COMMENT '巡检单号 INS-年-四位序号',
    building_id    BIGINT      NOT NULL COMMENT '被检建筑',
    inspector      VARCHAR(32) NOT NULL COMMENT '巡检人',
    inspect_date   DATE        NOT NULL COMMENT '巡检日期',
    inspect_type   VARCHAR(16) NOT NULL COMMENT 'DAILY 日巡 / MONTHLY 月检 / QUARTERLY 季检 / YEARLY 年检',
    expected_count INT         NOT NULL DEFAULT 0 COMMENT '应检点位数',
    actual_count   INT         NOT NULL DEFAULT 0 COMMENT '实检数',
    abnormal_count INT         NOT NULL DEFAULT 0 COMMENT '异常数',
    status         VARCHAR(16) NOT NULL COMMENT 'PROCESSING 进行中 / COMPLETED 已完成',
    remark         VARCHAR(128)         COMMENT '备注',
    created_at     DATETIME             COMMENT '创建时间',
    updated_at     DATETIME             COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_inspect_no (inspect_no),
    KEY idx_inspect_building (building_id, inspect_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '巡检记录';

INSERT INTO inspection (id, inspect_no, building_id, inspector, inspect_date, inspect_type, expected_count, actual_count, abnormal_count, status, remark, created_at, updated_at) VALUES
    (1,  'INS-2026-0001', 2, '王海涛', '2026-09-22', 'MONTHLY',   5,  5,  0, 'COMPLETED',  '一层月度巡检，全部正常',          '2026-09-22 09:00:00', '2026-09-22 11:20:00'),
    (2,  'INS-2026-0002', 2, '王海涛', '2026-09-15', 'DAILY',     5,  5,  1, 'COMPLETED',  '二层日巡发现喷淋支管压力表偏低',  '2026-09-15 08:40:00', '2026-09-15 10:10:00'),
    (3,  'INS-2026-0003', 2, '李振声', '2026-09-10', 'QUARTERLY', 5,  5,  2, 'COMPLETED',  '三层季检：防火门与灭火器异常',    '2026-09-10 09:05:00', '2026-09-10 12:00:00'),
    (4,  'INS-2026-0004', 2, '陈其琛', '2026-09-05', 'MONTHLY',   4,  4,  0, 'COMPLETED',  '四层月度巡检无异常',              '2026-09-05 09:00:00', '2026-09-05 10:40:00'),
    (5,  'INS-2026-0005', 2, '李振声', '2026-08-28', 'MONTHLY',   3,  3,  1, 'COMPLETED',  '地下一层配电间灭火器压力偏低',    '2026-08-28 09:10:00', '2026-08-28 11:00:00'),
    (6,  'INS-2026-0006', 2, '王海涛', '2026-08-20', 'DAILY',     3,  3,  0, 'COMPLETED',  '五层日巡正常',                    '2026-08-20 08:50:00', '2026-08-20 09:40:00'),
    (7,  'INS-2026-0007', 2, '陈其琛', '2026-08-12', 'QUARTERLY', 3,  3,  1, 'COMPLETED',  '地下二层排烟机房烟感信号异常',    '2026-08-12 09:20:00', '2026-08-12 10:50:00'),
    (8,  'INS-2026-0008', 2, '陈其琛', '2026-07-30', 'MONTHLY',   3,  3,  0, 'COMPLETED',  '六层月度巡检正常',                '2026-07-30 09:00:00', '2026-07-30 10:10:00'),
    (9,  'INS-2026-0009', 2, '李振声', '2026-06-12', 'YEARLY',    8,  8,  2, 'COMPLETED',  '年度检测：泵组切换 + 末端试水',   '2026-06-12 08:30:00', '2026-06-12 17:20:00'),
    (10, 'INS-2026-0010', 1, '韩磊',   '2026-09-20', 'MONTHLY',   2,  2,  0, 'COMPLETED',  '住宅塔楼月度巡检',                '2026-09-20 09:00:00', '2026-09-20 10:00:00'),
    (11, 'INS-2026-0011', 2, '王海涛', '2026-09-23', 'DAILY',     5,  4,  1, 'PROCESSING', '一层日巡进行中，还差一个点位',    '2026-09-23 08:45:00', '2026-09-23 15:30:00'),
    (12, 'INS-2026-0012', 3, '吴迪',   '2026-09-21', 'MONTHLY',   2,  0,  0, 'PROCESSING', '厂房巡检准备中，尚未开始打点',    '2026-09-21 09:00:00', '2026-09-21 09:10:00');

CREATE TABLE inspection_item (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    inspection_id BIGINT      NOT NULL COMMENT '所属巡检单',
    point_id      BIGINT      NOT NULL COMMENT '被检点位',
    result        VARCHAR(16) NOT NULL COMMENT 'PENDING 未检 / NORMAL 正常 / ABNORMAL 异常',
    problem_desc  VARCHAR(255)         COMMENT '异常描述',
    created_at    DATETIME             COMMENT '创建时间',
    updated_at    DATETIME             COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_item_inspection (inspection_id),
    KEY idx_item_point (point_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '巡检明细行';

-- INS-2026-0001 一层月检（5 行全正常）
INSERT INTO inspection_item (inspection_id, point_id, result, problem_desc, created_at, updated_at) VALUES
    (1, 7,  'NORMAL', NULL, '2026-09-22 09:05:00', '2026-09-22 09:05:00'),
    (1, 8,  'NORMAL', NULL, '2026-09-22 09:05:00', '2026-09-22 09:12:00'),
    (1, 9,  'NORMAL', NULL, '2026-09-22 09:05:00', '2026-09-22 09:18:00'),
    (1, 10, 'NORMAL', NULL, '2026-09-22 09:05:00', '2026-09-22 09:34:00'),
    (1, 11, 'NORMAL', NULL, '2026-09-22 09:05:00', '2026-09-22 09:41:00'),
-- INS-2026-0002 二层日巡（1 异常）
    (2, 12, 'NORMAL', NULL, '2026-09-15 08:45:00', '2026-09-15 08:52:00'),
    (2, 13, 'NORMAL', NULL, '2026-09-15 08:45:00', '2026-09-15 09:00:00'),
    (2, 14, 'ABNORMAL', '喷淋支管压力表显示 0.32MPa，低于设计值', '2026-09-15 08:45:00', '2026-09-15 09:26:00'),
    (2, 15, 'NORMAL', NULL, '2026-09-15 08:45:00', '2026-09-15 09:33:00'),
    (2, 16, 'NORMAL', NULL, '2026-09-15 08:45:00', '2026-09-15 09:40:00'),
-- INS-2026-0003 三层季检（2 异常）
    (3, 17, 'NORMAL', NULL, '2026-09-10 09:10:00', '2026-09-10 09:20:00'),
    (3, 18, 'ABNORMAL', '灭火器压力表指针落在红区，且上月未按时月检', '2026-09-10 09:10:00', '2026-09-10 09:52:00'),
    (3, 19, 'NORMAL', NULL, '2026-09-10 09:10:00', '2026-09-10 10:05:00'),
    (3, 20, 'NORMAL', NULL, '2026-09-10 09:10:00', '2026-09-10 10:14:00'),
    (3, 21, 'ABNORMAL', '防火门闭门器失效，无法自动闭合', '2026-09-10 09:10:00', '2026-09-10 11:40:00'),
-- INS-2026-0004 四层月检（4 行全正常）
    (4, 22, 'NORMAL', NULL, '2026-09-05 09:05:00', '2026-09-05 09:15:00'),
    (4, 23, 'NORMAL', NULL, '2026-09-05 09:05:00', '2026-09-05 09:24:00'),
    (4, 24, 'NORMAL', NULL, '2026-09-05 09:05:00', '2026-09-05 09:38:00'),
    (4, 25, 'NORMAL', NULL, '2026-09-05 09:05:00', '2026-09-05 09:47:00'),
-- INS-2026-0005 地下一层月检（1 异常）
    (5, 4,  'NORMAL', NULL, '2026-08-28 09:15:00', '2026-08-28 09:25:00'),
    (5, 5,  'ABNORMAL', '干粉灭火器结块，压力偏低', '2026-08-28 09:15:00', '2026-08-28 10:12:00'),
    (5, 6,  'NORMAL', NULL, '2026-08-28 09:15:00', '2026-08-28 10:20:00'),
-- INS-2026-0006 五层日巡（3 行全正常）
    (6, 26, 'NORMAL', NULL, '2026-08-20 08:55:00', '2026-08-20 09:05:00'),
    (6, 27, 'NORMAL', NULL, '2026-08-20 08:55:00', '2026-08-20 09:14:00'),
    (6, 28, 'NORMAL', NULL, '2026-08-20 08:55:00', '2026-08-20 09:26:00'),
-- INS-2026-0007 地下二层季检（1 异常）
    (7, 1,  'NORMAL', NULL, '2026-08-12 09:25:00', '2026-08-12 09:40:00'),
    (7, 2,  'NORMAL', NULL, '2026-08-12 09:25:00', '2026-08-12 09:58:00'),
    (7, 3,  'ABNORMAL', '烟感探测器超期未检，误报两次', '2026-08-12 09:25:00', '2026-08-12 10:35:00'),
-- INS-2026-0008 六层月检（3 行全正常）
    (8, 29, 'NORMAL', NULL, '2026-07-30 09:05:00', '2026-07-30 09:20:00'),
    (8, 30, 'NORMAL', NULL, '2026-07-30 09:05:00', '2026-07-30 09:34:00'),
    (8, 31, 'NORMAL', NULL, '2026-07-30 09:05:00', '2026-07-30 09:46:00'),
-- INS-2026-0009 年度检测（8 行，2 异常）
    (9, 1,  'NORMAL', NULL, '2026-06-12 08:35:00', '2026-06-12 09:10:00'),
    (9, 2,  'NORMAL', NULL, '2026-06-12 08:35:00', '2026-06-12 09:40:00'),
    (9, 7,  'NORMAL', NULL, '2026-06-12 08:35:00', '2026-06-12 10:20:00'),
    (9, 12, 'NORMAL', NULL, '2026-06-12 08:35:00', '2026-06-12 11:05:00'),
    (9, 14, 'ABNORMAL', '末端试水装置压力下降到 0.18MPa', '2026-06-12 08:35:00', '2026-06-12 14:20:00'),
    (9, 17, 'NORMAL', NULL, '2026-06-12 08:35:00', '2026-06-12 15:00:00'),
    (9, 22, 'NORMAL', NULL, '2026-06-12 08:35:00', '2026-06-12 15:40:00'),
    (9, 29, 'ABNORMAL', '屋面消防泵切换试验时稳压泵启动延迟 6 秒', '2026-06-12 08:35:00', '2026-06-12 16:50:00'),
-- INS-2026-0010 住宅月检（2 行全正常）
    (10, 32, 'NORMAL', NULL, '2026-09-20 09:05:00', '2026-09-20 09:20:00'),
    (10, 33, 'NORMAL', NULL, '2026-09-20 09:05:00', '2026-09-20 09:38:00'),
-- INS-2026-0011 一层日巡（进行中：1 未检 + 1 异常）
    (11, 7,  'NORMAL', NULL, '2026-09-23 08:50:00', '2026-09-23 08:58:00'),
    (11, 8,  'NORMAL', NULL, '2026-09-23 08:50:00', '2026-09-23 09:06:00'),
    (11, 9,  'ABNORMAL', '手动报警按钮玻璃面板有裂纹', '2026-09-23 08:50:00', '2026-09-23 09:44:00'),
    (11, 10, 'NORMAL', NULL, '2026-09-23 08:50:00', '2026-09-23 10:02:00'),
    (11, 11, 'PENDING', NULL, '2026-09-23 08:50:00', '2026-09-23 08:50:00'),
-- INS-2026-0012 厂房月检（进行中：全未检）
    (12, 34, 'PENDING', NULL, '2026-09-21 09:05:00', '2026-09-21 09:05:00'),
    (12, 35, 'PENDING', NULL, '2026-09-21 09:05:00', '2026-09-21 09:05:00');

-- ----------------------------------------------------------------------------
-- 模块四：维保单
-- ----------------------------------------------------------------------------
CREATE TABLE maintenance_order (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    order_no     VARCHAR(24)   NOT NULL COMMENT '维保单号 MO-年-四位序号',
    order_type   VARCHAR(16)   NOT NULL COMMENT 'REPAIR 维修 / REPLACE 更换 / REFILL 充装 / TEST 检测',
    point_id     BIGINT                 COMMENT '关联消防设施点位',
    ext_id       BIGINT                 COMMENT '关联灭火器气瓶',
    fault_desc   VARCHAR(255)            COMMENT '故障描述',
    solution     VARCHAR(255)            COMMENT '处理措施（完工必填）',
    material_fee DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '材料费',
    labor_fee    DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '人工费',
    total_fee    DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '合计（材料费 + 人工费）',
    vendor       VARCHAR(64)             COMMENT '服务商',
    owner_person VARCHAR(32)             COMMENT '责任人',
    finish_date  DATE                    COMMENT '完工日期',
    status       VARCHAR(16)   NOT NULL COMMENT 'PENDING 待派工 / ASSIGNED 已派工 / WORKING 处理中 / DONE 已完成 / ACCEPTED 已验收',
    created_at   DATETIME                COMMENT '创建时间',
    updated_at   DATETIME                COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_order_status (status),
    KEY idx_order_point (point_id),
    KEY idx_order_ext (ext_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '维保单';

INSERT INTO maintenance_order (id, order_no, order_type, point_id, ext_id, fault_desc, solution, material_fee, labor_fee, total_fee, vendor, owner_person, finish_date, status, created_at, updated_at) VALUES
    (1,  'MO-2026-0001', 'REPAIR',  10, NULL, '烟感探测器误报警两次，初步判断积灰',   '拆洗后复位，联动回授测试通过',  0.00, 120.00, 120.00, '泰盛消防维保', '周大勇', '2026-07-14', 'ACCEPTED', '2026-07-08 10:00:00', '2026-07-16 09:30:00'),
    (2,  'MO-2026-0002', 'REPLACE', 21, NULL, '防火门闭门器失效，无法自动闭合',       '更换闭门器并做 20 次启闭试验', 260.00, 180.00, 440.00, '泰盛消防维保', '周大勇', '2026-09-16', 'ACCEPTED', '2026-09-10 11:45:00', '2026-09-18 14:00:00'),
    (3,  'MO-2026-0003', 'REPAIR',  3,  NULL, '排烟机房烟感超期未检，信号时断时续',   '更换同型号探测器并重新编码',    320.00, 150.00, 470.00, '泰盛消防维保', '周大勇', '2026-09-12', 'DONE',      '2026-08-12 11:00:00', '2026-09-12 16:20:00'),
    (4,  'MO-2026-0004', 'REFILL',  NULL, 2,  '干粉灭火器水压试验到期，需返厂检测',   NULL,                            80.00,  60.00, 140.00, '安源气瓶检测站', '钱多加', NULL,          'WORKING',   '2026-09-05 09:30:00', '2026-09-10 10:15:00'),
    (5,  'MO-2026-0005', 'REFILL',  NULL, 6,  '餐饮区二氧化碳灭火器到抽检期',         NULL,                            60.00,  40.00, 100.00, '安源气瓶检测站', '钱多加', NULL,          'ASSIGNED',  '2026-09-12 14:20:00', '2026-09-12 14:35:00'),
    (6,  'MO-2026-0006', 'REPLACE', 24, NULL, '应急照明灯电池容量衰减，放电不足 20 分钟', NULL,                        180.00, 90.00,  270.00, NULL, NULL, NULL,                            'PENDING',   '2026-09-18 09:20:00', '2026-09-18 09:20:00'),
    (7,  'MO-2026-0007', 'REPAIR',  14, NULL, '喷淋支管压力表读数偏低',               '更换压力表并复压观测 24 小时',  95.00, 200.00, 295.00, '泰盛消防维保', '孔维民', '2026-09-02', 'DONE',      '2026-08-28 10:30:00', '2026-09-02 15:40:00'),
    (8,  'MO-2026-0008', 'REPLACE', NULL, 7,  '泡沫灭火器筒体锈蚀，宜报废处理',       NULL,                            0.00,   0.00,   0.00, '长宁消防器材', '严实', NULL,          'WORKING',   '2026-09-08 16:00:00', '2026-09-15 09:00:00'),
    (9,  'MO-2026-0009', 'TEST',    1,  NULL, '消防泵组年度性能测试',                 NULL,                            0.00, 300.00, 300.00, '泰盛消防维保', '孔维民', NULL,          'ASSIGNED',  '2026-09-14 11:10:00', '2026-09-14 11:25:00'),
    (10, 'MO-2026-0010', 'REPLACE', 18, NULL, '三层灭火器超期未月检且压力不足',       NULL,                            120.00, 60.00,  180.00, NULL, NULL, NULL,                            'PENDING',   '2026-09-23 15:30:00', '2026-09-23 15:30:00'),
    (11, 'MO-2026-0011', 'REFILL',  NULL, 4,  '水基灭火器待充装',                     NULL,                            45.00,  35.00,  80.00, NULL, NULL, NULL,                            'PENDING',   '2026-09-23 15:35:00', '2026-09-23 15:35:00'),
    (12, 'MO-2026-0012', 'REPAIR',  35, NULL, '厂房喷淋头有渗漏痕迹',                 '更换喷淋头并放水试验合格',      130.00, 160.00, 290.00, '安源气瓶检测站', '钱多加', '2026-08-18', 'ACCEPTED', '2026-08-14 13:20:00', '2026-08-20 10:10:00'),
    (13, 'MO-2026-0013', 'TEST',    33, NULL, '住宅消火栓栓口水压检测',               NULL,                            0.00, 220.00, 220.00, '宏源消防', '傅强', NULL,          'WORKING',   '2026-09-19 10:00:00', '2026-09-22 09:15:00'),
    (14, 'MO-2026-0014', 'REPAIR',  9,  NULL, '手动报警按钮玻璃面板裂纹',             '更换玻璃盖板并做报警回授测试',  45.00,  60.00, 105.00, '泰盛消防维保', '周大勇', '2026-09-19', 'DONE',      '2026-09-23 09:50:00', '2026-09-23 16:30:00'),
    (15, 'MO-2026-0015', 'REPAIR', 17,  NULL, '三层西侧消火栓栓口渗漏（停用许可 DP-2026-0003 期间持续跟踪）', NULL, 0.00, 0.00, 0.00, '泰盛消防维保', '周大勇', NULL, 'WORKING', '2026-09-26 10:00:00', '2026-09-27 09:40:00');

-- ----------------------------------------------------------------------------
-- 模块五：灭火器与气瓶台账
-- 列名同样来自 @AttributeOverride：ext_no / store_place / patrol_cycle_days / last_patrol_on / next_patrol_on
-- ----------------------------------------------------------------------------
CREATE TABLE extinguisher (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    building_id       BIGINT       NOT NULL COMMENT '所属建筑',
    floor_id          BIGINT                COMMENT '存放楼层',
    ext_no            VARCHAR(32)  NOT NULL COMMENT '台账编码 EXT-<建筑编号>-<四位序号>（@PrePersist 生成）',
    steel_no          VARCHAR(32)  NOT NULL COMMENT '钢印编号（唯一）',
    cyl_type          VARCHAR(16)  NOT NULL COMMENT 'POWDER 干粉 / CO2 二氧化碳 / WATER 水基 / FOAM 泡沫',
    charge_kg         DECIMAL(6,2) NOT NULL COMMENT '充装量（kg / L）',
    produce_date      DATE                  COMMENT '生产日期',
    last_fill_date    DATE                  COMMENT '上次充装日',
    hydro_test_due    DATE                  COMMENT '水压试验到期日',
    scrap_date        DATE                  COMMENT '报废日期',
    point_id          BIGINT                COMMENT '存放点位',
    store_place       VARCHAR(128)          COMMENT '存放位置描述',
    installed_on      DATE                  COMMENT '投用日期',
    patrol_cycle_days INT                   COMMENT '月检周期（天），默认 30',
    last_patrol_on    DATE                  COMMENT '上次月检日',
    next_patrol_on    DATE                  COMMENT '下次月检日（@PrePersist / @PreUpdate 推算）',
    status            VARCHAR(16)  NOT NULL COMMENT 'NORMAL 正常 / REFILL 待充装 / HYDRO 待水压 / SCRAP 报废',
    active_disable_permit_id BIGINT COMMENT '叠加标记：正被哪张停用许可挂起（基类共有列，气瓶场景预留）',
    plan_x            INT                   COMMENT '平面图横坐标 0~1000',
    plan_y            INT                   COMMENT '平面图纵坐标 0~660',
    created_at        DATETIME              COMMENT '创建时间',
    updated_at        DATETIME              COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ext_no (ext_no),
    UNIQUE KEY uk_ext_steel (steel_no),
    KEY idx_ext_building (building_id),
    KEY idx_ext_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '灭火器与气瓶台账';

INSERT INTO extinguisher (id, building_id, floor_id, ext_no, steel_no, cyl_type, charge_kg, produce_date, last_fill_date, hydro_test_due, scrap_date, point_id, store_place, installed_on, patrol_cycle_days, last_patrol_on, next_patrol_on, status, plan_x, plan_y, created_at, updated_at) VALUES
    (1,  2,  5, 'EXT-B02-0001', 'GP-2022-0431', 'POWDER', 4.00, '2022-03-11', '2025-11-20', '2027-03-11', '2032-03-11', 5,   '地下一层配电间门口',   '2022-03-11', 30, '2026-09-18', '2026-10-18', 'NORMAL', 200, 270, '2026-01-08 11:00:00', '2026-09-18 11:00:00'),
    (2,  2,  6, 'EXT-B02-0002', 'GP-2021-0512', 'POWDER', 5.00, '2021-05-20', '2023-05-20', '2026-05-20', '2031-05-20', 8,   '一层总服务台后侧',     '2021-05-20', 30, '2026-09-20', '2026-10-20', 'HYDRO',  240, 140, '2026-01-08 11:00:00', '2026-09-20 11:00:00'),
    (3,  2,  7, 'EXT-B02-0003', 'GP-2023-0178', 'CO2',    3.00, '2023-02-08', '2025-02-08', '2028-02-08', '2033-02-08', 13,  '二层服装区北柱位',     '2023-02-08', 30, '2026-09-19', '2026-10-19', 'NORMAL', 440, 140, '2026-01-08 11:00:00', '2026-09-19 11:00:00'),
    (4,  2,  8, 'EXT-B02-0004', 'GP-2022-0906', 'POWDER', 4.00, '2022-09-06', '2024-09-06', '2027-09-06', '2032-09-06', 18,  '三层影院售票厅门口',   '2022-09-06', 30, '2026-08-24', '2026-09-23', 'REFILL', 330, 260, '2026-01-08 11:00:00', '2026-08-24 11:00:00'),
    (5,  2,  9, 'EXT-B02-0005', 'GP-2024-0412', 'WATER',  3.00, '2024-04-12', '2026-04-12', '2027-04-12', '2030-04-12', 25,  '四层弱电井旁',         '2024-04-12', 30, '2026-09-15', '2026-10-15', 'NORMAL', 660, 260, '2026-01-08 11:00:00', '2026-09-15 11:00:00'),
    (6,  2, 10, 'EXT-B02-0006', 'GP-2022-0725', 'POWDER', 4.00, '2022-07-25', '2024-07-25', '2026-11-15', '2032-07-25', 28,  '五层影院放映厅门口',   '2022-07-25', 30, '2026-09-16', '2026-10-16', 'NORMAL', 880, 140, '2026-01-08 11:00:00', '2026-09-16 11:00:00'),
    (7,  2,  4, 'EXT-B02-0007', 'GP-2021-0314', 'FOAM',   6.00, '2021-03-14', '2023-03-14', '2024-03-14', '2027-03-14', NULL,'地下二层备用库',       '2021-03-14', 30, '2026-08-30', '2026-09-29', 'HYDRO',  160, 520, '2026-01-08 11:00:00', '2026-08-30 11:00:00'),
    (8,  2,  4, 'EXT-B02-0008', 'GP-2019-1108', 'POWDER', 4.00, '2019-11-08', '2021-11-08', '2024-11-08', '2026-11-08', NULL,'地下二层报废暂存区',   '2019-11-08', 30, '2026-06-10', '2026-07-10', 'SCRAP',  700, 520, '2026-01-08 11:00:00', '2026-06-10 11:00:00'),
    (9,  2,  5, 'EXT-B02-0009', 'GP-2024-0106', 'WATER',  6.00, '2024-01-06', '2026-01-06', '2027-01-06', '2030-01-06', 5,   '地下一层配电间备用瓶', '2024-01-06', 30, '2026-09-18', '2026-10-18', 'NORMAL', 260, 300, '2026-01-08 11:00:00', '2026-09-18 11:00:00'),
    (10, 1,  1, 'EXT-B01-0001', 'GP-2023-0613', 'POWDER', 4.00, '2023-06-13', '2025-06-13', '2028-06-13', '2033-06-13', 32,  '住宅塔楼一层大堂',     '2023-06-13', 30, '2026-09-18', '2026-10-18', 'NORMAL', 160, 140, '2026-01-08 11:00:00', '2026-09-18 11:00:00'),
    (11, 3, 12, 'EXT-B03-0001', 'GP-2024-0809', 'FOAM',   6.00, '2024-08-09', '2026-08-09', '2027-08-09', '2030-08-09', NULL,'厂房一层柱位旁',       '2024-08-09', 30, '2026-09-21', '2026-10-21', 'NORMAL', 300, 200, '2026-01-08 11:00:00', '2026-09-21 11:00:00'),
    (12, 4, 15, 'EXT-B04-0001', 'GP-2023-0301', 'CO2',    5.00, '2023-03-01', '2025-03-01', '2028-03-01', '2033-03-01', NULL,'办公楼一层门厅',       '2023-03-01', 30, '2026-09-20', '2026-10-20', 'NORMAL', 640, 140, '2026-01-08 11:00:00', '2026-09-20 11:00:00'),
    (13, 5, 17, 'EXT-B05-0001', 'GP-2022-1219', 'POWDER', 8.00, '2022-12-19', '2024-12-19', '2027-12-19', '2032-12-19', 38,  '库区二层东侧疏散口',   '2022-12-19', 30, '2026-09-21', '2026-10-21', 'NORMAL', 880, 140, '2026-01-08 11:00:00', '2026-09-21 11:00:00'),
    (14, 5, 18, 'EXT-B05-0002', 'GP-2023-0515', 'POWDER', 8.00, '2023-05-15', '2025-05-15', '2028-05-15', '2033-05-15', NULL,'库区二层东侧疏散口',   '2023-05-15', 30, '2026-09-21', '2026-10-21', 'NORMAL', 920, 140, '2026-01-08 11:00:00', '2026-09-21 11:00:00');

-- ============================================================================
-- 模块六：设施停用许可与复位闭环
--   sys_user                 三类操作角色（消防主管 / 安全负责人 / 现场负责人）
--   disable_permit           许可主表（@Version 乐观锁，scope_locked 范围锁）
--   disable_permit_point     受影响点位 + 生效时原状态快照（许可停用不覆盖原状态）
--   disable_measure          逐项补偿措施（替代灭火器/加密巡查/临时警戒…）
--   reset_verification       逐点位、逐轮复位核验记录
--   permit_order_waiver      关联未完成维保单的处理说明（复位前置条件）
--   permit_action_log        每次关键动作留痕（操作者/时间/前后状态/说明）
--
--   facility_point.active_disable_permit_id：叠加式「许可停用」标记，
--   批准时批量挂、关闭时在同一事务里批量解，原 status 始终保留。
--
-- 演示数据覆盖全状态（基准日 2026-09-29）：
--   DP-2026-0001 生效中（即将到期） 点位 12,13         补偿措施部分已确认
--   DP-2026-0002 生效中（已超期）   点位 22,23         措施齐、有未完成维保单（复位被卡）
--   DP-2026-0003 待复位（已超期+失败过）点位 17         新一轮仍有不合格
--   DP-2026-0004 待复位（全部合格待关闭）点位 16
--   DP-2026-0005 待批准             点位 26,27
--   DP-2026-0006 草稿
--   DP-2026-0007 草稿（被拒绝退回，范围锁定）
--   DP-2026-0008 已撤销             点位 7
--   DP-2026-0009 已关闭（闭环样板）点位 29,30          标记已解除
-- ============================================================================

CREATE TABLE sys_user (
    id           BIGINT       NOT NULL,
    user_name    VARCHAR(32)  NOT NULL COMMENT '登录名',
    display_name VARCHAR(32)  NOT NULL COMMENT '显示姓名（留痕用）',
    role         VARCHAR(24)  NOT NULL COMMENT 'FIRE_MANAGER 消防主管 / SAFETY_OFFICER 安全负责人 / SITE_OWNER 现场负责人',
    phone        VARCHAR(20)           COMMENT '联系电话',
    created_at   DATETIME              COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_name (user_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '系统操作用户';

INSERT INTO sys_user (id, user_name, display_name, role, phone, created_at) VALUES
    (1, 'fire_lead',   '陈默',   'FIRE_MANAGER',   '13907120101', '2026-01-08 09:00:00'),
    (2, 'safety_head', '王海燕', 'SAFETY_OFFICER', '13907120102', '2026-01-08 09:00:00'),
    (3, 'site_zhou',   '周大勇', 'SITE_OWNER',     '13907120111', '2026-01-08 09:00:00'),
    (4, 'site_kong',   '孔维民', 'SITE_OWNER',     '13907120112', '2026-01-08 09:00:00'),
    (5, 'site_wu',     '吴迪',   'SITE_OWNER',     '13907120113', '2026-01-08 09:00:00');

CREATE TABLE disable_permit (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    permit_no             VARCHAR(24)  NOT NULL COMMENT 'DP-年-四位序号',
    building_id           BIGINT       NOT NULL COMMENT '受影响主建筑（点位不跨建筑）',
    reason                VARCHAR(255) NOT NULL COMMENT '停用原因',
    plan_start            DATE         NOT NULL COMMENT '计划开始时间',
    plan_end              DATE         NOT NULL COMMENT '计划结束时间',
    site_owner_id         BIGINT       NOT NULL COMMENT '现场负责人（sys_user）',
    site_owner_name       VARCHAR(32)  NOT NULL COMMENT '现场负责人姓名快照',
    created_by            VARCHAR(32)  NOT NULL COMMENT '创建人（消防主管）',
    status                VARCHAR(24)  NOT NULL COMMENT 'DRAFT/PENDING_APPROVAL/EFFECTIVE/PENDING_RESET/CLOSED/REVOKED',
    scope_locked          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '范围锁定：首次提交后不可改点位清单',
    approved_at           DATETIME              COMMENT '批准生效时间',
    approved_by           VARCHAR(32)           COMMENT '批准人',
    reject_reason         VARCHAR(255)          COMMENT '最近一次拒绝原因',
    closed_at             DATETIME              COMMENT '关闭时间（此刻解除标记）',
    closed_by             VARCHAR(32)           COMMENT '关闭人',
    revoked_at            DATETIME              COMMENT '撤销时间',
    revoked_by            VARCHAR(32)           COMMENT '撤销人',
    revoke_reason         VARCHAR(255)          COMMENT '撤销原因',
    reset_started_at      DATETIME              COMMENT '发起复位核验时间',
    last_verify_at        DATETIME              COMMENT '最近一次复位核验时间',
    last_verify_by        VARCHAR(32)           COMMENT '最近核验人',
    last_verify_fail_reason VARCHAR(255)        COMMENT '最近一轮失败原因汇总（合格时清空）',
    created_at            DATETIME              COMMENT '创建时间',
    updated_at            DATETIME              COMMENT '更新时间',
    version               BIGINT       NOT NULL DEFAULT 0 COMMENT '乐观锁：同一版本只允许一个动作成功',
    PRIMARY KEY (id),
    UNIQUE KEY uk_permit_no (permit_no),
    KEY idx_permit_building (building_id),
    KEY idx_permit_status (status),
    KEY idx_permit_window (building_id, status, plan_start, plan_end)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '设施停用许可';

CREATE TABLE disable_permit_point (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    permit_id     BIGINT      NOT NULL COMMENT '所属许可',
    point_id      BIGINT      NOT NULL COMMENT '受影响点位',
    origin_status VARCHAR(16)           COMMENT '生效时设施原状态快照（正常/故障/待更换/停用）',
    marked        TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '当前是否挂着许可停用标记',
    created_at    DATETIME              COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_permit_point (permit_id, point_id),
    KEY idx_dpp_point (point_id),
    KEY idx_dpp_marked (marked)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '停用许可受影响点位';

CREATE TABLE disable_measure (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    permit_id      BIGINT       NOT NULL COMMENT '所属许可',
    measure_type   VARCHAR(24)  NOT NULL COMMENT 'SPARE_EXT 替代灭火器 / EXTRA_PATROL 加密巡查 / TEMP_BARRIER 临时警戒 / OTHER 其它',
    content        VARCHAR(255) NOT NULL COMMENT '措施内容',
    spec_desc      VARCHAR(128)          COMMENT '数量 / 频次等',
    created_by     VARCHAR(32)  NOT NULL COMMENT '登记人',
    created_at     DATETIME               COMMENT '登记时间',
    confirmed_at   DATETIME               COMMENT '现场负责人确认收口时间（NULL=未完成）',
    confirmed_by   VARCHAR(32)            COMMENT '确认人',
    confirm_remark VARCHAR(255)           COMMENT '确认说明',
    PRIMARY KEY (id),
    KEY idx_measure_permit (permit_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '停用补偿措施';

CREATE TABLE reset_verification (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    batch_no    VARCHAR(24)  NOT NULL COMMENT '核验批次 RV-许可id-轮次',
    permit_id   BIGINT       NOT NULL COMMENT '所属许可',
    point_id    BIGINT       NOT NULL COMMENT '被核验点位',
    result      VARCHAR(8)   NOT NULL COMMENT 'PASS 合格 / FAIL 不合格',
    verify_desc VARCHAR(255)           COMMENT '核验说明',
    fail_reason VARCHAR(255)           COMMENT '失败原因（不合格必填）',
    verified_by VARCHAR(32)  NOT NULL COMMENT '核验人（现场负责人）',
    created_at  DATETIME      NOT NULL COMMENT '核验时间',
    PRIMARY KEY (id),
    KEY idx_reset_permit (permit_id, batch_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '复位核验记录';

CREATE TABLE permit_order_waiver (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    permit_id    BIGINT       NOT NULL COMMENT '所属许可',
    order_id     BIGINT       NOT NULL COMMENT '关联维保单',
    handle_note  VARCHAR(255) NOT NULL COMMENT '未完成维保单的处理说明',
    created_by   VARCHAR(32)  NOT NULL COMMENT '登记人（现场负责人）',
    created_at   DATETIME               COMMENT '登记时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_waiver_permit_order (permit_id, order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '许可关联维保单处理说明';

CREATE TABLE permit_action_log (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    permit_id      BIGINT      NOT NULL COMMENT '所属许可',
    action         VARCHAR(24) NOT NULL COMMENT 'CREATE/UPDATE/SUBMIT/APPROVE/REJECT/REVOKE/MEASURE_DONE/ORDER_WAIVER/RESET_START/RESET_VERIFY/CLOSE',
    from_status    VARCHAR(24)          COMMENT '动作前状态',
    to_status      VARCHAR(24)          COMMENT '动作后状态',
    operator       VARCHAR(32) NOT NULL COMMENT '操作者',
    operator_role  VARCHAR(24) NOT NULL COMMENT '操作者角色',
    remark         VARCHAR(500)         COMMENT '说明',
    created_at     DATETIME    NOT NULL COMMENT '动作时间',
    PRIMARY KEY (id),
    KEY idx_log_permit (permit_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '停用许可动作留痕';

-- ----------------------------------------------------------------------------
-- 许可主表演示数据（version 给非零，模拟已发生过若干次并发读）
-- ----------------------------------------------------------------------------
INSERT INTO disable_permit
    (id, permit_no, building_id, reason, plan_start, plan_end, site_owner_id, site_owner_name, created_by,
     status, scope_locked, approved_at, approved_by, reject_reason, closed_at, closed_by,
     revoked_at, revoked_by, revoke_reason, reset_started_at, last_verify_at, last_verify_by,
     last_verify_fail_reason, created_at, updated_at, version) VALUES
    (1, 'DP-2026-0001', 2, '二层东侧消火栓箱管网改造，需临时停用本层消火栓与邻近灭火器',
     '2026-09-27', '2026-10-02', 3, '周大勇', '陈默',
     'EFFECTIVE', 1, '2026-09-26 15:10:00', '王海燕', NULL, NULL, NULL,
     NULL, NULL, NULL, NULL, NULL, NULL, NULL,
     '2026-09-25 10:00:00', '2026-09-26 15:10:00', 3),
    (2, 'DP-2026-0002', 2, '四层办公区消防线路检修，烟感与消火栓停用',
     '2026-09-20', '2026-09-27', 4, '孔维民', '陈默',
     'EFFECTIVE', 1, '2026-09-19 11:00:00', '王海燕', NULL, NULL, NULL,
     NULL, NULL, NULL, NULL, NULL, NULL, NULL,
     '2026-09-18 09:30:00', '2026-09-19 11:00:00', 2),
    (3, 'DP-2026-0003', 2, '三层西侧消火栓漏水维修',
     '2026-09-21', '2026-09-26', 3, '周大勇', '陈默',
     'PENDING_RESET', 1, '2026-09-20 16:00:00', '王海燕', NULL, NULL, NULL,
     NULL, NULL, NULL, '2026-09-27 09:00:00', '2026-09-28 16:20:00', '周大勇',
     'FAC-B02-0017：栓口试压仍有渗漏，需更换密封件后重新核验',
     '2026-09-19 14:00:00', '2026-09-28 16:20:00', 5),
    (4, 'DP-2026-0004', 2, '二层疏散指示回路检修，应急照明临时停用',
     '2026-09-25', '2026-09-29', 4, '孔维民', '陈默',
     'PENDING_RESET', 1, '2026-09-24 10:30:00', '王海燕', NULL, NULL, NULL,
     NULL, NULL, NULL, '2026-09-28 14:00:00', '2026-09-29 09:10:00', '孔维民', NULL,
     '2026-09-23 11:00:00', '2026-09-29 09:10:00', 4),
    (5, 'DP-2026-0005', 2, '五层厨房灶台喷淋系统升级，喷淋与邻近消火栓停用',
     '2026-10-03', '2026-10-08', 5, '吴迪', '陈默',
     'PENDING_APPROVAL', 1, NULL, NULL, NULL, NULL, NULL,
     NULL, NULL, NULL, NULL, NULL, NULL, NULL,
     '2026-09-28 10:00:00', '2026-09-28 10:00:00', 1),
    (6, 'DP-2026-0006', 2, '地下二层消防泵季度保养（草稿，尚未登记措施）',
     '2026-10-10', '2026-10-10', 3, '周大勇', '陈默',
     'DRAFT', 0, NULL, NULL, NULL, NULL, NULL,
     NULL, NULL, NULL, NULL, NULL, NULL, NULL,
     '2026-09-28 15:00:00', '2026-09-28 15:00:00', 0),
    (7, 'DP-2026-0007', 2, '一层中庭烟感联动测试停用（被拒绝，待补充巡查频次后重提）',
     '2026-10-05', '2026-10-06', 4, '孔维民', '陈默',
     'DRAFT', 1, NULL, NULL, '补偿措施未覆盖夜间时段，请补充夜间加密巡查安排', NULL, NULL,
     NULL, NULL, NULL, NULL, NULL, NULL, NULL,
     '2026-09-22 09:00:00', '2026-09-24 10:00:00', 2),
    (8, 'DP-2026-0008', 2, '一层主出入口报警按钮外壳更换（后改为不停用处理，撤销）',
     '2026-09-12', '2026-09-12', 3, '周大勇', '陈默',
     'REVOKED', 1, NULL, NULL, NULL, NULL, NULL,
     '2026-09-11 17:00:00', '陈默', '现场评估可带电更换，无需停用，撤销申请',
     NULL, NULL, NULL, NULL,
     '2026-09-11 09:00:00', '2026-09-11 17:00:00', 1),
    (9, 'DP-2026-0009', 2, '六层屋面设备层消火栓与烟感联动改造（已完成闭环）',
     '2026-09-08', '2026-09-15', 3, '周大勇', '陈默',
     'CLOSED', 1, '2026-09-07 14:00:00', '王海燕', NULL, '2026-09-16 10:00:00', '陈默',
     NULL, NULL, NULL, '2026-09-15 16:00:00', '2026-09-16 09:30:00', '周大勇', NULL,
     '2026-09-05 10:00:00', '2026-09-16 10:00:00', 7);

-- ----------------------------------------------------------------------------
-- 受影响点位 + 生效时原状态快照。marked=1 的（许可 1/2/3/4）facility_point
-- 的 active_disable_permit_id 在下方批量回写；已关闭的许可 9 marked=0。
-- ----------------------------------------------------------------------------
INSERT INTO disable_permit_point (id, permit_id, point_id, origin_status, marked, created_at) VALUES
    -- 0001 生效（即将到期）：二层 12 消火栓（原正常）、13 灭火器（原正常）
    (1,  1, 12, 'NORMAL', 1, '2026-09-25 10:00:00'),
    (2,  1, 13, 'NORMAL', 1, '2026-09-25 10:00:00'),
    -- 0002 生效（已超期）：四层 22 消火栓（原正常）、23 烟感（原正常）
    (3,  2, 22, 'NORMAL', 1, '2026-09-18 09:30:00'),
    (4,  2, 23, 'NORMAL', 1, '2026-09-18 09:30:00'),
    -- 0003 待复位（超期+核验失败）：三层 17 消火栓（原正常）
    (5,  3, 17, 'NORMAL', 1, '2026-09-19 14:00:00'),
    -- 0004 待复位（全部合格）：二层 16 应急照明（原正常）
    (6,  4, 16, 'NORMAL', 1, '2026-09-23 11:00:00'),
    -- 0005 待批准：五层 26 消火栓、27 喷淋
    (7,  5, 26, NULL, 0, '2026-09-28 10:00:00'),
    (8,  5, 27, NULL, 0, '2026-09-28 10:00:00'),
    -- 0006 草稿：地下二层 1 消防泵
    (9,  6, 1,  NULL, 0, '2026-09-28 15:00:00'),
    -- 0007 被拒绝退回：一层 10 烟感（原故障，证明原状态保留不影响申请）
    (10, 7, 10, NULL, 0, '2026-09-22 09:00:00'),
    -- 0008 已撤销：一层 9 报警按钮
    (11, 8, 9,  NULL, 0, '2026-09-11 09:00:00'),
    -- 0009 已关闭：六层 29 消火栓、30 烟感（闭环样板，标记已解除）
    (12, 9, 29, 'NORMAL', 0, '2026-09-05 10:00:00'),
    (13, 9, 30, 'NORMAL', 0, '2026-09-05 10:00:00');

-- 叠加标记回写到设施点位（与上面的 marked=1 严格对应）
UPDATE facility_point SET active_disable_permit_id = 1 WHERE id IN (12, 13);
UPDATE facility_point SET active_disable_permit_id = 2 WHERE id IN (22, 23);
UPDATE facility_point SET active_disable_permit_id = 3 WHERE id = 17;
UPDATE facility_point SET active_disable_permit_id = 4 WHERE id = 16;

-- ----------------------------------------------------------------------------
-- 逐项补偿措施
-- ----------------------------------------------------------------------------
INSERT INTO disable_measure
    (id, permit_id, measure_type, content, spec_desc, created_by, created_at, confirmed_at, confirmed_by, confirm_remark) VALUES
    -- 0001：三项措施，两项已收口、一项未确认（值班页能看到进行中）
    (1,  1, 'SPARE_EXT',    '二层东西疏散口各增配 4kg 干粉灭火器', '4 具 MFZ/ABC4', '陈默', '2026-09-25 10:05:00', '2026-09-27 08:30:00', '周大勇', '已布置并挂临时标识'),
    (2,  1, 'EXTRA_PATROL', '停用期间每层加密巡查，重点查看二层动火点', '每 2 小时一次', '陈默', '2026-09-25 10:05:00', '2026-09-27 08:35:00', '周大勇', NULL),
    (3,  1, 'TEMP_BARRIER', '停用消火栓箱前设临时警戒带与禁动警示牌', '警戒带 2 处', '陈默', '2026-09-25 10:05:00', NULL, NULL, NULL),
    -- 0002：措施全部确认（卡住复位的是未完成维保单 MO-2026-0006 不在范围内，这里没有关联工单）
    (4,  2, 'SPARE_EXT',    '四层弱电井与疏散口增配灭火器', '2 具 MFZ/ABC3', '陈默', '2026-09-18 09:40:00', '2026-09-20 08:20:00', '孔维民', NULL),
    (5,  2, 'EXTRA_PATROL', '线路检修期间安排专人每小时巡查四层', '每 1 小时一次', '陈默', '2026-09-18 09:40:00', '2026-09-20 08:22:00', '孔维民', NULL),
    (6,  2, 'TEMP_BARRIER', '检修区域封闭管理，悬挂“消防设施检修中”标识', '围挡 1 处', '陈默', '2026-09-18 09:40:00', '2026-09-20 08:25:00', '孔维民', NULL),
    -- 0003：措施齐
    (7,  3, 'SPARE_EXT',    '三层西侧通道临时增配灭火器', '2 具 4kg 干粉', '陈默', '2026-09-19 14:10:00', '2026-09-21 09:00:00', '周大勇', NULL),
    (8,  3, 'EXTRA_PATROL', '维修期间加密三层西侧巡查', '每 2 小时一次', '陈默', '2026-09-19 14:10:00', '2026-09-21 09:02:00', '周大勇', NULL),
    (9,  3, 'TEMP_BARRIER', '漏水区域设警戒与接水桶', '警戒带 1 处', '陈默', '2026-09-19 14:10:00', '2026-09-21 09:05:00', '周大勇', NULL),
    -- 0004：措施齐
    (10, 4, 'EXTRA_PATROL', '应急照明停用期间安排人工引导疏散', '夜间每小时巡查', '陈默', '2026-09-23 11:10:00', '2026-09-25 08:40:00', '孔维民', NULL),
    (11, 4, 'TEMP_BARRIER', '二层疏散指示灯位贴临时反光指示贴', '反光贴 6 张', '陈默', '2026-09-23 11:10:00', '2026-09-25 08:42:00', '孔维民', NULL),
    -- 0005 待批准：两项措施待生效后执行
    (12, 5, 'SPARE_EXT',    '厨房两个安全出口增配灭火毯与灭火器', '灭火毯 2 块、灭火器 2 具', '陈默', '2026-09-28 10:10:00', NULL, NULL, NULL),
    (13, 5, 'EXTRA_PATROL', '升级作业期间灶台区域专人值守', '作业全程值守', '陈默', '2026-09-28 10:10:00', NULL, NULL, NULL),
    -- 0007 被拒绝：措施只覆盖白天（对应拒绝意见）
    (14, 7, 'EXTRA_PATROL', '测试期间一层中庭每 2 小时巡查（仅营业时段）', '10:00-22:00', '陈默', '2026-09-22 09:10:00', NULL, NULL, NULL),
    (15, 7, 'TEMP_BARRIER', '测试烟感旁设置“测试中 勿惊慌”提示牌', '提示牌 1 块', '陈默', '2026-09-22 09:10:00', NULL, NULL, NULL),
    -- 0009 已关闭：措施历史
    (16, 9, 'SPARE_EXT',    '屋面设备层临时放置灭火器', '2 具 4kg 干粉', '陈默', '2026-09-05 10:10:00', '2026-09-08 08:30:00', '周大勇', NULL),
    (17, 9, 'TEMP_BARRIER', '改造区域设临时围挡', '围挡 1 处', '陈默', '2026-09-05 10:10:00', '2026-09-08 08:32:00', '周大勇', NULL);

-- ----------------------------------------------------------------------------
-- 复位核验记录
--   许可 3：两轮，最新一轮仍 FAIL
--   许可 4：一轮全 PASS（待消防主管关闭）
--   许可 9：一轮全 PASS（已关闭的闭环样板）
-- ----------------------------------------------------------------------------
INSERT INTO reset_verification
    (batch_no, permit_id, point_id, result, verify_desc, fail_reason, verified_by, created_at) VALUES
    ('RV-3-01', 3, 17, 'FAIL', '目视检查栓口连接处', '栓口试压 0.25MPa 低于设计值，判定不合格', '周大勇', '2026-09-27 09:00:00'),
    ('RV-3-02', 3, 17, 'FAIL', '更换密封件后复测', 'FAC-B02-0017：栓口试压仍有渗漏，需更换密封件后重新核验', '周大勇', '2026-09-28 16:20:00'),
    ('RV-4-01', 4, 16, 'PASS', '应急照明通电试验 90 分钟，照度达标', NULL, '孔维民', '2026-09-29 09:10:00'),
    ('RV-9-01', 9, 29, 'PASS', '消火栓出水试验正常', NULL, '周大勇', '2026-09-16 09:20:00'),
    ('RV-9-01', 9, 30, 'PASS', '烟感联动测试正常', NULL, '周大勇', '2026-09-16 09:25:00');

-- ----------------------------------------------------------------------------
-- 维保单处理说明：许可 3 复位前，现场负责人对仍在处理中的 MO-2026-0015
-- 逐单登记处理说明（密封件到货后继续处理，与复位核验结论分别跟踪）。
-- ----------------------------------------------------------------------------
INSERT INTO permit_order_waiver (permit_id, order_id, handle_note, created_by, created_at) VALUES
    (3, 15, '栓口渗漏维修与本次复位核验分别跟踪：密封件到货后继续处理，不影响其它点位复位结论', '周大勇', '2026-09-27 08:50:00');

-- ----------------------------------------------------------------------------
-- 动作留痕（按时间顺序；id 自增即可保证详情中按顺序追溯）
-- ----------------------------------------------------------------------------
INSERT INTO permit_action_log
    (permit_id, action, from_status, to_status, operator, operator_role, remark, created_at) VALUES
    -- 许可 1
    (1, 'CREATE',       NULL,               'DRAFT',            '陈默',   'FIRE_MANAGER',   '创建停用许可草稿', '2026-09-25 10:00:00'),
    (1, 'SUBMIT',       'DRAFT',            'PENDING_APPROVAL', '陈默',   'FIRE_MANAGER',   '提交安全负责人批准，范围 2 个点位', '2026-09-25 14:00:00'),
    (1, 'APPROVE',      'PENDING_APPROVAL', 'EFFECTIVE',        '王海燕', 'SAFETY_OFFICER', '批准生效，2 个点位挂「许可停用」标记，原状态保留', '2026-09-26 15:10:00'),
    (1, 'MEASURE_DONE', 'EFFECTIVE',        'EFFECTIVE',        '周大勇', 'SITE_OWNER',     '确认补偿措施已收口：替代灭火器 二层东西疏散口各增配 4kg 干粉灭火器', '2026-09-27 08:30:00'),
    (1, 'MEASURE_DONE', 'EFFECTIVE',        'EFFECTIVE',        '周大勇', 'SITE_OWNER',     '确认补偿措施已收口：加密巡查 停用期间每层加密巡查，重点查看二层动火点', '2026-09-27 08:35:00'),
    -- 许可 2（已超期仍在生效，系统不自动关闭）
    (2, 'CREATE',       NULL,               'DRAFT',            '陈默',   'FIRE_MANAGER',   '创建停用许可草稿', '2026-09-18 09:30:00'),
    (2, 'SUBMIT',       'DRAFT',            'PENDING_APPROVAL', '陈默',   'FIRE_MANAGER',   '提交批准，范围 2 个点位', '2026-09-18 10:00:00'),
    (2, 'APPROVE',      'PENDING_APPROVAL', 'EFFECTIVE',        '王海燕', 'SAFETY_OFFICER', '批准生效，2 个点位挂「许可停用」标记，原状态保留', '2026-09-19 11:00:00'),
    (2, 'MEASURE_DONE', 'EFFECTIVE',        'EFFECTIVE',        '孔维民', 'SITE_OWNER',     '确认补偿措施已收口：替代灭火器 四层弱电井与疏散口增配灭火器', '2026-09-20 08:20:00'),
    (2, 'MEASURE_DONE', 'EFFECTIVE',        'EFFECTIVE',        '孔维民', 'SITE_OWNER',     '确认补偿措施已收口：加密巡查 线路检修期间安排专人每小时巡查四层', '2026-09-20 08:22:00'),
    (2, 'MEASURE_DONE', 'EFFECTIVE',        'EFFECTIVE',        '孔维民', 'SITE_OWNER',     '确认补偿措施已收口：临时警戒 检修区域封闭管理，悬挂“消防设施检修中”标识', '2026-09-20 08:25:00'),
    -- 许可 3（待复位、超期、核验失败两轮）
    (3, 'CREATE',       NULL,               'DRAFT',            '陈默',   'FIRE_MANAGER',   '创建停用许可草稿', '2026-09-19 14:00:00'),
    (3, 'SUBMIT',       'DRAFT',            'PENDING_APPROVAL', '陈默',   'FIRE_MANAGER',   '提交批准，范围 1 个点位', '2026-09-19 15:00:00'),
    (3, 'APPROVE',      'PENDING_APPROVAL', 'EFFECTIVE',        '王海燕', 'SAFETY_OFFICER', '批准生效，1 个点位挂「许可停用」标记，原状态保留', '2026-09-20 16:00:00'),
    (3, 'MEASURE_DONE', 'EFFECTIVE',        'EFFECTIVE',        '周大勇', 'SITE_OWNER',     '确认补偿措施已收口：替代灭火器 三层西侧通道临时增配灭火器', '2026-09-21 09:00:00'),
    (3, 'MEASURE_DONE', 'EFFECTIVE',        'EFFECTIVE',        '周大勇', 'SITE_OWNER',     '确认补偿措施已收口：加密巡查 维修期间加密三层西侧巡查', '2026-09-21 09:02:00'),
    (3, 'MEASURE_DONE', 'EFFECTIVE',        'EFFECTIVE',        '周大勇', 'SITE_OWNER',     '确认补偿措施已收口：临时警戒 漏水区域设警戒与接水桶', '2026-09-21 09:05:00'),
    (3, 'RESET_START',  'EFFECTIVE',        'PENDING_RESET',    '周大勇', 'SITE_OWNER',     '发起复位核验（批次 RV-3-01）', '2026-09-27 09:00:00'),
    (3, 'ORDER_WAIVER', 'PENDING_RESET',    'PENDING_RESET',    '周大勇', 'SITE_OWNER',     '关联维保单 MO-2026-0015 处理说明：栓口渗漏维修与本次复位核验分别跟踪：密封件到货后继续处理，不影响其它点位复位结论', '2026-09-27 09:00:00'),
    (3, 'RESET_VERIFY', 'PENDING_RESET',    'PENDING_RESET',    '周大勇', 'SITE_OWNER',     '第 1 轮复位核验：合格 0 / 不合格 1；FAC-B02-0017：栓口试压 0.25MPa 低于设计值，判定不合格', '2026-09-27 09:00:00'),
    (3, 'RESET_VERIFY', 'PENDING_RESET',    'PENDING_RESET',    '周大勇', 'SITE_OWNER',     '第 2 轮复位核验：合格 0 / 不合格 1；FAC-B02-0017：栓口试压仍有渗漏，需更换密封件后重新核验', '2026-09-28 16:20:00'),
    -- 许可 4（待复位、全部合格、等待关闭）
    (4, 'CREATE',       NULL,               'DRAFT',            '陈默',   'FIRE_MANAGER',   '创建停用许可草稿', '2026-09-23 11:00:00'),
    (4, 'SUBMIT',       'DRAFT',            'PENDING_APPROVAL', '陈默',   'FIRE_MANAGER',   '提交批准，范围 1 个点位', '2026-09-23 14:00:00'),
    (4, 'APPROVE',      'PENDING_APPROVAL', 'EFFECTIVE',        '王海燕', 'SAFETY_OFFICER', '批准生效，1 个点位挂「许可停用」标记，原状态保留', '2026-09-24 10:30:00'),
    (4, 'MEASURE_DONE', 'EFFECTIVE',        'EFFECTIVE',        '孔维民', 'SITE_OWNER',     '确认补偿措施已收口：加密巡查 应急照明停用期间安排人工引导疏散', '2026-09-25 08:40:00'),
    (4, 'MEASURE_DONE', 'EFFECTIVE',        'EFFECTIVE',        '孔维民', 'SITE_OWNER',     '确认补偿措施已收口：临时警戒 二层疏散指示灯位贴临时反光指示贴', '2026-09-25 08:42:00'),
    (4, 'RESET_START',  'EFFECTIVE',        'PENDING_RESET',    '孔维民', 'SITE_OWNER',     '发起复位核验（批次 RV-4-01）', '2026-09-28 14:00:00'),
    (4, 'RESET_VERIFY', 'PENDING_RESET',    'PENDING_RESET',    '孔维民', 'SITE_OWNER',     '第 1 轮复位核验：合格 1 / 不合格 0，全部合格，待消防主管关闭许可', '2026-09-29 09:10:00'),
    -- 许可 5 待批准
    (5, 'CREATE',       NULL,               'DRAFT',            '陈默',   'FIRE_MANAGER',   '创建停用许可草稿', '2026-09-28 10:00:00'),
    (5, 'SUBMIT',       'DRAFT',            'PENDING_APPROVAL', '陈默',   'FIRE_MANAGER',   '提交安全负责人批准，范围 2 个点位', '2026-09-28 10:00:00'),
    -- 许可 6 草稿
    (6, 'CREATE',       NULL,               'DRAFT',            '陈默',   'FIRE_MANAGER',   '创建停用许可草稿', '2026-09-28 15:00:00'),
    -- 许可 7 被拒绝退回（范围锁定）
    (7, 'CREATE',       NULL,               'DRAFT',            '陈默',   'FIRE_MANAGER',   '创建停用许可草稿', '2026-09-22 09:00:00'),
    (7, 'SUBMIT',       'DRAFT',            'PENDING_APPROVAL', '陈默',   'FIRE_MANAGER',   '提交批准，范围 1 个点位', '2026-09-22 10:00:00'),
    (7, 'REJECT',       'PENDING_APPROVAL', 'DRAFT',            '王海燕', 'SAFETY_OFFICER', '拒绝退回：补偿措施未覆盖夜间时段，请补充夜间加密巡查安排', '2026-09-24 10:00:00'),
    -- 许可 8 已撤销
    (8, 'CREATE',       NULL,               'DRAFT',            '陈默',   'FIRE_MANAGER',   '创建停用许可草稿', '2026-09-11 09:00:00'),
    (8, 'SUBMIT',       'DRAFT',            'PENDING_APPROVAL', '陈默',   'FIRE_MANAGER',   '提交批准，范围 1 个点位', '2026-09-11 09:30:00'),
    (8, 'REVOKE',       'PENDING_APPROVAL', 'REVOKED',          '陈默',   'FIRE_MANAGER',   '撤销许可：现场评估可带电更换，无需停用，撤销申请', '2026-09-11 17:00:00'),
    -- 许可 9 完整闭环
    (9, 'CREATE',       NULL,               'DRAFT',            '陈默',   'FIRE_MANAGER',   '创建停用许可草稿', '2026-09-05 10:00:00'),
    (9, 'SUBMIT',       'DRAFT',            'PENDING_APPROVAL', '陈默',   'FIRE_MANAGER',   '提交批准，范围 2 个点位', '2026-09-05 11:00:00'),
    (9, 'APPROVE',      'PENDING_APPROVAL', 'EFFECTIVE',        '王海燕', 'SAFETY_OFFICER', '批准生效，2 个点位挂「许可停用」标记，原状态保留', '2026-09-07 14:00:00'),
    (9, 'MEASURE_DONE', 'EFFECTIVE',        'EFFECTIVE',        '周大勇', 'SITE_OWNER',     '确认补偿措施已收口：替代灭火器 屋面设备层临时放置灭火器', '2026-09-08 08:30:00'),
    (9, 'MEASURE_DONE', 'EFFECTIVE',        'EFFECTIVE',        '周大勇', 'SITE_OWNER',     '确认补偿措施已收口：临时警戒 改造区域设临时围挡', '2026-09-08 08:32:00'),
    (9, 'RESET_START',  'EFFECTIVE',        'PENDING_RESET',    '周大勇', 'SITE_OWNER',     '发起复位核验（批次 RV-9-01）', '2026-09-15 16:00:00'),
    (9, 'RESET_VERIFY', 'PENDING_RESET',    'PENDING_RESET',    '周大勇', 'SITE_OWNER',     '第 1 轮复位核验：合格 2 / 不合格 0，全部合格，待消防主管关闭许可', '2026-09-16 09:30:00'),
    (9, 'CLOSE',        'PENDING_RESET',    'CLOSED',           '陈默',   'FIRE_MANAGER',   '核验全部合格，关闭许可并解除 2 个点位的许可停用标记', '2026-09-16 10:00:00');

-- ============================================================================
-- 模块七：消防演练现场指令与响应打点
--   drill_session            演练场次（草稿/就绪/进行中/暂停/已完成/已取消）
--   drill_post               参与岗位（每个岗位有独立的岗位响应记录）
--   drill_card               现场指令卡（待下达/等待响应/已确认/已超时/已跳过）
--   drill_card_dependency    指令卡前置节点（多对多，开始后冻结）
--   drill_response_attempt   岗位响应尝试（确认落账 + 迟到尝试，全部保留）
--   drill_event              只追加事件记录（操作者/服务端时间/前后状态/业务说明）
--   drill_template*          常用演练脚本（场次开始后再改脚本不影响已冻结场次）
--
-- 计时口径：deadline 与 remaining_frozen_seconds 全部落在库里，剩余秒数由服务端
-- 当前时间计算；暂停冻结剩余，恢复从剩余值继续。重启 / 刷新 / 多页面交替一致。
--
-- drill_event.uk_event_action_key：同一客户端动作标识在同一场次内只能产生一条
-- 事件（TIMEOUT 为服务端扫描产生，action_key 为 NULL），重复打点不重复推进状态。
-- ============================================================================

DROP TABLE IF EXISTS drill_template_post;
DROP TABLE IF EXISTS drill_template_card;
DROP TABLE IF EXISTS drill_template;
DROP TABLE IF EXISTS drill_event;
DROP TABLE IF EXISTS drill_response_attempt;
DROP TABLE IF EXISTS drill_card_dependency;
DROP TABLE IF EXISTS drill_card;
DROP TABLE IF EXISTS drill_post;
DROP TABLE IF EXISTS drill_session;

CREATE TABLE drill_session (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    drill_no       VARCHAR(24)  NOT NULL COMMENT '演练编号 DR-年-四位序号',
    title          VARCHAR(128) NOT NULL COMMENT '演练名称',
    building_id    BIGINT                COMMENT '关联建筑（可空）',
    scenario       VARCHAR(500)          COMMENT '演练场景说明',
    organizer      VARCHAR(32)  NOT NULL COMMENT '演练组织者',
    status         VARCHAR(16)  NOT NULL COMMENT 'DRAFT 草稿 / READY 就绪 / RUNNING 进行中 / PAUSED 暂停 / FINISHED 已完成 / CANCELED 已取消',
    ready_at       DATETIME              COMMENT '就绪时间',
    started_at     DATETIME              COMMENT '开始时间（此刻冻结指令链快照）',
    paused_at      DATETIME              COMMENT '最近一次暂停时间',
    finished_at    DATETIME              COMMENT '结束时间',
    finished_by    VARCHAR(32)           COMMENT '结束操作人',
    canceled_at    DATETIME              COMMENT '取消时间',
    canceled_by    VARCHAR(32)           COMMENT '取消操作人',
    cancel_reason  VARCHAR(255)          COMMENT '取消原因',
    created_at     DATETIME     NOT NULL COMMENT '创建时间',
    updated_at     DATETIME     NOT NULL COMMENT '更新时间',
    version        BIGINT       NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    UNIQUE KEY uk_drill_no (drill_no),
    KEY idx_drill_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '消防演练场次';

CREATE TABLE drill_post (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    session_id    BIGINT       NOT NULL COMMENT '所属演练场次',
    post_code     VARCHAR(32)  NOT NULL COMMENT '岗位编码（本场内唯一，指令卡按编码引用）',
    post_name     VARCHAR(64)  NOT NULL COMMENT '岗位名称（如 消控室值班岗）',
    assignee      VARCHAR(32)           COMMENT '岗位人员姓名',
    contact       VARCHAR(32)           COMMENT '联系方式',
    sort_no       INT          NOT NULL DEFAULT 0 COMMENT '排序号',
    created_at    DATETIME     NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_session_code (session_id, post_code),
    KEY idx_post_session (session_id, sort_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '演练参与岗位';

CREATE TABLE drill_card (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    session_id             BIGINT       NOT NULL COMMENT '所属演练场次',
    seq_no                 INT          NOT NULL COMMENT '顺序号',
    title                  VARCHAR(128) NOT NULL COMMENT '指令标题',
    content                VARCHAR(500) NOT NULL COMMENT '指令内容',
    scene_hint             VARCHAR(500)          COMMENT '现场提示',
    target_post_id         BIGINT                COMMENT '目标岗位（草稿期引用岗位行；开始时快照姓名/编码）',
    target_post_code       VARCHAR(32)           COMMENT '目标岗位编码快照',
    target_post_name       VARCHAR(64)           COMMENT '目标岗位名称快照',
    time_limit_seconds     INT          NOT NULL DEFAULT 60 COMMENT '响应时限（秒）',
    status                 VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING 待下达 / WAITING 等待响应 / CONFIRMED 已确认 / TIMEOUT 已超时 / SKIPPED 已跳过',
    issued_at              DATETIME              COMMENT '下达时间',
    issued_by              VARCHAR(32)           COMMENT '下达人',
    deadline               DATETIME              COMMENT '响应截止时间（下达时刻 + 时限；暂停期间冻结不推进）',
    remaining_frozen_seconds INT                COMMENT '暂停时冻结的剩余秒数（NULL=未暂停/不在等待）',
    confirmed_at           DATETIME              COMMENT '确认时间',
    confirmed_by           VARCHAR(32)           COMMENT '确认人（岗位人员）',
    actual_action          VARCHAR(500)          COMMENT '实际动作（确认必填）',
    confirm_remark         VARCHAR(500)          COMMENT '确认备注（可选）',
    timed_out_at           DATETIME              COMMENT '判定超时的服务端时间',
    skipped_at             DATETIME              COMMENT '跳过时间',
    skipped_by             VARCHAR(32)           COMMENT '跳过操作人',
    skip_reason            VARCHAR(500)          COMMENT '跳过原因（跳过必填）',
    disposition            VARCHAR(500)          COMMENT '处置结论（超时/跳过记录结束前必填）',
    disposition_at         DATETIME              COMMENT '处置结论填写时间',
    disposition_by         VARCHAR(32)           COMMENT '处置结论填写人',
    created_at             DATETIME     NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_card_session (session_id, seq_no),
    KEY idx_card_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '现场指令卡';

CREATE TABLE drill_card_dependency (
    id           BIGINT NOT NULL AUTO_INCREMENT,
    session_id   BIGINT NOT NULL COMMENT '所属演练场次（冗余，便于冻结后整链查询）',
    card_id      BIGINT NOT NULL COMMENT '指令卡',
    prereq_id    BIGINT NOT NULL COMMENT '前置指令卡',
    created_at   DATETIME NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dep_card_prereq (card_id, prereq_id),
    KEY idx_dep_card (card_id),
    KEY idx_dep_prereq (prereq_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '指令卡前置节点';

CREATE TABLE drill_response_attempt (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    session_id    BIGINT       NOT NULL COMMENT '所属演练场次',
    card_id       BIGINT       NOT NULL COMMENT '对应指令卡',
    post_id       BIGINT       NOT NULL COMMENT '响应岗位',
    result        VARCHAR(16)  NOT NULL COMMENT 'CONFIRMED 按时确认 / LATE 迟到尝试（卡已超时或已跳过后到达）',
    actual_action VARCHAR(500) NOT NULL COMMENT '实际动作',
    remark        VARCHAR(500)          COMMENT '备注',
    operator      VARCHAR(32)  NOT NULL COMMENT '提交人',
    created_at    DATETIME     NOT NULL COMMENT '服务端接收时间',
    PRIMARY KEY (id),
    KEY idx_attempt_session (session_id, id),
    KEY idx_attempt_card (card_id, id),
    KEY idx_attempt_post (post_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '岗位响应尝试（含迟到尝试，只追加）';

CREATE TABLE drill_event (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    session_id   BIGINT       NOT NULL COMMENT '所属演练场次',
    action       VARCHAR(24)  NOT NULL COMMENT 'CREATE/READY/START/ISSUE/CONFIRM/LATE_CONFIRM/TIMEOUT/PAUSE/RESUME/SKIP/DISPOSE/CANCEL/FINISH',
    target_type  VARCHAR(16)           COMMENT '作用对象类型：SESSION / CARD（场次级动作为 NULL）',
    target_id    BIGINT                COMMENT '作用对象 id（指令卡动作记录 card id）',
    action_key   VARCHAR(64)           COMMENT '客户端动作标识（幂等键；服务端 TIMEOUT 为 NULL）',
    from_status  VARCHAR(16)           COMMENT '动作前状态',
    to_status    VARCHAR(16)           COMMENT '动作后状态',
    operator     VARCHAR(32)  NOT NULL COMMENT '操作者（系统超时记为「系统」）',
    note         VARCHAR(1000)         COMMENT '业务说明',
    created_at   DATETIME     NOT NULL COMMENT '服务端动作时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_action_key (session_id, action_key),
    KEY idx_event_session (session_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '演练事件记录（只追加）';

CREATE TABLE drill_template (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(128) NOT NULL COMMENT '脚本名称',
    description VARCHAR(500)          COMMENT '脚本说明',
    created_by  VARCHAR(32)  NOT NULL COMMENT '创建人',
    created_at  DATETIME     NOT NULL COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_template_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '常用演练脚本';

CREATE TABLE drill_template_post (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    template_id  BIGINT      NOT NULL COMMENT '所属脚本',
    post_code    VARCHAR(32) NOT NULL COMMENT '岗位编码',
    post_name    VARCHAR(64) NOT NULL COMMENT '岗位名称',
    sort_no      INT         NOT NULL DEFAULT 0 COMMENT '排序号',
    PRIMARY KEY (id),
    KEY idx_tpost_template (template_id, sort_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '脚本岗位';

CREATE TABLE drill_template_card (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    template_id        BIGINT       NOT NULL COMMENT '所属脚本',
    seq_no             INT          NOT NULL COMMENT '顺序号',
    title              VARCHAR(128) NOT NULL COMMENT '指令标题',
    content            VARCHAR(500) NOT NULL COMMENT '指令内容',
    scene_hint         VARCHAR(500)          COMMENT '现场提示',
    target_post_code   VARCHAR(32)           COMMENT '目标岗位编码（按编码对应到场次岗位）',
    time_limit_seconds INT          NOT NULL DEFAULT 60 COMMENT '响应时限（秒）',
    prereq_seqs        VARCHAR(128)          COMMENT '前置节点顺序号，逗号分隔（如 1,2）',
    PRIMARY KEY (id),
    KEY idx_tcard_template (template_id, seq_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '脚本指令卡';

-- ----------------------------------------------------------------------------
-- 常用脚本演示数据：商业综合体火灾疏散标准演练（4 岗 6 卡，含前置链）
-- ----------------------------------------------------------------------------
INSERT INTO drill_template (id, name, description, created_by, created_at, updated_at) VALUES
    (1, '商业综合体火灾疏散标准演练', '发现火情 → 消控室核实 → 启动应急广播 → 各楼层引导疏散 → 清点上报 → 终止演练的标准指令链', '陈默', '2026-09-20 10:00:00', '2026-09-20 10:00:00');

INSERT INTO drill_template_post (id, template_id, post_code, post_name, sort_no) VALUES
    (1, 1, 'CTRL',  '消控室值班岗', 1),
    (2, 1, 'PATROL','巡逻处置岗',   2),
    (3, 1, 'GUIDE', '楼层引导岗',   3),
    (4, 1, 'CMD',   '现场总指挥岗', 4);

INSERT INTO drill_template_card
    (id, template_id, seq_no, title, content, scene_hint, target_post_code, time_limit_seconds, prereq_seqs) VALUES
    (1, 1, 1, '发现火情并上报', '一层餐饮区疑似起火，立即到现场确认并向消控室报告火情位置、燃烧物与火势',
     '携带对讲机，就近取灭火器先行观察，注意自身站位避开烟气方向', 'PATROL', 30, NULL),
    (2, 1, 2, '消控室核实火情', '接到巡逻岗上报后，调用一层监控与火灾报警主机核实，并通知现场总指挥',
     '核对报警主机回路号，避免误报直接广播', 'CTRL', 25, '1'),
    (3, 1, 3, '启动应急广播', '总指挥下令后，启动消防应急广播，按楼层依次播报疏散指令',
     '先起火层、再相邻层，广播词使用标准话术', 'CTRL', 20, '2'),
    (4, 1, 4, '组织一层人员疏散', '收到广播后，在一层安全出口与中庭位置引导顾客低姿撤离至集合点',
     '提醒勿乘扶梯/电梯，照顾行动不便人员', 'GUIDE', 45, '3'),
    (5, 1, 5, '清点人数并上报', '在集合点清点一层疏散人数，确认无被困人员后向总指挥报告',
     '人数与值班表核对，缺员立即报告具体位置', 'GUIDE', 40, '4'),
    (6, 1, 6, '宣布演练结束', '汇总各岗位情况，确认现场安全，宣布演练结束并组织讲评',
     '讲评记录各节点响应耗时与超时项', 'CMD', 30, '5');

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
-- 数据小结：
--   building 5 / building_floor 18 / facility_point 38 / extinguisher 14
--   inspection 12 / inspection_item 48 / maintenance_order 14
--   drill_template 1（4 岗 6 卡）
-- ============================================================================
