-- T67 / AUDIT DB-10：枚举列 CHECK 约束（48 条）+ DB-17 的 budget_unit 口径。
--
-- 取值集的**唯一真源是代码**（enum 类 / `*Fields` 守卫集合 / View 的 @Schema allowableValues），
-- 本迁移只是把「应用层保证」升级成「数据库兜底」——应用层校验给用户 42201 字段错误，CHECK 兜住
-- 直写库/漏守卫的 bug（CHECK 违约按 CONVENTIONS §2.3 属于缺守卫的 bug → 50001，不映射，fail-loud）。
-- 每条约束带真源注释；**不确定取值集的列一律不加**（把合法值挡在门外 = 生产事故），跳过清单见 T67 任务卡。
-- 命名 `ck_<表>_<列>`。方言：MySQL 8.0.16+ 与 H2(MODE=MySQL) 都强制 CHECK（H2 实测强制，见 T67 验收）；
-- 回滚 = MySQL `ALTER TABLE … DROP CHECK ck_…` / H2 `ALTER TABLE … DROP CONSTRAINT ck_…`。
-- 存量数据与测试夹具必须满足约束（本迁移在全链回放 + MigrationCliIT 真 MySQL 逐表对账上验证过）。

-- ===== org =====
-- Gender 枚举（platform.session.Gender）：m|f
ALTER TABLE account ADD CONSTRAINT ck_account_gender CHECK (gender IN ('m', 'f'));
-- AccountStatus 枚举：active|disabled（锁定是 locked_at 时间戳，不是状态值）
ALTER TABLE account ADD CONSTRAINT ck_account_status CHECK (status IN ('active', 'disabled'));

-- ===== platform（menu / dict / lang_import）=====
-- SaveMenuHandler.STATUSES
ALTER TABLE menu ADD CONSTRAINT ck_menu_status CHECK (status IN ('active', 'disabled'));
-- MenuPO.DIR/MENU/BUTTON（V32 node_type）
ALTER TABLE menu ADD CONSTRAINT ck_menu_node_type CHECK (node_type IN ('dir', 'menu', 'button'));
-- SaveDictHandler.STATUSES（create/update 同源）
ALTER TABLE dict_type ADD CONSTRAINT ck_dict_type_status CHECK (status IN ('active', 'disabled'));
ALTER TABLE dict_data ADD CONSTRAINT ck_dict_data_status CHECK (status IN ('active', 'disabled'));
-- LangImportStatus 枚举：success|failed
ALTER TABLE lang_import ADD CONSTRAINT ck_lang_import_status CHECK (status IN ('success', 'failed'));

-- ===== doc =====
-- DocFields.SPACE_TYPES
ALTER TABLE doc_space ADD CONSTRAINT ck_doc_space_type CHECK (type IN ('product', 'project', 'execution', 'custom', 'mine'));
-- DocFields.SPACE_ACLS
ALTER TABLE doc_space ADD CONSTRAINT ck_doc_space_acl CHECK (acl IN ('open', 'default', 'private'));
-- DocFields.SPACE_DOC_SORTS
ALTER TABLE doc_space ADD CONSTRAINT ck_doc_space_doc_sort CHECK (doc_sort IN ('id_asc', 'id_desc'));
-- DocFields.DOC_TYPES（create 现只放行 markdown；html 是声明宇宙的一部分，DB 兜底取全集）
ALTER TABLE doc ADD CONSTRAINT ck_doc_type CHECK (type IN ('markdown', 'html'));
-- DocFields.DOC_STATUSES
ALTER TABLE doc ADD CONSTRAINT ck_doc_status CHECK (status IN ('draft', 'published'));
-- DocFields.DOC_ACLS
ALTER TABLE doc ADD CONSTRAINT ck_doc_acl CHECK (acl IN ('open', 'private'));

-- ===== product =====
-- ProductFields.TYPES
ALTER TABLE product ADD CONSTRAINT ck_product_type CHECK (type IN ('normal', 'branch', 'platform'));
-- ProductView @Schema allowableValues（工作流 close/reopen 只在这两个值之间走）
ALTER TABLE product ADD CONSTRAINT ck_product_status CHECK (status IN ('normal', 'closed'));
-- ProductFields.ACLS（custom = 白名单模式）
ALTER TABLE product ADD CONSTRAINT ck_product_acl CHECK (acl IN ('public', 'private', 'custom'));
-- BranchView @Schema allowableValues
ALTER TABLE branch ADD CONSTRAINT ck_branch_status CHECK (status IN ('active', 'closed'));
-- CategoryHandlers.TYPES
ALTER TABLE category ADD CONSTRAINT ck_category_type CHECK (type IN ('story', 'bug', 'case'));
-- PlanView @Schema allowableValues
ALTER TABLE plan ADD CONSTRAINT ck_plan_status CHECK (status IN ('wait', 'doing', 'done', 'closed'));
-- PlanHandlers.CLOSE_REASONS（可空）
ALTER TABLE plan ADD CONSTRAINT ck_plan_closed_reason CHECK (closed_reason IN ('done', 'cancel'));
-- ReleaseView @Schema allowableValues
ALTER TABLE product_release ADD CONSTRAINT ck_product_release_status CHECK (status IN ('normal', 'terminated'));

-- ===== project =====
-- ProjectFields.TYPES（execution 是 API 分型词，落库是 sprint/stage/kanban 三种）
ALTER TABLE project ADD CONSTRAINT ck_project_type CHECK (type IN ('program', 'project', 'sprint', 'stage', 'kanban'));
-- ProjectFields.MODELS
ALTER TABLE project ADD CONSTRAINT ck_project_model CHECK (model IN ('scrum', 'waterfall', 'kanban'));
-- ProjectFields.validateAcl（open|private|program）
ALTER TABLE project ADD CONSTRAINT ck_project_acl CHECK (acl IN ('open', 'private', 'program'));
-- ProjectFields.BUDGET_UNITS（DB-17：取值封闭 → 不再是自由串）
ALTER TABLE project ADD CONSTRAINT ck_project_budget_unit CHECK (budget_unit IN ('CNY', 'USD'));
-- ManageStageHandler.TYPES
ALTER TABLE stage ADD CONSTRAINT ck_stage_type CHECK (type IN ('mix', 'request', 'design', 'dev', 'qa', 'release', 'review', 'other'));
-- BoardSpaceHandlers.TYPES
ALTER TABLE board_space ADD CONSTRAINT ck_board_space_type CHECK (type IN ('cooperation', 'public', 'private'));
-- BoardHandlers @Schema allowableValues
ALTER TABLE board ADD CONSTRAINT ck_board_status CHECK (status IN ('active', 'closed'));
-- CardHandlers.STATUSES
ALTER TABLE board_card ADD CONSTRAINT ck_board_card_status CHECK (status IN ('doing', 'done'));
-- AddStakeholderHandler.TYPES
ALTER TABLE stakeholder ADD CONSTRAINT ck_stakeholder_type CHECK (type IN ('inside', 'outside'));

-- ===== requirement =====
-- StoryFields.TYPES
ALTER TABLE story ADD CONSTRAINT ck_story_type CHECK (type IN ('story', 'epic', 'requirement'));
-- StoryFields.SOURCES
ALTER TABLE story ADD CONSTRAINT ck_story_source CHECK (source IN ('manual', 'customer', 'market', 'bug', 'other'));
-- StoryView @Schema allowableValues（状态机全集）
ALTER TABLE story ADD CONSTRAINT ck_story_status CHECK (status IN ('draft', 'reviewing', 'active', 'changing', 'changed', 'closed'));
-- StoryFields.CLOSE_REASONS（可空）
ALTER TABLE story ADD CONSTRAINT ck_story_closed_reason CHECK (closed_reason IN ('done', 'duplicate', 'rejected', 'willnotfix', 'postponed'));

-- ===== task =====
-- TaskFields.TYPES
ALTER TABLE task ADD CONSTRAINT ck_task_type CHECK (type IN ('design', 'devel', 'request', 'test', 'study', 'discuss', 'ui', 'affair', 'misc'));
-- TaskView @Schema allowableValues
ALTER TABLE task ADD CONSTRAINT ck_task_status CHECK (status IN ('wait', 'doing', 'done', 'pause', 'closed', 'cancel'));

-- ===== quality =====
-- BugFields.TYPES（V11 列宽 VARCHAR(32)，取值仍以代码集合为准）
ALTER TABLE bug ADD CONSTRAINT ck_bug_type CHECK (type IN ('codeerror', 'config', 'install', 'security', 'performance', 'standard', 'automation', 'designdefect', 'others'));
-- BugView @Schema allowableValues
ALTER TABLE bug ADD CONSTRAINT ck_bug_status CHECK (status IN ('active', 'resolved', 'closed'));
-- BugFields.RESOLUTIONS（可空）
ALTER TABLE bug ADD CONSTRAINT ck_bug_resolution CHECK (resolution IN ('bydesign', 'duplicate', 'external', 'fixed', 'notrepro', 'postponed', 'willnotfix', 'tostory'));
-- TestCaseFields.TYPES
ALTER TABLE test_case ADD CONSTRAINT ck_test_case_type CHECK (type IN ('unit', 'interface', 'feature', 'install', 'config', 'performance', 'security', 'other'));
-- TestCaseView @Schema allowableValues
ALTER TABLE test_case ADD CONSTRAINT ck_test_case_status CHECK (status IN ('wait', 'normal', 'blocked', 'investigate'));
-- RecordResultHandler.RESULTS（可空）
ALTER TABLE test_case ADD CONSTRAINT ck_test_case_last_run_result CHECK (last_run_result IN ('pass', 'fail', 'blocked', 'n/a'));
-- SuiteHandlers.SUITE_TYPES + Suite.TYPE_LIBRARY（/libraries 面强制 type=library，三值才是列的全集）
ALTER TABLE suite ADD CONSTRAINT ck_suite_type CHECK (type IN ('public', 'private', 'library'));
-- TestRunHandlers.TYPES（可空）
ALTER TABLE test_run ADD CONSTRAINT ck_test_run_type CHECK (type IN ('integrate', 'system', 'acceptance', 'performance', 'safety'));
-- TestRunView @Schema allowableValues
ALTER TABLE test_run ADD CONSTRAINT ck_test_run_status CHECK (status IN ('wait', 'doing', 'blocked', 'done'));
-- RecordResultHandler.RESULTS（可空）
ALTER TABLE test_run_case ADD CONSTRAINT ck_test_run_case_result CHECK (result IN ('pass', 'fail', 'blocked', 'n/a'));

-- ===== workspace =====
-- TodoFields.TYPES
ALTER TABLE todo ADD CONSTRAINT ck_todo_type CHECK (type IN ('custom', 'bug', 'task', 'story', 'epic', 'requirement', 'testRun'));
-- TodoView @Schema allowableValues
ALTER TABLE todo ADD CONSTRAINT ck_todo_status CHECK (status IN ('wait', 'doing', 'done', 'closed'));
