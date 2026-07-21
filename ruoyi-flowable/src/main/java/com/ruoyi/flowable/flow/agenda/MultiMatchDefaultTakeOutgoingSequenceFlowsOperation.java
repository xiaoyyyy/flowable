package com.ruoyi.flowable.flow.agenda;

import java.util.ArrayList;
import java.util.List;

import org.flowable.bpmn.model.Activity;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.Gateway;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.impl.interceptor.CommandContext;
import org.flowable.common.engine.impl.logging.LoggingSessionConstants;
import org.flowable.engine.impl.agenda.TakeOutgoingSequenceFlowsOperation;
import org.flowable.engine.impl.bpmn.helper.SkipExpressionUtil;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.flowable.engine.impl.persistence.entity.ExecutionEntityManager;
import org.flowable.engine.impl.util.BpmnLoggingSessionUtil;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.engine.impl.util.condition.ConditionUtil;

/**
 * 普通活动节点出线增强：未命中或命中多条条件出线时走默认流，命中一条时正常执行。
 * 网关和非活动节点保持 Flowable 原生语义。
 */
public class MultiMatchDefaultTakeOutgoingSequenceFlowsOperation extends TakeOutgoingSequenceFlowsOperation {

    public MultiMatchDefaultTakeOutgoingSequenceFlowsOperation(CommandContext commandContext,
                                                                 ExecutionEntity executionEntity,
                                                                 boolean evaluateConditions,
                                                                 boolean forcedSynchronous) {
        super(commandContext, executionEntity, evaluateConditions, forcedSynchronous);
    }

    @Override
    protected void leaveFlowNode(FlowNode flowNode) {
        if (!evaluateConditions || flowNode instanceof Gateway || !(flowNode instanceof Activity)) {
            super.leaveFlowNode(flowNode);
            return;
        }

        String defaultSequenceFlowId = ((Activity) flowNode).getDefaultFlow();
        List<SequenceFlow> outgoingSequenceFlows = new ArrayList<>();

        for (SequenceFlow sequenceFlow : flowNode.getOutgoingFlows()) {
            String skipExpression = sequenceFlow.getSkipExpression();
            if (!SkipExpressionUtil.isSkipExpressionEnabled(skipExpression, sequenceFlow.getId(),
                execution, commandContext)) {

                boolean isDefaultFlow = defaultSequenceFlowId != null
                    && defaultSequenceFlowId.equals(sequenceFlow.getId());
                if (!isDefaultFlow && ConditionUtil.hasTrueCondition(sequenceFlow, execution)) {
                    outgoingSequenceFlows.add(sequenceFlow);
                }

            } else if (flowNode.getOutgoingFlows().size() == 1
                || SkipExpressionUtil.shouldSkipFlowElement(skipExpression, sequenceFlow.getId(),
                    execution, commandContext)) {
                // skipExpression 表示跳过条件判断，而不是跳过这条顺序流。
                outgoingSequenceFlows.add(sequenceFlow);
            }
        }

        if (outgoingSequenceFlows.size() > 1) {
            SequenceFlow defaultSequenceFlow = findDefaultSequenceFlow(flowNode, defaultSequenceFlowId);
            if (defaultSequenceFlow == null) {
                throw new FlowableException("普通节点 '" + flowNode.getId() + "' 同时命中 "
                    + outgoingSequenceFlows.size() + " 条出线，但未配置有效的默认流");
            }
            outgoingSequenceFlows.clear();
            outgoingSequenceFlows.add(defaultSequenceFlow);
        }

        if (outgoingSequenceFlows.isEmpty()) {
            SequenceFlow defaultSequenceFlow = findDefaultSequenceFlow(flowNode, defaultSequenceFlowId);
            if (defaultSequenceFlow != null) {
                outgoingSequenceFlows.add(defaultSequenceFlow);
            }
        }

        if (outgoingSequenceFlows.isEmpty()) {
            if (flowNode.getOutgoingFlows().isEmpty()) {
                agenda.planEndExecutionOperation(execution);
            } else {
                throw new FlowableException("No outgoing sequence flow of element '" + flowNode.getId()
                    + "' could be selected for continuing the process");
            }
            return;
        }

        continueWithOutgoingSequenceFlows(outgoingSequenceFlows);
    }

    protected SequenceFlow findDefaultSequenceFlow(FlowNode flowNode, String defaultSequenceFlowId) {
        if (defaultSequenceFlowId == null) {
            return null;
        }
        for (SequenceFlow sequenceFlow : flowNode.getOutgoingFlows()) {
            if (defaultSequenceFlowId.equals(sequenceFlow.getId())) {
                return sequenceFlow;
            }
        }
        return null;
    }

    protected void continueWithOutgoingSequenceFlows(List<SequenceFlow> outgoingSequenceFlows) {
        ProcessEngineConfigurationImpl processEngineConfiguration =
            CommandContextUtil.getProcessEngineConfiguration(commandContext);
        ExecutionEntityManager executionEntityManager = processEngineConfiguration.getExecutionEntityManager();
        List<ExecutionEntity> outgoingExecutions = new ArrayList<>(outgoingSequenceFlows.size());

        execution.setCurrentFlowElement(outgoingSequenceFlows.get(0));
        execution.setActive(false);
        outgoingExecutions.add(execution);

        for (int i = 1; i < outgoingSequenceFlows.size(); i++) {
            ExecutionEntity parent = execution.getParentId() != null ? execution.getParent() : execution;
            ExecutionEntity outgoingExecution = executionEntityManager.createChildExecution(parent);
            outgoingExecution.setActive(false);
            outgoingExecution.setCurrentFlowElement(outgoingSequenceFlows.get(i));
            executionEntityManager.insert(outgoingExecution);
            outgoingExecutions.add(outgoingExecution);
        }

        for (ExecutionEntity outgoingExecution : outgoingExecutions) {
            agenda.planContinueProcessOperation(outgoingExecution);
            if (processEngineConfiguration.isLoggingSessionEnabled()) {
                BpmnLoggingSessionUtil.addSequenceFlowLoggingData(
                    LoggingSessionConstants.TYPE_SEQUENCE_FLOW_TAKE, outgoingExecution);
            }
        }
    }
}
