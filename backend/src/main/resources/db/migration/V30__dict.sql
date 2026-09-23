-- 字典管理（T16 P1-4）：管理后台可维护的字典。
-- 内置字典（timezones/locales/privileges/accounts…）仍由 DictProvider 代码注册，DB 字典**只做扩展**：
-- DictRegistry 先查注册表、再回落到这两张表（创建时也挡掉与已注册名撞名的 code，双保险）。
-- 列名用 item_label/item_value：value 是 MySQL 保留字（同 setting.item_key/item_value 的处理）。
CREATE TABLE dict_type (
    id     BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code   VARCHAR(60) NOT NULL,
    name   VARCHAR(60) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    CONSTRAINT uq_dict_type_code UNIQUE (code)
);

CREATE TABLE dict_data (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    type_code  VARCHAR(60)  NOT NULL,
    item_label VARCHAR(120) NOT NULL,
    item_value VARCHAR(120) NOT NULL,
    sort_no    INT          NOT NULL DEFAULT 0,
    status     VARCHAR(16)  NOT NULL DEFAULT 'active'
);
CREATE INDEX idx_dict_data_type ON dict_data (type_code, sort_no);
