package com.ruoyi.workflow.service;

import com.ruoyi.workflow.domain.bo.WfTaskBo;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.engine.runtime.ProcessInstance;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * @author KonBAI
 * @createTime 2022/3/10 00:12
 */
public interface IWfTaskService {

    /**
     * 审批任务
     *
     * @param task 请求实体参数
     */
    void complete(WfTaskBo task);

    /**
     * 拒绝任务
     *
     * @param taskBo
     */
    void taskReject(WfTaskBo taskBo);


    /**
     * 退回任务
     *
     * @param bo 请求实体参数
     */
    void taskReturn(WfTaskBo bo);

    /**
     * 获取所有可回退的节点
     *
     * @param bo
     * @return
     */
    List<FlowElement> findReturnTaskList(WfTaskBo bo);

    /**
     * 删除任务
     *
     * @param bo 请求实体参数
     */
    void deleteTask(WfTaskBo bo);

    /**
     * 认领/签收任务
     *
     * @param bo 请求实体参数
     */
    void claim(WfTaskBo bo);

    /**
     * 取消认领/签收任务
     *
     * @param bo 请求实体参数
     */
    void unClaim(WfTaskBo bo);

    /**
     * 委派任务
     *
     * @param bo 请求实体参数
     */
    void delegateTask(WfTaskBo bo);


    /**
     * 转办任务
     *
     * @param bo 请求实体参数
     */
    void transferTask(WfTaskBo bo);

    /**
     * 加签：将指定用户动态加入当前多实例（会签/或签）节点，继承该节点的会签/或签形式。
     * 当前用户可继续审批；或签场景下当前用户完成后，加签用户的任务将由引擎自动结束。
     *
     * @param bo 请求实体参数（taskId 当前任务、userId 加签人，多个以逗号分隔）
     */
    void addMultiInstance(WfTaskBo bo);

    /**
     * 取消申请
     * @param bo
     * @return
     */
    void stopProcess(WfTaskBo bo);

    /**
     * 撤回流程
     * @param bo
     * @return
     */
    void revokeProcess(WfTaskBo bo);

    /**
     * 获取流程过程图
     * @param processId
     * @return
     */
    InputStream diagram(String processId);

    /**
     * 获取流程变量
     * @param taskId 任务ID
     * @return 流程变量
     */
    Map<String, Object> getProcessVariables(String taskId);

    /**
     * 启动第一个任务
     * @param processInstance 流程实例
     * @param variables 流程参数
     */
    void startFirstTask(ProcessInstance processInstance, Map<String, Object> variables);
}
