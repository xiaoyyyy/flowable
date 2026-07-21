package com.ruoyi.flowable.listener;

import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.impl.interceptor.CommandContext;
import org.flowable.engine.impl.agenda.TakeOutgoingSequenceFlowsOperation;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;

/**
 * @author zhengzhiyong
 * @des
 * @date 2026/7/21
 */
@Slf4j
public class OutFlowListener extends TakeOutgoingSequenceFlowsOperation {

    public OutFlowListener(CommandContext commandContext, ExecutionEntity executionEntity, boolean evaluateConditions, boolean forcedSynchronous) {
        super(commandContext, executionEntity, evaluateConditions, forcedSynchronous);
    }
}
