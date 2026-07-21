package com.ruoyi.flowable.delegate;

import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 任务超时处理委托类（配合边界定时事件 Boundary Timer Event 使用）。
 * <p>
 * 该 Bean 由 BPMN 服务任务通过 {@code flowable:delegateExpression="${timeoutHandleDelegate}"} 调用，
 * 用于在用户任务超时后执行"自动处理"逻辑（发提醒 / 记录升级 / 通知等）。
 * <p>
 * 边界定时事件负责"何时触发"（在 BPMN 里声明），本类负责"触发后做什么"。
 * 两者配合即可实现任务超时监控与自动处理，无需手写 TimerJobService / JobHandler。
 * <p>
 * 通过流程变量 {@code timeoutAction} 区分处理类型：
 * <ul>
 *     <li>{@code remind}：非中断提醒，原审批任务继续保留</li>
 *     <li>{@code escalate}：中断升级，原审批任务已被取消，流程流转到升级节点</li>
 * </ul>
 * <p>
 * 注意：本类以 delegateExpression 方式复用同一个 Spring 单例 Bean，因此
 * <b>不使用字段注入的 {@code Expression} 参数</b>（并发下不安全），
 * 处理类型统一从执行上下文的流程变量读取。
 *
 * @author kiro
 */
@Component("timeoutHandleDelegate")
@Slf4j
public class TimeoutHandleDelegate implements JavaDelegate {

    /** 处理类型流程变量名 */
    public static final String ACTION_VAR = "timeoutAction";
    /** 处理类型：非中断提醒 */
    public static final String ACTION_REMIND = "remind";
    /** 处理类型：中断升级 */
    public static final String ACTION_ESCALATE = "escalate";

    /** 记录最近一次超时处理时间的流程变量名（便于审计/查询） */
    public static final String LAST_TIMEOUT_TIME_VAR = "lastTimeoutHandleTime";

    @Override
    public void execute(DelegateExecution execution) {
        String processInstanceId = execution.getProcessInstanceId();
        String activityId = execution.getCurrentActivityId();
        // 处理类型判定优先级：
        // 1) 显式流程变量 timeoutAction（remind/escalate）
        // 2) 缺省时按当前服务任务节点 id 约定（含 "remind" 视为提醒）
        // 3) 兜底按"升级"处理，保证误配置时仍走安全的中断分支
        Object actionObj = execution.getVariable(ACTION_VAR);
        String action;
        if (actionObj != null) {
            action = actionObj.toString();
        } else if (activityId != null && activityId.toLowerCase().contains(ACTION_REMIND)) {
            action = ACTION_REMIND;
        } else {
            action = ACTION_ESCALATE;
        }

        switch (action) {
            case ACTION_REMIND:
                handleRemind(execution, processInstanceId, activityId);
                break;
            case ACTION_ESCALATE:
            default:
                handleEscalate(execution, processInstanceId, activityId);
                break;
        }

        // 记录处理时间，供后续审计或前端展示
        execution.setVariable(LAST_TIMEOUT_TIME_VAR, new Date());
    }

    /**
     * 非中断提醒：任务临近截止但尚未超时，发送催办提醒。
     * 此时原用户任务未被取消，仍在等待审批人处理。
     */
    private void handleRemind(DelegateExecution execution, String processInstanceId, String activityId) {
        log.info("[任务超时-提醒] 流程实例[{}] 节点[{}] 触发临期提醒，任务仍在办理中，发送催办通知",
            processInstanceId, activityId);
        // TODO 接入实际通知渠道（站内信/邮件/短信）：提醒当前审批人尽快处理
    }

    /**
     * 中断升级/自动处理：任务已超时，原用户任务被边界中断事件取消，
     * 流程流转到本服务任务执行自动处理（记录、通知、后续升级到上级节点）。
     */
    private void handleEscalate(DelegateExecution execution, String processInstanceId, String activityId) {
        log.warn("[任务超时-升级] 流程实例[{}] 节点[{}] 审批任务已超时，执行自动升级处理",
            processInstanceId, activityId);
        // 标记超时，供升级节点或后续逻辑识别
        execution.setVariable("processTimeout", Boolean.TRUE);
        // TODO 接入实际业务：通知上级领导、记录超时审计、或按规则自动通过/驳回
    }
}
