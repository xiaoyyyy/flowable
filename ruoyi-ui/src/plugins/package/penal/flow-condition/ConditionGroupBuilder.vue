<template>
  <div class="condition-group-builder">
    <div v-for="(group, gIndex) in model.groups" :key="gIndex">
      <!-- 条件组容器 -->
      <div class="cg-group">
        <div class="cg-group__header">
          <span class="cg-group__title">
            <i
              :class="group._collapsed ? 'el-icon-caret-right' : 'el-icon-caret-bottom'"
              class="cg-caret"
              @click="toggleCollapse(group)"
            />
            条件组{{ gIndex + 1 }}
          </span>
          <i class="el-icon-circle-close cg-group__remove" title="删除条件组" @click="removeGroup(gIndex)" />
        </div>

        <div v-show="!group._collapsed" class="cg-group__body">
          <!-- 单个条件 -->
          <div v-for="(cond, cIndex) in group.conditions" :key="cIndex" class="cg-condition">
            <!-- 组内且/或（第一条不显示） -->
            <el-select
              v-if="cIndex > 0"
              v-model="group.relation"
              class="cg-relation"
              size="mini"
              @change="emitChange"
            >
              <el-option label="且" value="and" />
              <el-option label="或" value="or" />
            </el-select>
            <span v-else class="cg-relation cg-relation--placeholder" />

            <!-- 字段 -->
            <el-select
              v-model="cond.field"
              class="cg-field"
              size="mini"
              placeholder="选择字段"
              filterable
              @change="onFieldChange(cond)"
            >
              <el-option
                v-for="f in fields"
                :key="f.value"
                :label="f.label"
                :value="f.value"
              />
            </el-select>

            <!-- 操作符 -->
            <el-select
              v-model="cond.op"
              class="cg-op"
              size="mini"
              placeholder="操作符"
              @change="onOpChange(cond)"
            >
              <el-option
                v-for="o in getOperators(cond.field)"
                :key="o.value"
                :label="o.label"
                :value="o.value"
              />
            </el-select>

            <!-- 值 -->
            <div class="cg-value">
              <template v-if="isNoValueOp(cond.op)">
                <span class="cg-value--none">无需填写</span>
              </template>
              <!-- 枚举 / 多值：多选标签 -->
              <el-select
                v-else-if="needMultiSelect(cond)"
                v-model="cond.value"
                class="cg-value__input"
                size="mini"
                multiple
                filterable
                :allow-create="allowCreate(cond.field)"
                default-first-option
                placeholder="请选择/输入"
                @change="emitChange"
              >
                <el-option
                  v-for="opt in getFieldOptions(cond.field)"
                  :key="optValue(opt)"
                  :label="optLabel(opt)"
                  :value="optValue(opt)"
                />
              </el-select>
              <!-- 数字 -->
              <el-input-number
                v-else-if="cond.valueType === 'number'"
                v-model="cond.value"
                class="cg-value__input"
                size="mini"
                :controls-position="'right'"
                :precision="numberPrecision(cond.field)"
                placeholder="请输入数值"
                @change="emitChange"
              />
              <!-- 布尔 -->
              <el-select
                v-else-if="cond.valueType === 'boolean'"
                v-model="cond.value"
                class="cg-value__input"
                size="mini"
                @change="emitChange"
              >
                <el-option label="是" :value="true" />
                <el-option label="否" :value="false" />
              </el-select>
              <!-- 单值枚举下拉 -->
              <el-select
                v-else-if="hasFieldOptions(cond.field)"
                v-model="cond.value"
                class="cg-value__input"
                size="mini"
                filterable
                :allow-create="allowCreate(cond.field)"
                placeholder="请选择"
                @change="emitChange"
              >
                <el-option
                  v-for="opt in getFieldOptions(cond.field)"
                  :key="optValue(opt)"
                  :label="optLabel(opt)"
                  :value="optValue(opt)"
                />
              </el-select>
              <!-- 文本 -->
              <el-input
                v-else
                v-model="cond.value"
                class="cg-value__input"
                size="mini"
                clearable
                placeholder="请输入"
                @input="emitChange"
              />
            </div>

            <!-- 删除条件 -->
            <i
              class="el-icon-delete cg-condition__remove"
              title="删除条件"
              @click="removeCondition(group, cIndex)"
            />
          </div>

          <!-- 添加条件 -->
          <div class="cg-add-condition" @click="addCondition(group)">
            <i class="el-icon-plus" /> 添加条件
          </div>
        </div>
      </div>

      <!-- 条件组之间关系 -->
      <div v-if="gIndex < model.groups.length - 1" class="cg-group-relation">
        <span>条件组关系</span>
        <a class="cg-group-relation__toggle" @click="toggleGroupRelation">
          {{ model.groupRelation === "and" ? "且" : "或" }}
          <i class="el-icon-sort" />
        </a>
      </div>
    </div>

    <!-- 添加条件组 -->
    <div class="cg-add-group">
      <a @click="addGroup"><i class="el-icon-plus" /> 添加条件组</a>
    </div>
  </div>
</template>

<script>
import {
  OPERATORS,
  isMultiValueOp,
  isNoValueOp,
  createEmptyCondition,
  createEmptyGroup,
  createEmptyModel
} from "./conditionCompiler";

export default {
  name: "ConditionGroupBuilder",
  props: {
    // 结构化条件模型 v-model
    value: {
      type: Object,
      default: null
    },
    // 字段定义列表：[{ label, value, type: 'text'|'number'|'enum'|'boolean', multiple, options:[{label,value}], allowCreate, precision }]
    fields: {
      type: Array,
      default: () => []
    }
  },
  data() {
    return {
      model: createEmptyModel()
    };
  },
  watch: {
    value: {
      immediate: true,
      handler(val) {
        // 由自身 emit 引起的父级回流，跳过重建以避免输入框失焦/光标跳动
        if (this._selfUpdate) {
          this._selfUpdate = false;
          return;
        }
        this.model = this.normalizeModel(val);
      }
    }
  },
  methods: {
    isNoValueOp,
    normalizeModel(val) {
      if (val && Array.isArray(val.groups) && val.groups.length) {
        // 深拷贝，避免直接修改父级引用
        const cloned = JSON.parse(JSON.stringify(val));
        if (cloned.groupRelation !== "and" && cloned.groupRelation !== "or") {
          cloned.groupRelation = "or";
        }
        cloned.groups.forEach(g => {
          if (g.relation !== "and" && g.relation !== "or") g.relation = "and";
          if (!Array.isArray(g.conditions) || !g.conditions.length) {
            g.conditions = [createEmptyCondition()];
          }
        });
        return cloned;
      }
      return createEmptyModel();
    },
    // 根据字段查找定义
    findField(fieldValue) {
      return this.fields.find(f => f.value === fieldValue);
    },
    // 字段的可用操作符
    getOperators(fieldValue) {
      const field = this.findField(fieldValue);
      const type = (field && field.type) || "text";
      return OPERATORS[type] || OPERATORS.text;
    },
    getFieldOptions(fieldValue) {
      const field = this.findField(fieldValue);
      return (field && field.options) || [];
    },
    hasFieldOptions(fieldValue) {
      return this.getFieldOptions(fieldValue).length > 0;
    },
    allowCreate(fieldValue) {
      const field = this.findField(fieldValue);
      // 无预置选项时默认允许自由输入
      if (!field) return true;
      if (field.allowCreate !== undefined) return field.allowCreate;
      return !this.hasFieldOptions(fieldValue);
    },
    numberPrecision(fieldValue) {
      const field = this.findField(fieldValue);
      return field && field.precision !== undefined ? field.precision : 2;
    },
    optLabel(opt) {
      return typeof opt === "object" ? opt.label : opt;
    },
    optValue(opt) {
      return typeof opt === "object" ? opt.value : opt;
    },
    // 是否需要多选控件（多值操作符 或 字段声明为多选）
    needMultiSelect(cond) {
      if (isMultiValueOp(cond.op)) return true;
      const field = this.findField(cond.field);
      return !!(field && field.multiple);
    },
    // 选择字段后，重置操作符与值，并推断值类型
    onFieldChange(cond) {
      const field = this.findField(cond.field);
      cond.valueType = (field && (field.type === "number" ? "number" : field.type === "boolean" ? "boolean" : "text")) || "text";
      const ops = this.getOperators(cond.field);
      cond.op = ops.length ? ops[0].value : "";
      cond.value = this.defaultValueForOp(cond.op, cond);
      this.emitChange();
    },
    // 切换操作符后，调整值的数据形态
    onOpChange(cond) {
      cond.value = this.defaultValueForOp(cond.op, cond);
      this.emitChange();
    },
    defaultValueForOp(op, cond) {
      if (isNoValueOp(op)) return "";
      if (this.needMultiSelect({ ...cond, op })) return [];
      if (cond.valueType === "number") return undefined;
      if (cond.valueType === "boolean") return true;
      return "";
    },
    toggleCollapse(group) {
      this.$set(group, "_collapsed", !group._collapsed);
    },
    addCondition(group) {
      group.conditions.push(createEmptyCondition());
      this.emitChange();
    },
    removeCondition(group, index) {
      group.conditions.splice(index, 1);
      if (!group.conditions.length) {
        group.conditions.push(createEmptyCondition());
      }
      this.emitChange();
    },
    addGroup() {
      this.model.groups.push(createEmptyGroup());
      this.emitChange();
    },
    removeGroup(index) {
      this.model.groups.splice(index, 1);
      if (!this.model.groups.length) {
        this.model.groups.push(createEmptyGroup());
      }
      this.emitChange();
    },
    toggleGroupRelation() {
      this.model.groupRelation = this.model.groupRelation === "and" ? "or" : "and";
      this.emitChange();
    },
    emitChange() {
      // 去除内部临时字段（_collapsed）后向外抛出
      const payload = {
        groupRelation: this.model.groupRelation,
        groups: this.model.groups.map(g => ({
          relation: g.relation,
          conditions: g.conditions.map(c => ({
            field: c.field,
            op: c.op,
            value: c.value,
            valueType: c.valueType
          }))
        }))
      };
      this._selfUpdate = true;
      this.$emit("input", payload);
      this.$emit("change", payload);
    }
  }
};
</script>

<style lang="scss" scoped>
.condition-group-builder {
  .cg-group {
    border: 1px solid #ebeef5;
    border-radius: 4px;
    margin-bottom: 8px;
    background: #fff;

    &__header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 6px 10px;
      background: #f5f7fa;
      border-bottom: 1px solid #ebeef5;
      border-radius: 4px 4px 0 0;
    }

    &__title {
      font-size: 13px;
      color: #303133;
      font-weight: 500;
    }

    &__remove {
      cursor: pointer;
      color: #c0c4cc;
      font-size: 16px;

      &:hover {
        color: #f56c6c;
      }
    }

    &__body {
      padding: 10px;
    }
  }

  .cg-caret {
    cursor: pointer;
    color: #909399;
    margin-right: 2px;
  }

  // 窄面板（属性面板默认约 480px）下无法一行容纳「关系 + 字段 + 操作符 + 值」，
  // 因此采用自动换行布局：第一行放 关系/字段/操作符/删除，值列独占下一整行，
  // 保证第三列（值）始终完整可见。
  .cg-condition {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 6px;
    margin-bottom: 8px;

    .cg-relation {
      width: 46px;
      flex: 0 0 46px;

      &--placeholder {
        display: inline-block;
      }
    }

    .cg-field {
      flex: 1 1 90px;
      min-width: 0;
    }

    .cg-op {
      flex: 1 1 80px;
      min-width: 0;
    }

    // 值：通过 order 排到末尾并占满整行，独立换行显示
    .cg-value {
      order: 10;
      flex: 1 1 100%;
      min-width: 0;

      &__input {
        width: 100%;
      }

      &--none {
        color: #c0c4cc;
        font-size: 12px;
      }
    }

    &__remove {
      cursor: pointer;
      color: #c0c4cc;
      font-size: 16px;
      flex: 0 0 auto;
      margin-left: auto;

      &:hover {
        color: #f56c6c;
      }
    }
  }

  .cg-add-condition {
    cursor: pointer;
    text-align: center;
    color: #909399;
    font-size: 13px;
    border: 1px dashed #dcdfe6;
    border-radius: 4px;
    padding: 6px 0;

    &:hover {
      color: #409eff;
      border-color: #409eff;
    }
  }

  .cg-group-relation {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 8px;
    font-size: 12px;
    color: #909399;
    margin: 4px 2px 10px;

    &__toggle {
      cursor: pointer;
      color: #409eff;

      &:hover {
        opacity: 0.8;
      }
    }
  }

  .cg-add-group {
    text-align: right;
    margin-top: 4px;

    a {
      cursor: pointer;
      color: #409eff;
      font-size: 13px;

      &:hover {
        opacity: 0.8;
      }
    }
  }
}
</style>
