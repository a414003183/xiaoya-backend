-- 会话表去冗余列：V35 已把存量行的 id 换成 sha256(cookie token)，token_hash 与之**逐行同值**——
-- 两个字段存同一个值迟早漂移，而"对外 id"这个用途现在由主键本身承担。索引随列一起退休。
--
-- 方言政策（T52）：本目录的 SQL 必须同时是 MySQL 8 与 H2(MODE=MySQL) 的合法语句。
-- 索引一律 `ALTER TABLE … DROP INDEX <名>`（MySQL 里唯一/普通索引都是这个写法，`DROP CONSTRAINT` 要到 8.0.19 才认）。
ALTER TABLE session DROP INDEX idx_session_token_hash;
ALTER TABLE session DROP COLUMN token_hash;
