package com.ruoyi.flowable.flow.agenda;

import org.flowable.common.engine.impl.interceptor.CommandContext;
import org.flowable.engine.FlowableEngineAgenda;
import org.flowable.engine.FlowableEngineAgendaFactory;

/**
 * 自定义流程引擎 Agenda 工厂。
 */
public class MultiMatchDefaultFlowableEngineAgendaFactory implements FlowableEngineAgendaFactory {

    @Override
    public FlowableEngineAgenda createAgenda(CommandContext commandContext) {
        return new MultiMatchDefaultFlowableEngineAgenda(commandContext);
    }
}
