-- workspace 域燃尽日行快照（workspace 卡 §2/§3.3）：UNIQUE(execution_id, burn_date, task_id)，
-- task_id=0 为执行级日行；当日缺失懒算落库，同日 upsert 幂等；无软删、无乐观锁。
CREATE TABLE burn (
    id             BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    execution_id   BIGINT        NOT NULL,
    burn_date      DATE          NOT NULL,
    task_id        BIGINT        NOT NULL DEFAULT 0,
    estimate_hours DECIMAL(12,2) NOT NULL DEFAULT 0,
    consumed_hours DECIMAL(12,2) NOT NULL DEFAULT 0,
    left_hours     DECIMAL(12,2) NOT NULL DEFAULT 0,
    story_point    DECIMAL(12,2) NOT NULL DEFAULT 0,
    CONSTRAINT uk_burn_execution_date_task UNIQUE (execution_id, burn_date, task_id)
);
CREATE INDEX idx_burn_execution_date ON burn (execution_id, burn_date);
