package com.ruoyi.workflow.domain.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 流程预演节点视图对象
 * <p>
 * 表示流程发起时预演确定的、后续会真正执行到的一个用户任务节点。
 *
 * @author kiro
 */
@Data
public class WfNextNodeVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 节点定义 Key（对应 UserTask 的 id）
     */
    private String nodeKey;

    /**
     * 节点名称
     */
    private String nodeName;

    /**
     * 是否为多实例（会签/或签）节点
     */
    private Boolean multiInstance;
}
