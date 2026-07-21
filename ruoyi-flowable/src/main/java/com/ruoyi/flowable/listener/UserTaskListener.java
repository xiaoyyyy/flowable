package com.ruoyi.flowable.listener;

import cn.hutool.core.util.StrUtil;
import com.ruoyi.flowable.utils.ModelUtils;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 用户任务监听器
 * <p>
 * 在流程设计器「通知配置」中配置的“节点到达 / 节点完成”，会在对应用户任务节点上生成
 * {@code <flowable:taskListener event="create|complete" delegateExpression="${userTaskListener}"/>}，
 * 由本监听器在任务创建（create）与完成（complete）事件时读取节点扩展属性并发送通知。
 *
 * @author KonBAI
 * @since 2023/5/13
 */
@Component(value = "userTaskListener")
@Slf4j
public class UserTaskListener implements TaskListener {

    @Resource
    private RepositoryService repositoryService;

    /** 节点到达（任务创建）通知配置属性名 */
    private static final String NOTIFY_ARRIVE_TEMPLATE = "notify_arrive_template";
    private static final String NOTIFY_ARRIVE_RECEIVER = "notify_arrive_receiver";
    /** 节点完成（任务完成）通知配置属性名 */
    private static final String NOTIFY_COMPLETE_TEMPLATE = "notify_complete_template";
    private static final String NOTIFY_COMPLETE_RECEIVER = "notify_complete_receiver";

    @Override
    public void notify(DelegateTask delegateTask) {
        String eventName = delegateTask.getEventName();
        // 仅处理任务创建（节点到达）与任务完成（节点完成）事件
        if (!TaskListener.EVENTNAME_CREATE.equals(eventName)
            && !TaskListener.EVENTNAME_COMPLETE.equals(eventName)) {
            return;
        }

        // 读取当前用户任务节点的扩展属性（通知配置）
        BpmnModel bpmnModel = repositoryService.getBpmnModel(delegateTask.getProcessDefinitionId());
        Map<String, String> props = ModelUtils.getUserTaskProperties(bpmnModel, delegateTask.getTaskDefinitionKey());

        String template;
        String receiver;
        if (TaskListener.EVENTNAME_CREATE.equals(eventName)) {
            template = props.get(NOTIFY_ARRIVE_TEMPLATE);
            receiver = props.get(NOTIFY_ARRIVE_RECEIVER);
        } else {
            template = props.get(NOTIFY_COMPLETE_TEMPLATE);
            receiver = props.get(NOTIFY_COMPLETE_RECEIVER);
        }

        // 未配置通知模板与接收人则跳过
        if (StrUtil.isAllBlank(template, receiver)) {
            return;
        }

        log.info("任务节点通知触发 -> 节点[{}] 事件[{}] 通知模板[{}] 接收人[{}]",
            delegateTask.getName(), eventName, template, receiver);

        // TODO 根据通知模板与接收人发送通知（站内信/邮件/短信等），此处按需接入具体消息服务
        sendNotify(delegateTask, template, receiver);
    }

    /**
     * 发送通知（预留扩展点）
     *
     * @param delegateTask 当前任务
     * @param template     通知模板
     * @param receiver     接收人
     */
    private void sendNotify(DelegateTask delegateTask, String template, String receiver) {
        // TODO 接入实际的消息发送渠道
        log.debug("待发送通知：taskId={}, template={}, receiver={}", delegateTask.getId(), template, receiver);
    }

}
