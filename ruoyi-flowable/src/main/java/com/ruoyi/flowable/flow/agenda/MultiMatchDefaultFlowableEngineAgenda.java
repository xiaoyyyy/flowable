package com.ruoyi.flowable.flow.agenda;

import org.flowable.common.engine.impl.interceptor.CommandContext;
import org.flowable.engine.impl.agenda.DefaultFlowableEngineAgenda;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;

/**
 * 使用自定义出线 Operation 的流程引擎 Agenda。
 */
public class MultiMatchDefaultFlowableEngineAgenda extends DefaultFlowableEngineAgenda {

    public MultiMatchDefaultFlowableEngineAgenda(CommandContext commandContext) {
        super(commandContext);
    }

    @Override
    public void planTakeOutgoingSequenceFlowsOperation(ExecutionEntity execution, boolean evaluateConditions) {
        planOperation(new MultiMatchDefaultTakeOutgoingSequenceFlowsOperation(
            commandContext, execution, evaluateConditions, false), execution);
    }

    @Override
    public void planTakeOutgoingSequenceFlowsSynchronousOperation(ExecutionEntity execution,
                                                                   boolean evaluateConditions) {
        planOperation(new MultiMatchDefaultTakeOutgoingSequenceFlowsOperation(
            commandContext, execution, evaluateConditions, true), execution);
    }
}
