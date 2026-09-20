package net.zentao.project.api;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.zentao.platform.session.SessionPrincipal;

/**
 * 执行域对外接口（A2；project 卡 §3.1/§7，task 卡 §7 的两个消费点）：
 * 可见执行 id 集（任务列表 DataScope 注入 `execution_id IN`）与执行状态（已关闭 → 写端点 42203 只读闸门）。
 */
public interface ExecutionApi {

  /** {@code visibleToAll=true} 表示不受限（超管），调用方不加过滤条件。 */
  record ExecutionScope(boolean visibleToAll, Set<Long> executionIds) {}

  ExecutionScope executionScope(SessionPrincipal principal);

  default List<Long> visibleExecutionIds(SessionPrincipal principal) {
    return List.copyOf(executionScope(principal).executionIds());
  }

  Optional<ProjectView> findExecution(long executionId);

  /** 执行状态码；不存在返回空。 */
  Optional<String> status(long executionId);

  /** 不存在或非执行型 → 40401；存在但不可见 → 40302。 */
  ProjectView requireExecution(SessionPrincipal principal, long executionId);

  /** 写闸门（task 卡 §7）：执行已关闭 → 42203，其下任务/工时整体只读。 */
  ProjectView requireWritable(SessionPrincipal principal, long executionId);
}
