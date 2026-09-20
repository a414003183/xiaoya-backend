package net.zentao.project.api;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.zentao.platform.session.SessionPrincipal;

/** 项目域对外接口（A2：跨域只经本包；project 卡 §7）。 */
public interface ProjectApi {

  /** 某型（program|project|execution）的可见 id 集；{@code visibleToAll=true} 表示不受限（超管）。 */
  record VisibleScope(boolean visibleToAll, Set<Long> ids) {}

  VisibleScope visibleScope(SessionPrincipal principal, String type);

  default List<Long> visibleProjectIds(SessionPrincipal principal) {
    return List.copyOf(visibleScope(principal, "project").ids());
  }

  Optional<ProjectView> findById(long id);

  /** 数据权限判定（不抛异常；写路径在 40401 之后调用）。 */
  boolean canAccess(SessionPrincipal principal, long id);

  /** 详情/动作前置守卫：不存在或类型不符 → 40401；存在但不可见 → 40302（先于 40401 防探测）。 */
  ProjectView requireVisible(SessionPrincipal principal, long id, String type);
}
