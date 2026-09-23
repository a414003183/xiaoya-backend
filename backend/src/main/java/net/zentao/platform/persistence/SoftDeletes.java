package net.zentao.platform.persistence;

import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;

/**
 * 软删/恢复的唯一写入口（T54 / BE-01）：软删是一次行变更，必须与 update 走同一套乐观锁协议。
 *
 * <p>为什么需要它：{@code @Column(version = true)} 的版本协议只作用于实体（PO）SQL 生成
 * （{@code mapper.update(po, …)} 自带 {@code AND lock_version = ?} 与 {@code SET lock_version = lock_version + 1}），
 * 而 {@code Db.updateByCondition} 这类 Row API 语句完全绕过它。于是一次并发的旧版本 update——它带着删除前读到的
 * {@code lockVersion}——在软删之后仍然匹配成功，把整行（含 {@code deleted_at = NULL}）写回：数据静默复活且不报 409。
 *
 * <p>用法：把软删/恢复语句的 {@code Db.updateByCondition(table, row, where)} 换成
 * {@code SoftDeletes.apply(table, row, where)}，row 里照旧含 {@code deleted_at}。
 * 免锁路径（{@code path}/{@code views}/{@code sort} 等派生列自增）**不要**用本入口：它们故意不推进版本。
 *
 * <p>无版本列的关联表（V8 的 {@code stakeholder}/{@code team_member}、V4 的 {@code file}）不适用——它们保留
 * {@code Db.updateByCondition}/{@code mapper.update} 并在门禁里挂 {@code // soft-delete-ok：<理由>} 豁免。
 */
public final class SoftDeletes {

  private SoftDeletes() {}

  /** 等价于 {@code Db.updateByCondition}，但额外推进 {@code lock_version}（返回受影响行数）。 */
  public static int apply(String table, Row row, QueryCondition where) {
    return Db.updateByCondition(table, row.setRaw("lock_version", "lock_version + 1"), where);
  }
}
