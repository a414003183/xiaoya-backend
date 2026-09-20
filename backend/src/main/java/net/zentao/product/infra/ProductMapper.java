package net.zentao.product.infra;

import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** product 表 Mapper。 */
@Mapper
public interface ProductMapper extends BaseMapper<ProductPO> {}
