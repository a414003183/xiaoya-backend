package net.zentao.org.api;

import java.util.Optional;

/** 部门域对外接口（A2：跨域只经本包）；字典/指派类控件经此取部门信息。 */
public interface DepartmentApi {

  Optional<DepartmentNode> findById(long departmentId);

  /** 全量部门树（嵌套）。 */
  java.util.List<DepartmentNode> tree();
}
