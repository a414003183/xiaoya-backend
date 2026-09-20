package net.zentao.quality.infra;

import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** test_case 表 Mapper。 */
@Mapper
public interface TestCaseMapper extends BaseMapper<TestCasePO> {}
