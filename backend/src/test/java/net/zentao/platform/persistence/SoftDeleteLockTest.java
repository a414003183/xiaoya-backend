package net.zentao.platform.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.zentao.ApiTestSupport;
import net.zentao.doc.domain.Doc;
import net.zentao.doc.domain.DocRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * T54 / BE-01 协议回归：**软删是一次行变更，必须推进 lock_version**。
 *
 * <p>跨层取样（两个真实入口，两个被证对象）：删除走 HTTP 真 handler（真权限链 + 真仓储），
 * stale 写走仓储 `update`，用的是「删除前抓到的聚合」——即并发里先读到、后写入的那一方。
 * 旧行为：stale update 匹配成功（WHERE lock_version=? 命中旧值）→ 整行回写、`deleted_at` 被置回 NULL，
 * 数据静默复活且不报 409；新行为：0 行 → handler 照既有口径回 40901。
 */
class SoftDeleteLockTest extends ApiTestSupport {

  @Autowired private DocRepository docs;

  @Test
  @DisplayName("软删后 stale update 必须 0 行（旧行为：命中并复活）")
  void softDeleteBumpsLockVersion() throws Exception {
    String admin = login("admin", "admin123");
    long spaceId =
        dataId(send("POST", "/api/v1/doc-spaces", "{\"name\":\"T54 space\",\"type\":\"custom\"}", admin));
    long docId = dataId(send("POST", "/api/v1/doc-spaces/" + spaceId + "/docs",
        "{\"title\":\"T54 doc\",\"content\":\"body\"}", admin));

    // 删除前抓聚合：版本 = 删除前版本（并发里的「先读后写」一方）
    Doc stale = docs.findActiveById(docId).orElseThrow();

    assertEquals(200, send("DELETE", "/api/v1/docs/" + docId, null, admin).statusCode(),
        "删除应成功");
    assertTrue(docs.findActiveById(docId).isEmpty(), "删除后不该还能读到");

    assertTrue(docs.update(stale).isEmpty(), "软删后旧版本 update 必须 0 行（命中即静默复活且无 409）");
    assertTrue(docs.findActiveById(docId).isEmpty(), "复活必须没有发生");
  }
}
