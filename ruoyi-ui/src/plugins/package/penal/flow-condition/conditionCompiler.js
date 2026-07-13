/**
 * 可视化条件组 <-> Flowable UEL 表达式 编译工具
 *
 * 结构化数据模型：
 * {
 *   groupRelation: 'and' | 'or',        // 条件组之间的关系
 *   groups: [
 *     {
 *       relation: 'and' | 'or',         // 组内条件之间的关系
 *       conditions: [
 *         { field: 'contractAmount', op: 'lt', value: 200000, valueType: 'number' },
 *         ...
 *       ]
 *     }
 *   ]
 * }
 *
 * 编译产物示例：
 * ${ (companyEntity.contains("FS JAPAN CO., LTD.") && contractAmount < 200000 && !(signType == "电子签"))
 *    || (!companyEntity.contains("FS JAPAN CO., LTD.") && contractAmount <= 10000 && !(signType == "电子签")) }
 */

// 操作符定义：按字段类型提供可选操作符
export const OPERATORS = {
  text: [
    { label: "包含", value: "contains" },
    { label: "不包含", value: "notContains" },
    { label: "等于", value: "eq" },
    { label: "不等于", value: "ne" },
    { label: "为空", value: "empty" },
    { label: "不为空", value: "notEmpty" }
  ],
  number: [
    { label: "等于", value: "eq" },
    { label: "不等于", value: "ne" },
    { label: "大于", value: "gt" },
    { label: "大于等于", value: "ge" },
    { label: "小于", value: "lt" },
    { label: "小于等于", value: "le" }
  ],
  enum: [
    { label: "属于", value: "in" },
    { label: "不属于", value: "notIn" },
    { label: "等于", value: "eq" },
    { label: "不等于", value: "ne" }
  ],
  boolean: [
    { label: "等于", value: "eq" },
    { label: "不等于", value: "ne" }
  ]
};

// 操作符中文标签（用于回显/展示）
export const OPERATOR_LABELS = {
  contains: "包含",
  notContains: "不包含",
  eq: "等于",
  ne: "不等于",
  gt: "大于",
  ge: "大于等于",
  lt: "小于",
  le: "小于等于",
  in: "属于",
  notIn: "不属于",
  empty: "为空",
  notEmpty: "不为空"
};

// 多值型操作符（值为数组）
const MULTI_VALUE_OPS = ["in", "notIn"];
// 无需值的操作符
const NO_VALUE_OPS = ["empty", "notEmpty"];

export function isMultiValueOp(op) {
  return MULTI_VALUE_OPS.includes(op);
}

export function isNoValueOp(op) {
  return NO_VALUE_OPS.includes(op);
}

/**
 * 将单个值转为表达式字面量
 * - number/boolean 直接输出
 * - 其余按字符串处理，转义双引号
 */
function literal(value, valueType) {
  if (value === null || value === undefined) return '""';
  if (valueType === "number") {
    return `${value}`;
  }
  if (valueType === "boolean") {
    return value === true || value === "true" ? "true" : "false";
  }
  const str = String(value).replace(/\\/g, "\\\\").replace(/"/g, '\\"');
  return `"${str}"`;
}

/**
 * 编译单个条件为表达式片段（不含最外层 ${}）
 */
export function compileCondition(cond) {
  const { field, op, value, valueType } = cond || {};
  if (!field || !op) return "";

  switch (op) {
    case "empty":
      return `(${field} == null || ${field} == "")`;
    case "notEmpty":
      return `(${field} != null && ${field} != "")`;
    case "contains": {
      const vals = normalizeArray(value);
      if (!vals.length) return "";
      // 包含任一值
      const parts = vals.map(v => `${field}.contains(${literal(v, valueType)})`);
      return parts.length > 1 ? `(${parts.join(" || ")})` : parts[0];
    }
    case "notContains": {
      const vals = normalizeArray(value);
      if (!vals.length) return "";
      const parts = vals.map(v => `${field}.contains(${literal(v, valueType)})`);
      const inner = parts.length > 1 ? `(${parts.join(" || ")})` : parts[0];
      return `!${inner}`;
    }
    case "in": {
      const vals = normalizeArray(value);
      if (!vals.length) return "";
      const parts = vals.map(v => `${field} == ${literal(v, valueType)}`);
      return parts.length > 1 ? `(${parts.join(" || ")})` : parts[0];
    }
    case "notIn": {
      const vals = normalizeArray(value);
      if (!vals.length) return "";
      const parts = vals.map(v => `${field} != ${literal(v, valueType)}`);
      return parts.length > 1 ? `(${parts.join(" && ")})` : parts[0];
    }
    case "eq":
      return `${field} == ${literal(value, valueType)}`;
    case "ne":
      return `${field} != ${literal(value, valueType)}`;
    case "gt":
      return `${field} > ${literal(value, valueType)}`;
    case "ge":
      return `${field} >= ${literal(value, valueType)}`;
    case "lt":
      return `${field} < ${literal(value, valueType)}`;
    case "le":
      return `${field} <= ${literal(value, valueType)}`;
    default:
      return "";
  }
}

function normalizeArray(value) {
  if (Array.isArray(value)) return value.filter(v => v !== null && v !== undefined && v !== "");
  if (value === null || value === undefined || value === "") return [];
  return [value];
}

/**
 * 编译单个条件组为表达式片段
 */
export function compileGroup(group) {
  if (!group || !Array.isArray(group.conditions)) return "";
  const joiner = group.relation === "or" ? " || " : " && ";
  const parts = group.conditions.map(compileCondition).filter(Boolean);
  if (!parts.length) return "";
  return parts.length > 1 ? `(${parts.join(joiner)})` : parts[0];
}

/**
 * 编译整个条件组模型为完整 UEL 表达式（含 ${}）
 * 无有效条件时返回空字符串
 */
export function compileConditionModel(model) {
  if (!model || !Array.isArray(model.groups)) return "";
  const joiner = model.groupRelation === "and" ? " && " : " || ";
  const parts = model.groups.map(compileGroup).filter(Boolean);
  if (!parts.length) return "";
  const expr = parts.length > 1 ? parts.map(p => `(${p})`).join(joiner) : parts[0];
  return `\${${expr}}`;
}

/**
 * 生成一个空条件
 */
export function createEmptyCondition() {
  return { field: "", op: "", value: "", valueType: "text" };
}

/**
 * 生成一个空条件组
 */
export function createEmptyGroup() {
  return { relation: "and", conditions: [createEmptyCondition()] };
}

/**
 * 生成一个空的条件组模型
 */
export function createEmptyModel() {
  return { groupRelation: "or", groups: [createEmptyGroup()] };
}
