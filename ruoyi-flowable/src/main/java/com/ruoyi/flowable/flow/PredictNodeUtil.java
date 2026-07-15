package com.ruoyi.flowable.flow;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.Expression;
import com.ruoyi.common.utils.StringUtils;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.*;

import java.util.*;

/**
 * 流程全路径预演工具
 * <p>
 * 在流程发起时，根据发起表单变量，静态解析 BpmnModel，
 * 从开始节点一路走到结束节点，收集所有 <b>真正会执行到</b> 的用户任务节点（按流转顺序）。
 * 排他网关只走命中条件的一条分支，并行网关全部走，不会执行的分支不会被收集。
 * <p>
 * 使用前提：分支条件只依赖发起时的表单变量，不依赖后续审批人中途填写的变量。
 *
 * @author kiro
 */
public class PredictNodeUtil {

    /**
     * 预演流程，收集会执行到的用户任务节点（按顺序、已去重）。
     *
     * @param bpmnModel 流程模型
     * @param variables 发起时的流程变量
     * @return 会执行到的用户任务节点列表
     */
    public static List<UserTask> predict(BpmnModel bpmnModel, Map<String, Object> variables) {
        List<UserTask> result = new ArrayList<>();
        if (bpmnModel == null) {
            return result;
        }
        Map<String, Object> vars = variables == null ? new HashMap<>() : variables;
        Process process = bpmnModel.getMainProcess();
        Collection<FlowElement> flowElements = process.getFlowElements();
        StartEvent startEvent = findStartEvent(flowElements);
        if (startEvent == null) {
            return result;
        }
        // 已收集的用户任务 ID，避免重复
        Set<String> collectedTaskIds = new HashSet<>();
        // 已经走过的连线 ID，避免循环（回退/驳回）导致的死递归
        Set<String> visitedSequenceFlows = new HashSet<>();
        walk(startEvent, flowElements, vars, result, collectedTaskIds, visitedSequenceFlows);
        return result;
    }

    /**
     * 展示节点类型：开始事件
     */
    public static final String TYPE_START = "startEvent";
    /**
     * 展示节点类型：用户任务
     */
    public static final String TYPE_USER_TASK = "userTask";
    /**
     * 展示节点类型：结束事件
     */
    public static final String TYPE_END = "endEvent";

    /**
     * 预演展示节点，携带层级信息用于体现并行关系。
     */
    public static class PredictNode {
        /**
         * 节点 Key（BPMN 元素 id）
         */
        private final String nodeKey;
        /**
         * 节点名称
         */
        private final String nodeName;
        /**
         * 节点类型：startEvent / userTask / endEvent
         */
        private final String nodeType;
        /**
         * 是否为多实例（会签/或签）节点
         */
        private final boolean multiInstance;
        /**
         * 层级（从 0 开始，同层级节点为并行关系）
         */
        private int level;
        /**
         * 是否与同层级其它节点并行
         */
        private boolean parallel;

        public PredictNode(String nodeKey, String nodeName, String nodeType, boolean multiInstance) {
            this.nodeKey = nodeKey;
            this.nodeName = nodeName;
            this.nodeType = nodeType;
            this.multiInstance = multiInstance;
        }

        public String getNodeKey() {
            return nodeKey;
        }

        public String getNodeName() {
            return nodeName;
        }

        public String getNodeType() {
            return nodeType;
        }

        public boolean isMultiInstance() {
            return multiInstance;
        }

        public int getLevel() {
            return level;
        }

        public boolean isParallel() {
            return parallel;
        }
    }

    /**
     * 预演流程线：收集会执行到的展示节点（开始事件 / 用户任务 / 结束事件），
     * 并按“从开始节点到该节点的最长路径”计算层级(level)，同层级节点即为并行关系。
     *
     * @param bpmnModel 流程模型
     * @param variables 发起时的流程变量
     * @return 含层级的展示节点列表（按 level 升序、同层保持经过顺序）
     */
    public static List<PredictNode> predictLine(BpmnModel bpmnModel, Map<String, Object> variables) {
        if (bpmnModel == null) {
            return new ArrayList<>();
        }
        Map<String, Object> vars = variables == null ? new HashMap<>() : variables;
        Process process = bpmnModel.getMainProcess();
        Collection<FlowElement> flowElements = process.getFlowElements();
        StartEvent startEvent = findStartEvent(flowElements);
        if (startEvent == null) {
            return new ArrayList<>();
        }
        LineContext ctx = new LineContext(vars, flowElements);
        // 开始节点作为首个展示节点
        ctx.nodeMap.put(startEvent.getId(),
            new PredictNode(startEvent.getId(), startEvent.getName(), TYPE_START, false));
        walkLine(startEvent, startEvent.getId(), ctx);
        return buildLeveledNodes(ctx);
    }

    /**
     * 分层预演的遍历上下文。
     */
    private static class LineContext {
        final Map<String, Object> variables;
        final Collection<FlowElement> flowElements;
        /**
         * 展示节点，保持首次经过顺序
         */
        final Map<String, PredictNode> nodeMap = new LinkedHashMap<>();
        /**
         * 展示节点反向边：to -> sources，用于层级计算
         */
        final Map<String, Set<String>> reverseEdges = new HashMap<>();
        /**
         * 已走过的连线 id，去重与循环检测
         */
        final Set<String> visitedFlows = new HashSet<>();

        LineContext(Map<String, Object> variables, Collection<FlowElement> flowElements) {
            this.variables = variables;
            this.flowElements = flowElements;
        }
    }

    /**
     * 分层预演遍历：在收集展示节点的同时记录展示节点之间的有向边。
     *
     * @param current       当前流程元素
     * @param fromDisplayId 到达当前元素前最近经过的展示节点 id
     * @param ctx           遍历上下文
     */
    private static void walkLine(FlowElement current, String fromDisplayId, LineContext ctx) {
        if (current == null) {
            return;
        }
        // 结束节点：若在子流程内则跳到子流程节点继续，否则终止
        if (current instanceof EndEvent) {
            SubProcess subProcess = getSubProcess(ctx.flowElements, current);
            if (subProcess != null) {
                current = subProcess;
            } else {
                return;
            }
        }
        List<SequenceFlow> outgoingFlows = getOutgoingFlows(current);
        if (outgoingFlows == null || outgoingFlows.isEmpty()) {
            return;
        }
        List<SequenceFlow> selectedFlows = chooseOutgoingFlows(current, outgoingFlows, ctx.variables);
        if (selectedFlows.isEmpty()) {
            throw new RuntimeException("流程节点配置错误,无后续节点流转");
        }
        if (!(current instanceof ParallelGateway || current instanceof InclusiveGateway) && selectedFlows.size() > 1) {
            throw new RuntimeException("流程节点配置错误,节点后续流转有多个，不符合条件");
        }
        for (SequenceFlow sequenceFlow : selectedFlows) {
            // 防止循环
            if (!ctx.visitedFlows.add(sequenceFlow.getId())) {
                continue;
            }
            FlowElement next = getFlowElementById(sequenceFlow.getTargetRef(), ctx.flowElements);
            if (next == null) {
                continue;
            }
            if (next instanceof UserTask) {
                boolean isNew = !ctx.nodeMap.containsKey(next.getId());
                if (isNew) {
                    ctx.nodeMap.put(next.getId(), new PredictNode(next.getId(), next.getName(),
                        TYPE_USER_TASK, ((UserTask) next).getLoopCharacteristics() != null));
                }
                addEdge(ctx, fromDisplayId, next.getId());
                // 已存在节点其后续已遍历过，无需重复
                if (isNew) {
                    walkLine(next, next.getId(), ctx);
                }
            } else if (next instanceof EndEvent && next.getParentContainer() instanceof Process) {
                // 主流程结束节点：记录为展示节点（子流程结束节点不展示）
                boolean isNew = !ctx.nodeMap.containsKey(next.getId());
                if (isNew) {
                    ctx.nodeMap.put(next.getId(),
                        new PredictNode(next.getId(), next.getName(), TYPE_END, false));
                }
                addEdge(ctx, fromDisplayId, next.getId());
            } else {
                // 网关、服务任务、子流程开始/结束节点等中间节点，透传最近展示节点继续遍历
                walkLine(next, fromDisplayId, ctx);
            }
        }
    }

    private static void addEdge(LineContext ctx, String from, String to) {
        if (from == null || to == null || from.equals(to)) {
            return;
        }
        ctx.reverseEdges.computeIfAbsent(to, k -> new LinkedHashSet<>()).add(from);
    }

    /**
     * 计算层级并标记并行，输出有序展示节点列表。
     */
    private static List<PredictNode> buildLeveledNodes(LineContext ctx) {
        Map<String, Integer> levelCache = new HashMap<>();
        for (String nodeId : ctx.nodeMap.keySet()) {
            computeLevel(nodeId, ctx.reverseEdges, levelCache, new HashSet<>());
        }
        Map<Integer, Integer> levelCount = new HashMap<>();
        for (PredictNode node : ctx.nodeMap.values()) {
            node.level = levelCache.getOrDefault(node.getNodeKey(), 0);
            levelCount.merge(node.level, 1, Integer::sum);
        }
//        for (PredictNode node : ctx.nodeMap.values()) {
//            node.parallel = levelCount.getOrDefault(node.level, 0) > 1;
//        }
        List<PredictNode> ordered = new ArrayList<>(ctx.nodeMap.values());
        ordered.sort(Comparator.comparingInt(PredictNode::getLevel));
        return ordered;
    }

    /**
     * 记忆化计算节点层级：level = 前驱节点层级最大值 + 1，无前驱为 0。
     * 使用 visiting 集合检测环，遇环忽略该前驱贡献。
     */
    private static int computeLevel(String nodeId, Map<String, Set<String>> reverseEdges,
                                    Map<String, Integer> levelCache, Set<String> visiting) {
        Integer cached = levelCache.get(nodeId);
        if (cached != null) {
            return cached;
        }
        Set<String> preds = reverseEdges.get(nodeId);
        if (preds == null || preds.isEmpty()) {
            levelCache.put(nodeId, 0);
            return 0;
        }
        if (!visiting.add(nodeId)) {
            return 0;
        }
        int max = 0;
        for (String pred : preds) {
            max = Math.max(max, computeLevel(pred, reverseEdges, levelCache, visiting) + 1);
        }
        visiting.remove(nodeId);
        levelCache.put(nodeId, max);
        return max;
    }

    /**
     * 深度遍历流程节点。
     *
     * @param current              当前节点
     * @param flowElements         主流程节点集合
     * @param variables            流程变量
     * @param result               结果收集（有序）
     * @param collectedTaskIds     已收集用户任务 ID
     * @param visitedSequenceFlows 已走过的连线 ID
     */
    private static void walk(FlowElement current, Collection<FlowElement> flowElements, Map<String, Object> variables,
                             List<UserTask> result, Set<String> collectedTaskIds, Set<String> visitedSequenceFlows) {
        if (current == null) {
            return;
        }
        // 结束节点：若在子流程内则跳到子流程节点继续，否则终止
        if (current instanceof EndEvent) {
            SubProcess subProcess = getSubProcess(flowElements, current);
            if (subProcess != null) {
                current = subProcess;
            } else {
                return;
            }
        }

        List<SequenceFlow> outgoingFlows = getOutgoingFlows(current);
        if (outgoingFlows == null || outgoingFlows.isEmpty()) {
            return;
        }

        // 根据节点类型挑选要走的出线
        List<SequenceFlow> selectedFlows = chooseOutgoingFlows(current, outgoingFlows, variables);

        if (selectedFlows.isEmpty()) {
            throw new RuntimeException("流程节点配置错误,无后续节点流转");
        }

        if (!(current instanceof ParallelGateway || current instanceof InclusiveGateway) && selectedFlows.size() > 1) {
            throw new RuntimeException("流程节点配置错误,节点后续流转有多个，不符合条件");
        }

        for (SequenceFlow sequenceFlow : selectedFlows) {
            // 防止循环
            if (visitedSequenceFlows.contains(sequenceFlow.getId())) {
                continue;
            }
            visitedSequenceFlows.add(sequenceFlow.getId());

            String targetRef = sequenceFlow.getTargetRef();
            FlowElement next = getFlowElementById(targetRef, flowElements);
            if (next == null) {
                continue;
            }

            if (next instanceof UserTask) {
                // 收集用户任务，然后继续往后走
                if (collectedTaskIds.add(next.getId())) {
                    result.add((UserTask) next);
                }
                walk(next, flowElements, variables, result, collectedTaskIds, visitedSequenceFlows);
            } else {
                // 网关、服务任务、子流程开始节点、结束节点等，继续递归
                walk(next, flowElements, variables, result, collectedTaskIds, visitedSequenceFlows);
            }
        }
    }

    /**
     * 根据节点类型选择要走的出线。
     * <ul>
     *     <li>排他网关：按顺序取第一条条件为 true 的，全不满足则取默认流</li>
     *     <li>包含网关：取所有条件为 true 的，全不满足则取默认流</li>
     *     <li>并行网关：全部出线（并行语义忽略条件）</li>
     *     <li>其它节点(如用户任务)多出口：排他式，只走第一条满足条件的，全不满足走默认流</li>
     * </ul>
     */
    private static List<SequenceFlow> chooseOutgoingFlows(FlowElement current, List<SequenceFlow> outgoingFlows,
                                                          Map<String, Object> variables) {
        // 并行网关：全部走
        if (current instanceof ParallelGateway) {
            return outgoingFlows;
        }
        // 排他网关：只走一条
        if (current instanceof ExclusiveGateway) {
            String defaultFlow = ((ExclusiveGateway) current).getDefaultFlow();
            SequenceFlow matched = matchFirst(outgoingFlows, defaultFlow, variables);
            return matched == null ? Collections.emptyList() : Collections.singletonList(matched);
        }
        // 包含网关：走所有满足条件的
        if (current instanceof InclusiveGateway) {
            String defaultFlow = ((InclusiveGateway) current).getDefaultFlow();
            return matchAll(outgoingFlows, defaultFlow, variables);
        }
        // 其它节点：对每条出线判断条件（顺序流一般无条件，视为可走）
        List<SequenceFlow> selected = new ArrayList<>();
        List<SequenceFlow> defaultSelected = new ArrayList<>();
        for (SequenceFlow flow : outgoingFlows) {
            if (StringUtils.isEmpty(flow.getConditionExpression())) {
                defaultSelected.add(flow);
            } else if (conditionPass(flow, variables)) {
                selected.add(flow);
            }
        }
        return selected.isEmpty() ? defaultSelected : selected;
    }

    /**
     * 排他网关：取第一条命中的出线，全不命中取默认流。
     */
    private static SequenceFlow matchFirst(List<SequenceFlow> outgoingFlows, String defaultFlowId, Map<String, Object> variables) {
        SequenceFlow defaultFlow = null;
        for (SequenceFlow flow : outgoingFlows) {
            if (defaultFlowId != null && defaultFlowId.equals(flow.getId())) {
                defaultFlow = flow;
                continue;
            }
            if (conditionPass(flow, variables)) {
                return flow;
            }
        }
        return defaultFlow;
    }

    /**
     * 包含网关：取所有命中的出线，全不命中取默认流。
     */
    private static List<SequenceFlow> matchAll(List<SequenceFlow> outgoingFlows, String defaultFlowId, Map<String, Object> variables) {
        List<SequenceFlow> matched = new ArrayList<>();
        SequenceFlow defaultFlow = null;
        for (SequenceFlow flow : outgoingFlows) {
            if (defaultFlowId != null && defaultFlowId.equals(flow.getId())) {
                defaultFlow = flow;
                continue;
            }
            if (conditionPass(flow, variables)) {
                matched.add(flow);
            }
        }
        if (matched.isEmpty() && defaultFlow != null) {
            matched.add(defaultFlow);
        }
        return matched;
    }

    /**
     * 判断一条连线的条件是否成立。无条件表达式视为成立。
     */
    private static boolean conditionPass(SequenceFlow sequenceFlow, Map<String, Object> variables) {
        String expression = sequenceFlow.getConditionExpression();
        if (expression == null || expression.trim().isEmpty()) {
            return true;
        }
        return evaluate(variables, expression);
    }

    /**
     * 计算条件表达式（形如 ${amount > 1000}），计算异常时按不通过处理，避免中断预演。
     */
    private static boolean evaluate(Map<String, Object> variables, String expression) {
        try {
            int start = expression.lastIndexOf("{");
            int end = expression.lastIndexOf("}");
            String realExpr = (start >= 0 && end > start) ? expression.substring(start + 1, end) : expression;
            Expression exp = AviatorEvaluator.compile(realExpr, true);
            Object execute = exp.execute(variables);
            return Boolean.parseBoolean(String.valueOf(execute));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取节点的出口连线。
     */
    private static List<SequenceFlow> getOutgoingFlows(FlowElement flowElement) {
        if (flowElement instanceof FlowNode) {
            return ((FlowNode) flowElement).getOutgoingFlows();
        }
        return Collections.emptyList();
    }

    /**
     * 从开始节点集合中找开始节点。
     */
    private static StartEvent findStartEvent(Collection<FlowElement> flowElements) {
        for (FlowElement flowElement : flowElements) {
            if (flowElement instanceof StartEvent) {
                return (StartEvent) flowElement;
            }
        }
        return null;
    }

    /**
     * 根据 ID 查询流程节点；若目标是子流程，返回子流程的开始节点。
     */
    private static FlowElement getFlowElementById(String id, Collection<FlowElement> flowElements) {
        for (FlowElement flowElement : flowElements) {
            if (flowElement.getId().equals(id)) {
                if (flowElement instanceof SubProcess) {
                    return findStartEvent(((SubProcess) flowElement).getFlowElements());
                }
                return flowElement;
            }
            if (flowElement instanceof SubProcess) {
                FlowElement inner = getFlowElementById(id, ((SubProcess) flowElement).getFlowElements());
                if (inner != null) {
                    return inner;
                }
            }
        }
        return null;
    }

    /**
     * 判断某节点是否位于某个子流程内，是则返回该子流程。
     */
    private static SubProcess getSubProcess(Collection<FlowElement> flowElements, FlowElement target) {
        for (FlowElement flowElement : flowElements) {
            if (flowElement instanceof SubProcess) {
                for (FlowElement inner : ((SubProcess) flowElement).getFlowElements()) {
                    if (inner.equals(target)) {
                        return (SubProcess) flowElement;
                    }
                }
            }
        }
        return null;
    }
}
