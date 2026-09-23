package net.zentao.platform.menu;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** menu 表读写（T21）。行数 = 管理员覆盖的内置节点 + 新增节点，通常几十行，故一次性全取。 */
@Component
public class MenuRepository {

  static final QueryColumn ID = new QueryColumn("id");
  static final QueryColumn NODE_KEY = new QueryColumn("node_key");
  static final QueryColumn ORDER_NO = new QueryColumn("order_no");

  public static final String ACTIVE = "active";
  /** 新增节点的 key 前缀（内置节点的 key 是组/分区键或页面路径，不会长这样）。 */
  public static final String NEW_KEY_PREFIX = "db-";

  private final MenuMapper mapper;

  public MenuRepository(MenuMapper mapper) {
    this.mapper = mapper;
  }

  public List<MenuPO> listAll() {
    return mapper.selectListByQuery(QueryWrapper.create()
        .orderBy(ORDER_NO.asc(), ID.asc())); // banned-words-ok：MyBatis-Flex 构造器方法名，非请求参数
  }

  public Optional<MenuPO> findByKey(String nodeKey) {
    return Optional.ofNullable(mapper.selectOneByCondition(NODE_KEY.eq(nodeKey)));
  }

  public Optional<MenuPO> find(long id) {
    return Optional.ofNullable(mapper.selectOneByCondition(ID.eq(id)));
  }

  public void insert(MenuPO po) {
    mapper.insert(po);
  }

  public void update(MenuPO po) {
    mapper.update(po);
  }

  public int delete(long id) {
    return mapper.deleteByCondition(ID.eq(id));
  }

  /** 整棵子树（含自身）的行：删目录要连子孙一起删，否则留下悬空的 parent_key。 */
  public List<MenuPO> subtree(MenuPO root) {
    List<MenuPO> all = listAll();
    List<MenuPO> collected = new ArrayList<>();
    collected.add(root);
    boolean grew = true;
    while (grew) {
      grew = false;
      for (MenuPO row : all) {
        if (!collected.contains(row) && row.getParentKey() != null
            && collected.stream().anyMatch(kept -> kept.getNodeKey().equals(row.getParentKey()))) {
          collected.add(row);
          grew = true;
        }
      }
    }
    return collected;
  }
}
