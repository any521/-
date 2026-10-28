-- ============================================================
-- V23 · IM 会话/消息表（onsite03 实时通讯 IM 交付，缺口 G3）
-- 背景：后端无 WebSocket 依赖、无会话/消息表；本迁移建表支撑
--       用户↔接单员工实时聊天（文本/表情/语音/视频 + 未读/已读）。
-- 注意：V23 编号保留；V1~V22 已应用禁止改动。
-- ============================================================
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS biz_im_conversation (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    biz_type      VARCHAR(16)  NOT NULL COMMENT 'RECYCLE/REPAIR（后续可扩展 KITCHEN 等）',
    biz_id        BIGINT       NOT NULL COMMENT '关联订单 ID',
    user_id       BIGINT       NOT NULL COMMENT '下单用户 sys_user.id',
    staff_user_id BIGINT       NOT NULL COMMENT '接单员工 sys_user.id（biz_staff.user_id）',
    last_msg_type VARCHAR(16)  COMMENT '最后一条消息类型（列表预览）',
    last_msg      VARCHAR(255) COMMENT '最后一条消息摘要（文本截断/文件类型名）',
    last_time     DATETIME,
    unread_user   INT          NOT NULL DEFAULT 0 COMMENT '用户侧未读',
    unread_staff  INT          NOT NULL DEFAULT 0 COMMENT '员工侧未读',
    deleted       TINYINT      NOT NULL DEFAULT 0,
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME,
    create_by     VARCHAR(64),
    update_by     VARCHAR(64),
    PRIMARY KEY (id),
    UNIQUE KEY uk_im_conv_biz (biz_type, biz_id),
    KEY idx_im_conv_user (user_id),
    KEY idx_im_conv_staff (staff_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='IM 会话（onsite03）';

CREATE TABLE IF NOT EXISTS biz_im_message (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    conv_id     BIGINT       NOT NULL COMMENT '会话 ID（biz_im_conversation.id）',
    sender_id   BIGINT       NOT NULL COMMENT '发送者 sys_user.id',
    sender_role VARCHAR(8)   NOT NULL COMMENT 'USER/STAFF（区分会话两侧展示）',
    msg_type    VARCHAR(16)  NOT NULL COMMENT 'TEXT/IMAGE/STICKER/VOICE/VIDEO/SYSTEM',
    content     TEXT         NOT NULL COMMENT '文本或文件 URL（/api/file/preview/**）',
    duration    INT          NOT NULL DEFAULT 0 COMMENT 'VOICE/VIDEO 时长秒',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 2撤回（预留）',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_im_msg_conv (conv_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='IM 消息（onsite03）';
