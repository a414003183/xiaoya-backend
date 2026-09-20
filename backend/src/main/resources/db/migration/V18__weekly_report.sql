-- workspace 域项目周报快照（workspace 卡 §2/§3.2）：UNIQUE(project_id, week_start)，重算即整行覆写。
-- 无软删、无乐观锁（单行幂等覆写，无并发丢失语义）；analysis 读取时现算不落库（旧 progress 列不保留）。
CREATE TABLE weekly_report (
    id         BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT        NOT NULL,
    week_start DATE          NOT NULL,
    pv         DECIMAL(12,2) NOT NULL DEFAULT 0,
    ev         DECIMAL(12,2) NOT NULL DEFAULT 0,
    ac         DECIMAL(12,2) NOT NULL DEFAULT 0,
    sv         DECIMAL(12,2) NOT NULL DEFAULT 0,
    cv         DECIMAL(12,2) NOT NULL DEFAULT 0,
    staff      INT           NOT NULL DEFAULT 0,
    workload   TEXT          NULL,
    updated_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_weekly_report_project_week UNIQUE (project_id, week_start)
);
CREATE INDEX idx_weekly_report_week ON weekly_report (week_start);
