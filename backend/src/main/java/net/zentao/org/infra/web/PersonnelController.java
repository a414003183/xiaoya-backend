package net.zentao.org.infra.web;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import net.zentao.org.api.PersonnelMemberList;
import net.zentao.org.api.PersonnelWorkloadList;
import net.zentao.org.app.PersonnelQueryService;
import net.zentao.platform.rbac.RequirePrivilege;
import net.zentao.platform.web.DataEnvelope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 人员管理端点（org 卡 §5 Personnel 节两端点；无行级 ACL，personnel-view 功能码 + 超管）。 */
@RestController
@RequestMapping("/api/v1")
public class PersonnelController {

  private final PersonnelQueryService queryService;

  public PersonnelController(PersonnelQueryService queryService) {
    this.queryService = queryService;
  }

  @GetMapping("/personnel/members")
  @Operation(operationId = "listPersonnelMembers")
  @RequirePrivilege("personnel-view")
  public DataEnvelope<PersonnelMemberList> members(HttpServletRequest request) {
    return DataEnvelope.of(queryService.members(request.getParameterMap()));
  }

  @GetMapping("/personnel/workload")
  @Operation(operationId = "listPersonnelWorkload")
  @RequirePrivilege("personnel-view")
  public DataEnvelope<PersonnelWorkloadList> workload(HttpServletRequest request) {
    return DataEnvelope.of(queryService.workload(request.getParameterMap()));
  }
}
