package com.ruoyi.flowable.flow;

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
     * 流转顺序（从 1 开始，按预演经过先后排序）
     */
    private Integer sortOrder;

    /**
     * 层级（从 0 开始，同层级节点为并行关系）
     */
    private Integer level;

    /**
     * 是否与同层级其它节点并行
     */
    private Boolean parallel;

    /**
     * 节点类型：startEvent（开始）/ userTask（用户任务）/ endEvent（结束）
     */
    private String nodeType;

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
