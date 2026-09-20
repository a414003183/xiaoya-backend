-- 语言包上传记录（platform 卡 §3.12）：多语言 Excel 导入/导出的操作日志——谁在什么时候传了什么、成了几行。
-- 校验失败同样留痕（status='failed' + message 存失败摘要），故 message 可为 NULL；
-- 本表只追加、不修改业务语义（无写路径），审计四件套 + 软删 + 乐观锁按 02 §5 约定保留。
CREATE TABLE lang_import (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    lang         VARCHAR(30)  NOT NULL,
    file_name    VARCHAR(255) NOT NULL,
    total_rows   INT          NOT NULL DEFAULT 0,
    applied_rows INT          NOT NULL DEFAULT 0,
    failed_rows  INT          NOT NULL DEFAULT 0,
    status       VARCHAR(32)  NOT NULL,
    message      TEXT         NULL,
    created_by   VARCHAR(64)  NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   VARCHAR(64)  NULL,
    updated_at   TIMESTAMP    NULL,
    deleted_at   TIMESTAMP    NULL,
    lock_version INT          NOT NULL DEFAULT 0
);
CREATE INDEX idx_lang_import_created ON lang_import (created_at);
