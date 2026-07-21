package com.ruoyi.workflow.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * 流程模型 Mapper 接口
 *
 * @author KonBAI
 */
public interface WfModelMapper {

    /**
     * 修改流程定义描述
     *
     * @param processDefinitionId 流程定义 ID
     * @param description 流程描述
     * @return 受影响行数
     */
    int updateProcessDefinitionDescription(@Param("processDefinitionId") String processDefinitionId,
                                           @Param("description") String description);
}
