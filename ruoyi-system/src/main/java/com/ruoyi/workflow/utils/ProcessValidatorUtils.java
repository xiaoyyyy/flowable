package com.ruoyi.workflow.utils;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.validation.ValidationError;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程模型校验工具类
 * <p>
 * 提供两部分能力：
 * 1. 对 Flowable 引擎 {@code repositoryService.validateProcess()} 返回的英文错误做中文翻译；
 * 2. 补充 Flowable 引擎默认不做的校验（开始/结束节点数量），并输出中文提示。
 *
 * @author generated
 */
public class ProcessValidatorUtils {

    private ProcessValidatorUtils() {
    }

    /**
     * Flowable 引擎校验错误码 -> 中文描述映射
     * 错误码常量来源：org.flowable.validation.validator.Problems
     * 未匹配到的错误码将回退使用 Flowable 的英文默认描述
     */
    private static final Map<String, String> PROBLEM_MESSAGES = new HashMap<>();

    static {
        // 流程 Process
        PROBLEM_MESSAGES.put("flowable-process-no-start-event", "流程缺少开始节点");
        PROBLEM_MESSAGES.put("flowable-process-definition-not-executable", "流程定义不可执行(isExecutable=false)");
        // 节点连线
        PROBLEM_MESSAGES.put("flowable-elements-no-outgoing-sequence-flow", "节点缺少输出连线，流程无法继续流转");
        PROBLEM_MESSAGES.put("flowable-seqflow-invalid-src", "连线的源节点(sourceRef)无效");
        PROBLEM_MESSAGES.put("flowable-seqflow-invalid-target", "连线的目标节点(targetRef)无效");
        // 排他网关
        PROBLEM_MESSAGES.put("flowable-exclusive-gateway-no-outgoing-seq-flow", "排他网关缺少输出连线");
        PROBLEM_MESSAGES.put("flowable-exclusive-gateway-condition-on-seq-flow", "排他网关的输出连线缺少流转条件");
        PROBLEM_MESSAGES.put("flowable-exclusive-gateway-condition-not-allowed-on-single-seq-flow",
            "排他网关只有一条输出连线时不允许配置流转条件");
        // 服务任务
        PROBLEM_MESSAGES.put("flowable-servicetask-missing-implementation",
            "服务任务缺少实现(class/expression/delegateExpression)");
        // 用户任务
        PROBLEM_MESSAGES.put("flowable-usertask-listener-implementation-missing", "用户任务监听器缺少实现");
        // 定时事件
        PROBLEM_MESSAGES.put("flowable-timer-event-invalid-configuration", "定时事件配置无效");
    }

    /**
     * 校验流程模型，返回中文错误信息列表（为空表示校验通过）
     *
     * @param bpmnModel      流程模型
     * @param flowableErrors Flowable 引擎 validateProcess 的校验结果，可为 null
     * @return 中文错误信息列表
     */
    public static List<String> validate(BpmnModel bpmnModel, List<ValidationError> flowableErrors) {
        List<String> messages = new ArrayList<>();
        // 自定义校验：开始/结束节点数量
        messages.addAll(checkStartEndEvents(bpmnModel));
        // Flowable 引擎校验错误翻译为中文
        if (flowableErrors != null) {
            for (ValidationError error : flowableErrors) {
                messages.add(translate(error));
            }
        }
        return messages;
    }

    /**
     * 校验主流程的开始/结束节点数量
     * <p>
     * 覆盖场景：没有开始节点、多个开始节点、没有结束节点、多个结束节点。
     * 说明：仅统计主流程顶层节点，不含子流程内部的开始/结束节点。
     *
     * @param bpmnModel 流程模型
     * @return 中文错误信息列表
     */
    public static List<String> checkStartEndEvents(BpmnModel bpmnModel) {
        List<String> messages = new ArrayList<>();
        if (bpmnModel == null) {
            messages.add("流程模型为空，请检查流程设计");
            return messages;
        }
        Process process = bpmnModel.getMainProcess();
        if (process == null) {
            messages.add("流程主体不存在，请检查流程设计");
            return messages;
        }

        int startCount = 0;
        int endCount = 0;
        for (FlowElement element : process.getFlowElements()) {
            if (element instanceof StartEvent) {
                startCount++;
            } else if (element instanceof EndEvent) {
                endCount++;
            }
        }

        // 开始节点校验
        if (startCount == 0) {
            messages.add("流程缺少开始节点，请添加一个开始节点");
        } else if (startCount > 1) {
            messages.add("流程存在多个开始节点（当前 " + startCount + " 个），只允许配置一个开始节点");
        }

        // 结束节点校验
        if (endCount == 0) {
            messages.add("流程缺少结束节点，请添加一个结束节点");
        } else if (endCount > 1) {
            messages.add("流程存在多个结束节点（当前 " + endCount + " 个），只允许配置一个结束节点");
        }

        return messages;
    }

    /**
     * 将单个 Flowable 校验错误翻译为中文，找不到映射时回退到英文默认描述，并附加节点定位信息
     *
     * @param error Flowable 校验错误
     * @return 中文错误信息
     */
    public static String translate(ValidationError error) {
        String zh = PROBLEM_MESSAGES.get(error.getProblem());
        String base = (zh != null) ? zh : error.getDefaultDescription();
        String node = (error.getActivityName() != null && !error.getActivityName().isEmpty())
            ? error.getActivityName()
            : error.getActivityId();
        if (node != null && !node.isEmpty()) {
            return String.format("[%s] %s", node, base);
        }
        return base;
    }
}
