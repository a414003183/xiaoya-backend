package net.zentao.platform.activity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.zentao.platform.session.SessionPrincipal;
import org.springframework.stereotype.Component;

/**
 * 对象可见性判定注册表（platform 卡 §7.2：详情不可见 → 40302 的平台侧统一注入点）。
 * 各域注册自己的对象可见性谓词；未注册的类型默认可见（P1：org §7 org 域无行级 ACL）。
 */
@Component
public class ObjectVisibilityRegistry {

  public interface VisibilityChecker {
    boolean isVisible(SessionPrincipal principal, long objectId);
  }

  private final Map<String, VisibilityChecker> checkers = new ConcurrentHashMap<>();

  public void register(String objectType, VisibilityChecker checker) {
    checkers.put(objectType, checker);
  }

  public boolean isVisible(SessionPrincipal principal, String objectType, long objectId) {
    VisibilityChecker checker = checkers.get(objectType);
    return checker == null || checker.isVisible(principal, objectId);
  }
}
