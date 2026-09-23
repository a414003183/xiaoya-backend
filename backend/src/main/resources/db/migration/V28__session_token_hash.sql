-- 在线用户列表（T13 P1-1）：对外 id 用会话 token 的 sha256，凭据本身不出网。
-- session.id 就是 cookie ZT_SESSION 的值，原样回给列表 = 持 online-user-view 的角色
-- （不必是超管）可以读走任意在线用户（含超管）的会话令牌，等同提权；故列表与强退一律走本列。
-- 与 id 同宽 64 位 hex：等值查/索引与主键同价，且长度不泄露「这是摘要」。
ALTER TABLE session ADD COLUMN token_hash VARCHAR(64) NULL;
CREATE INDEX idx_session_token_hash ON session (token_hash);
