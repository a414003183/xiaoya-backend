package net.zentao.doc.api;

import java.util.List;
import net.zentao.platform.session.SessionPrincipal;

/** doc 域对外接口（A2：跨域只经本包；doc 卡 §7 跨域供给）。 */
public interface DocSpaceApi {

  /**
   * 账号可见的文档库 id 集（供 platform 搜索与 workspace 聚合过滤文档结果，跨域只读）。
   * 语义同 /doc-spaces 列表门禁：mine 库仅创建者、超管不豁免。
   *
   * <p>签名收 {@link SessionPrincipal} 而非账号串：白名单组命中需要账号 id（PrivilegeChecker.groupsOf），
   * 且超管判定同源，账号串无法独立完成判定。
   */
  List<Long> visibleSpaceIds(SessionPrincipal principal);
}
