package com.ruoyi.flowable.listener;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.ruoyi.flowable.common.constant.ProcessConstants;
import com.ruoyi.flowable.common.constant.TaskConstants;
import com.ruoyi.flowable.common.enums.FlowComment;
import com.ruoyi.flowable.common.enums.ProcessStatus;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEntityEvent;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.delegate.event.AbstractFlowableEngineEventListener;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Flowable 全局监听器
 *
 * @author konbai
 * @since 2023/3/8 22:45
 */
@Component
public class GlobalEventListener extends AbstractFlowableEngineEventListener {

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    /**
     * 流程结束监听器
     */
    @Override
    protected void processCompleted(FlowableEngineEntityEvent event) {
        String processInstanceId = event.getProcessInstanceId();
        Object variable = runtimeService.getVariable(processInstanceId, ProcessConstants.PROCESS_STATUS_KEY);
        ProcessStatus status = ProcessStatus.getProcessStatus(Convert.toStr(variable));
        if (ObjectUtil.isNotNull(status) && ProcessStatus.RUNNING == status) {
            runtimeService.setVariable(processInstanceId, ProcessConstants.PROCESS_STATUS_KEY, ProcessStatus.COMPLETED.getStatus());
        }
        super.processCompleted(event);
    }

    /**
     * 任务创建事件：流程流转到新节点、生成待办任务时触发
     */
    @Override
    protected void taskCreated(FlowableEngineEntityEvent event) {
        // 事件实体就是刚创建的任务
        if (!(event.getEntity() instanceof TaskEntity)) {
            return;
        }
        TaskEntity task = (TaskEntity) event.getEntity();
        String assignee = task.getAssignee();
        if (StrUtil.isBlank(task.getExecutionId())) {
            return; // 独立任务（影子任务），跳过
        }

        // 判断当前节点审批人是否为需要转派的人
        if (StrUtil.equals(assignee, "2076856012164575233")) {
            // 计算目标审批人（见下方 resolveTargetAssignee 说明）
            String targetUserId = "3";
            if (StrUtil.isBlank(targetUserId) || StrUtil.equals(targetUserId, assignee)) {
                return; // 没有可转派目标则不处理，避免死循环
            }

            // ① 改派：把审批人从 1 改成目标人
            taskService.setAssignee(task.getId(), targetUserId);

            // ② 写流程记录（转办类型），前端“流转记录”即可看到
            String comment = StrUtil.format("系统自动转派：原审批人[{}] -> 新审批人[{}]", assignee, targetUserId);
            taskService.addComment(task.getId(), task.getProcessInstanceId(),
                FlowComment.TRANSFER.getType(), comment);
        }

        if (StrUtil.equals(assignee, "2076856045412823041")) {
            taskService.setAssignee(task.getId(), null);
            taskService.addCandidateUser(task.getId(), "2076856045412823041");
            taskService.addCandidateUser(task.getId(), "4");
            // 打标记：记录这是或签组，供完成时识别未办理人
            taskService.setVariableLocal(task.getId(), "orSignGroup", "2076856045412823041,4");
        }

        /*
         * 这里可以做根据配置的信息动态获取当前节点的审批人
         * 比如发起人的上级领导
         * */
        // 获取当前实例的发起人（流程启动时以变量 initiator 存储，值为发起人的用户ID）
        String processInstanceId = task.getProcessInstanceId();
        String initiator = Convert.toStr(runtimeService.getVariable(processInstanceId, TaskConstants.PROCESS_INITIATOR));
        // initiator 即为当前实例发起人的用户ID，后续可据此查询上级领导等信息


        super.taskCreated(event);
    }

}
