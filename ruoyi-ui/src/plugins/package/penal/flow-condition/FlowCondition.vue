<template>
  <div class="panel-tab__content">
    <el-form :model="flowConditionForm" label-width="90px" size="mini" @submit.native.prevent>
      <!-- 是否默认流程（对应默认流转路径） -->
      <el-form-item label="是否默认流程">
        <el-switch v-model="isDefaultFlow" @change="onDefaultFlowChange" />
      </el-form-item>

      <template v-if="!isDefaultFlow">
        <el-form-item label="流转类型">
          <el-select v-model="flowConditionForm.type" @change="updateFlowType">
            <el-option label="普通流转路径" value="normal" />
            <el-option label="条件流转路径" value="condition" />
          </el-select>
        </el-form-item>
        <el-form-item label="条件格式" v-if="flowConditionForm.type === 'condition'" key="condition">
          <el-select v-model="flowConditionForm.conditionType" @change="onConditionTypeChange">
            <el-option label="条件组" value="group" />
            <el-option label="表达式" value="expression" />
            <el-option label="脚本" value="script" />
          </el-select>
        </el-form-item>

        <!-- 可视化条件组 -->
        <template v-if="flowConditionForm.type === 'condition' && flowConditionForm.conditionType === 'group'">
          <el-form-item label="流程规则" key="group">
            <condition-group-builder
              v-model="conditionModel"
              :fields="conditionFields"
              @change="updateGroupCondition"
            />
          </el-form-item>
          <el-form-item label="表达式预览" key="group-preview">
            <el-input
              type="textarea"
              :autosize="{ minRows: 2, maxRows: 6 }"
              :value="groupExpressionPreview"
              readonly
            />
          </el-form-item>
        </template>

        <el-form-item
          label="表达式"
          v-if="flowConditionForm.type === 'condition' && flowConditionForm.conditionType === 'expression'"
          key="express"
        >
          <el-input v-model="flowConditionForm.body" clearable @change="updateFlowCondition" />
        </el-form-item>
        <template v-if="flowConditionForm.type === 'condition' && flowConditionForm.conditionType === 'script'">
          <el-form-item label="脚本语言" key="language">
            <el-input v-model="flowConditionForm.language" clearable @change="updateFlowCondition" />
          </el-form-item>
          <el-form-item label="脚本类型" key="scriptType">
            <el-select v-model="flowConditionForm.scriptType">
              <el-option label="内联脚本" value="inlineScript" />
              <el-option label="外部脚本" value="externalScript" />
            </el-select>
          </el-form-item>
          <el-form-item label="脚本" v-if="flowConditionForm.scriptType === 'inlineScript'" key="body">
            <el-input v-model="flowConditionForm.body" type="textarea" clearable @change="updateFlowCondition" />
          </el-form-item>
          <el-form-item label="资源地址" v-if="flowConditionForm.scriptType === 'externalScript'" key="resource">
            <el-input v-model="flowConditionForm.resource" clearable @change="updateFlowCondition" />
          </el-form-item>
        </template>
      </template>
    </el-form>
  </div>
</template>

<script>
import ConditionGroupBuilder from "./ConditionGroupBuilder";
import { compileConditionModel, createEmptyModel } from "./conditionCompiler";

// 存储结构化条件组数据的扩展属性名
const CONDITION_GROUP_PROP = "conditionGroups";

export default {
  name: "FlowCondition",
  components: { ConditionGroupBuilder },
  props: {
    businessObject: Object,
    type: String
  },
  inject: {
    prefix: { default: "flowable" }
  },
  data() {
    return {
      flowConditionForm: {},
      isDefaultFlow: false,
      conditionModel: createEmptyModel(),
      // 字段定义：可替换为「当前流程绑定表单」的字段列表。
      // type: text | number | enum | boolean；multiple: 是否多选；options: 枚举可选值
      conditionFields: [
        { label: "公司主体", value: "companyEntity", type: "text" },
        { label: "合同金额", value: "contractAmount", type: "number", precision: 2 },
        {
          label: "签名类型",
          value: "signType",
          type: "enum",
          multiple: true,
          options: [
            { label: "电子签", value: "电子签" },
            { label: "纸质签", value: "纸质签" }
          ]
        }
      ]
    };
  },
  computed: {
    groupExpressionPreview() {
      return compileConditionModel(this.conditionModel) || "（无有效条件）";
    }
  },
  watch: {
    businessObject: {
      immediate: true,
      handler() {
        this.$nextTick(() => this.resetFlowCondition());
      }
    }
  },
  methods: {
    resetFlowCondition() {
      this.bpmnElement = window.bpmnInstances.bpmnElement;
      this.bpmnElementSource = this.bpmnElement.source;
      this.bpmnElementSourceRef = this.bpmnElement.businessObject.sourceRef;
      if (this.bpmnElementSourceRef && this.bpmnElementSourceRef.default && this.bpmnElementSourceRef.default.id === this.bpmnElement.id) {
        // 默认
        this.isDefaultFlow = true;
        this.flowConditionForm = { type: "default" };
      } else if (!this.bpmnElement.businessObject.conditionExpression) {
        // 普通
        this.isDefaultFlow = false;
        this.flowConditionForm = { type: "normal" };
      } else {
        // 带条件
        this.isDefaultFlow = false;
        const conditionExpression = this.bpmnElement.businessObject.conditionExpression;
        this.flowConditionForm = { ...conditionExpression, type: "condition" };
        // resource 可直接标识 是否是外部资源脚本
        if (this.flowConditionForm.resource) {
          this.$set(this.flowConditionForm, "conditionType", "script");
          this.$set(this.flowConditionForm, "scriptType", "externalScript");
          return;
        }
        if (conditionExpression.language) {
          this.$set(this.flowConditionForm, "conditionType", "script");
          this.$set(this.flowConditionForm, "scriptType", "inlineScript");
          return;
        }
        // 尝试从扩展属性回显结构化条件组
        const savedModel = this.readConditionGroupModel();
        if (savedModel) {
          this.conditionModel = savedModel;
          this.$set(this.flowConditionForm, "conditionType", "group");
          return;
        }
        this.$set(this.flowConditionForm, "conditionType", "expression");
      }
    },
    onDefaultFlowChange(val) {
      this.updateFlowType(val ? "default" : "normal");
      if (!val) {
        // 关闭默认流程后，回到普通类型
        this.$set(this.flowConditionForm, "type", "normal");
      }
    },
    onConditionTypeChange(val) {
      if (val === "group") {
        // 切换到条件组时，若已有模型则立即编译
        this.updateGroupCondition(this.conditionModel);
      }
    },
    updateFlowType(flowType) {
      // 正常条件类
      if (flowType === "condition") {
        this.flowConditionRef = window.bpmnInstances.moddle.create("bpmn:FormalExpression");
        window.bpmnInstances.modeling.updateProperties(this.bpmnElement, {
          conditionExpression: this.flowConditionRef
        });
        return;
      }
      // 默认路径
      if (flowType === "default") {
        window.bpmnInstances.modeling.updateProperties(this.bpmnElement, {
          conditionExpression: null
        });
        window.bpmnInstances.modeling.updateProperties(this.bpmnElementSource, {
          default: this.bpmnElement
        });
        return;
      }
      // 正常路径，如果来源节点的默认路径是当前连线时，清除父元素的默认路径配置
      if (this.bpmnElementSourceRef.default && this.bpmnElementSourceRef.default.id === this.bpmnElement.id) {
        window.bpmnInstances.modeling.updateProperties(this.bpmnElementSource, {
          default: null
        });
      }
      window.bpmnInstances.modeling.updateProperties(this.bpmnElement, {
        conditionExpression: null
      });
    },
    // 可视化条件组变更 -> 编译表达式写入 body + 持久化结构化数据
    updateGroupCondition(model) {
      this.conditionModel = model;
      const body = compileConditionModel(model);
      const condition = window.bpmnInstances.moddle.create("bpmn:FormalExpression", { body });
      window.bpmnInstances.modeling.updateProperties(this.bpmnElement, { conditionExpression: condition });
      // 结构化数据持久化到扩展属性以支持回显
      this.writeConditionGroupModel(model);
    },
    updateFlowCondition() {
      let { conditionType, scriptType, body, resource, language } = this.flowConditionForm;
      let condition;
      if (conditionType === "expression") {
        condition = window.bpmnInstances.moddle.create("bpmn:FormalExpression", { body });
      } else {
        if (scriptType === "inlineScript") {
          condition = window.bpmnInstances.moddle.create("bpmn:FormalExpression", { body, language });
          this.$set(this.flowConditionForm, "resource", "");
        } else {
          this.$set(this.flowConditionForm, "body", "");
          condition = window.bpmnInstances.moddle.create("bpmn:FormalExpression", { resource, language });
        }
      }
      window.bpmnInstances.modeling.updateProperties(this.bpmnElement, { conditionExpression: condition });
    },

    // ---------- 扩展属性读写（用于结构化条件组回显） ----------
    readConditionGroupModel() {
      try {
        const values = this.bpmnElement.businessObject?.extensionElements?.values ?? [];
        const propsType = `${this.prefix}:Properties`;
        for (const ex of values) {
          if (ex.$type === propsType && Array.isArray(ex.values)) {
            const target = ex.values.find(p => p.name === CONDITION_GROUP_PROP);
            if (target && target.value) {
              const parsed = JSON.parse(target.value);
              if (parsed && Array.isArray(parsed.groups)) return parsed;
            }
          }
        }
      } catch (e) {
        console.warn("解析条件组数据失败：", e);
      }
      return null;
    },
    writeConditionGroupModel(model) {
      const moddle = window.bpmnInstances.moddle;
      const propsType = `${this.prefix}:Properties`;
      const propType = `${this.prefix}:Property`;
      const jsonValue = JSON.stringify(model);

      const bo = this.bpmnElement.businessObject;
      const existing = bo.extensionElements?.values ?? [];
      // 保留非 Properties 的其他扩展
      const otherExtensions = existing.filter(ex => ex.$type !== propsType);
      // 合并已有 Properties 中的其他属性（排除条件组属性）
      const otherProps = [];
      existing
        .filter(ex => ex.$type === propsType && Array.isArray(ex.values))
        .forEach(ex => {
          ex.values.forEach(p => {
            if (p.name !== CONDITION_GROUP_PROP) otherProps.push(p);
          });
        });

      const groupProp = moddle.create(propType, { name: CONDITION_GROUP_PROP, value: jsonValue });
      const propertiesObject = moddle.create(propsType, { values: otherProps.concat([groupProp]) });
      const extensions = moddle.create("bpmn:ExtensionElements", {
        values: otherExtensions.concat([propertiesObject])
      });
      window.bpmnInstances.modeling.updateProperties(this.bpmnElement, { extensionElements: extensions });
    }
  },
  beforeDestroy() {
    this.bpmnElement = null;
    this.bpmnElementSource = null;
    this.bpmnElementSourceRef = null;
  }
};
</script>
