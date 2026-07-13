<template>
  <div class="node-config">
    <!-- 基础配置 -->
    <div class="nc-form">
      <!-- 节点名称 -->
      <div class="nc-form-item">
        <label class="nc-label">节点名称</label>
        <el-input v-model="nodeName" placeholder="请输入" clearable @change="changeNodeName" />
      </div>

      <!-- 节点类型 + 经办人 -->
      <el-row :gutter="12">
        <el-col :span="12">
          <div class="nc-form-item">
            <label class="nc-label">节点类型</label>
            <el-select v-model="dataType" placeholder="请选择" style="width: 100%" @change="changeDataType">
              <el-option label="指定用户" value="USERS" />
              <el-option label="角色" value="ROLES" />
              <el-option label="部门" value="DEPTS" />
              <el-option label="发起人" value="INITIATOR" />
            </el-select>
          </div>
        </el-col>
        <el-col :span="12">
          <div class="nc-form-item">
            <label class="nc-label">经办人</label>
            <!-- 指定用户 -->
            <template v-if="dataType === 'USERS'">
              <div class="nc-assignee" @click="onSelectUsers">
                <span v-if="selectedUser.text && selectedUser.text.length" class="nc-assignee-text">
                  {{ selectedUser.text.join('，') }}
                </span>
                <span v-else class="nc-placeholder">请选择</span>
                <i class="el-icon-arrow-down nc-suffix"></i>
              </div>
            </template>
            <!-- 角色 -->
            <el-select
              v-else-if="dataType === 'ROLES'"
              v-model="roleIds"
              multiple
              collapse-tags
              placeholder="请选择"
              style="width: 100%"
              @change="changeSelectRoles"
            >
              <el-option
                v-for="item in roleOptions"
                :key="item.roleId"
                :label="item.roleName"
                :value="`ROLE${item.roleId}`"
                :disabled="item.status === 1"
              />
            </el-select>
            <!-- 部门 -->
            <tree-select
              v-else-if="dataType === 'DEPTS'"
              :width="320"
              :height="400"
              :data="deptTreeData"
              :defaultProps="deptProps"
              multiple
              clearable
              checkStrictly
              nodeKey="id"
              :checkedKeys="deptIds"
              @change="checkedDeptChange"
            />
            <!-- 发起人 -->
            <el-input v-else value="流程发起人" disabled />
          </div>
        </el-col>
      </el-row>

      <!-- 审批模式 -->
      <div class="nc-form-item" v-show="showMultiFlog">
        <label class="nc-label">审批模式</label>
        <div class="nc-mode-cards">
          <div
            class="nc-mode-card"
            :class="{ active: multiLoopType === 'SequentialMultiInstance' }"
            @click="selectMode('SequentialMultiInstance')"
          >
            <div class="nc-mode-radio">
              <i :class="multiLoopType === 'SequentialMultiInstance' ? 'el-icon-success' : 'nc-radio-empty'"></i>
              <span class="nc-mode-title">会签</span>
            </div>
            <div class="nc-mode-desc">所有审批人全部审批同意，则完成节点</div>
          </div>
          <div
            class="nc-mode-card"
            :class="{ active: multiLoopType === 'ParallelMultiInstance' }"
            @click="selectMode('ParallelMultiInstance')"
          >
            <div class="nc-mode-radio">
              <i :class="multiLoopType === 'ParallelMultiInstance' ? 'el-icon-success' : 'nc-radio-empty'"></i>
              <span class="nc-mode-title">或签</span>
            </div>
            <div class="nc-mode-desc">一位审批人审批同意或拒绝，则完成节点</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 功能配置 -->
    <div class="nc-section">
      <div class="nc-section-title">功能配置</div>

      <!-- 审批通过 -->
      <div class="nc-func-card">
        <div class="nc-func-row">
          <div class="nc-func-left">
            <el-switch v-model="funcConfig.approve" @change="updateProperties" />
            <span class="nc-func-name">审批通过</span>
          </div>
          <span class="nc-config-btn" @click="funcConfig.approveExpand = !funcConfig.approveExpand">
            <i class="el-icon-setting"></i> 配置
          </span>
        </div>
        <div class="nc-func-detail" v-show="funcConfig.approveExpand && funcConfig.approve">
          <div class="nc-detail-row">
            <label class="nc-detail-label">通知模板：</label>
            <el-input v-model="funcConfig.approveTemplate" placeholder="请输入" style="flex: 1" clearable @change="updateProperties" />
          </div>
          <div class="nc-detail-row">
            <label class="nc-detail-label">接收人：</label>
            <el-input v-model="funcConfig.approveReceiver" placeholder="请输入" style="flex: 1" clearable @change="updateProperties" />
          </div>
        </div>
      </div>

      <!-- 审批驳回 -->
      <div class="nc-func-card">
        <div class="nc-func-row">
          <div class="nc-func-left">
            <el-switch v-model="funcConfig.reject" @change="updateProperties" />
            <span class="nc-func-name">审批驳回</span>
          </div>
          <span class="nc-config-btn"><i class="el-icon-setting"></i> 配置</span>
        </div>
      </div>

      <!-- 审批加签 -->
      <div class="nc-func-card">
        <div class="nc-func-row">
          <div class="nc-func-left">
            <el-switch v-model="funcConfig.addSign" @change="updateProperties" />
            <span class="nc-func-name">审批加签</span>
          </div>
          <span class="nc-config-btn"><i class="el-icon-setting"></i> 配置</span>
        </div>
      </div>

      <!-- 审批转办 -->
      <div class="nc-func-card">
        <div class="nc-func-row">
          <div class="nc-func-left">
            <el-switch v-model="funcConfig.transfer" @change="updateProperties" />
            <span class="nc-func-name">审批转办</span>
          </div>
          <span class="nc-config-btn"><i class="el-icon-setting"></i> 配置</span>
        </div>
      </div>

      <!-- 审批抄送 -->
      <div class="nc-func-card">
        <div class="nc-func-row">
          <div class="nc-func-left">
            <el-switch v-model="funcConfig.copy" @change="updateProperties" />
            <span class="nc-func-name">审批抄送</span>
          </div>
          <span class="nc-config-btn" @click="funcConfig.copyExpand = !funcConfig.copyExpand">
            <i class="el-icon-setting"></i> 配置
          </span>
        </div>
        <div class="nc-func-detail" v-show="funcConfig.copyExpand && funcConfig.copy">
          <div class="nc-detail-row">
            <label class="nc-detail-label">通知模板：</label>
            <el-input v-model="funcConfig.copyTemplate" placeholder="请输入" style="flex: 1" clearable @change="updateProperties" />
          </div>
        </div>
      </div>
    </div>

    <!-- 通知配置 -->
    <div class="nc-section">
      <div class="nc-section-title">通知配置</div>

      <!-- 节点到达 -->
      <div class="nc-notify-card">
        <div class="nc-notify-header">
          <span class="nc-notify-name">节点到达</span>
          <span class="nc-config-btn" @click="notifyConfig.arriveExpand = !notifyConfig.arriveExpand">
            <i class="el-icon-setting"></i> 配置
          </span>
        </div>
        <div class="nc-func-detail" v-show="notifyConfig.arriveExpand">
          <div class="nc-detail-row">
            <label class="nc-detail-label">通知模板：</label>
            <el-input v-model="notifyConfig.arriveTemplate" placeholder="请输入" style="flex: 1" clearable @change="updateProperties" />
          </div>
          <div class="nc-detail-row">
            <label class="nc-detail-label">接收人：</label>
            <el-input v-model="notifyConfig.arriveReceiver" placeholder="请输入" style="flex: 1" clearable @change="updateProperties" />
          </div>
        </div>
      </div>

      <!-- 节点完成 -->
      <div class="nc-notify-card">
        <div class="nc-notify-header">
          <span class="nc-notify-name">节点完成</span>
          <span class="nc-config-btn" @click="notifyConfig.completeExpand = !notifyConfig.completeExpand">
            <i class="el-icon-setting"></i> 配置
          </span>
        </div>
        <div class="nc-func-detail" v-show="notifyConfig.completeExpand">
          <div class="nc-detail-row">
            <label class="nc-detail-label">通知模板：</label>
            <el-input v-model="notifyConfig.completeTemplate" placeholder="请输入" style="flex: 1" clearable @change="updateProperties" />
          </div>
          <div class="nc-detail-row">
            <label class="nc-detail-label">接收人：</label>
            <el-input v-model="notifyConfig.completeReceiver" placeholder="请输入" style="flex: 1" clearable @change="updateProperties" />
          </div>
        </div>
      </div>
    </div>

    <!-- 候选用户弹窗 -->
    <el-dialog title="候选用户" :visible.sync="userOpen" width="60%" append-to-body>
      <el-row type="flex" :gutter="20">
        <el-col :span="7">
          <el-card shadow="never" style="height: 100%">
            <div slot="header"><span>部门列表</span></div>
            <div class="head-container">
              <el-input
                v-model="deptName"
                placeholder="请输入部门名称"
                clearable
                size="small"
                prefix-icon="el-icon-search"
                style="margin-bottom: 20px"
              />
              <el-tree
                :data="deptOptions"
                :props="deptProps"
                :expand-on-click-node="false"
                :filter-node-method="filterNode"
                ref="tree"
                default-expand-all
                @node-click="handleNodeClick"
              />
            </div>
          </el-card>
        </el-col>
        <el-col :span="17">
          <el-table ref="multipleTable" height="600" :data="userTableList" border @selection-change="handleSelectionChange">
            <el-table-column type="selection" width="50" align="center" />
            <el-table-column label="用户名" align="center" prop="nickName" />
            <el-table-column label="部门" align="center" prop="dept.deptName" />
          </el-table>
          <pagination
            :total="userTotal"
            :page.sync="queryParams.pageNum"
            :limit.sync="queryParams.pageSize"
            @pagination="getUserList"
          />
        </el-col>
      </el-row>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="handleTaskUserComplete">确 定</el-button>
        <el-button @click="userOpen = false">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listUser, deptTreeSelect } from "@/api/system/user";
import { listRole } from "@/api/system/role";
import TreeSelect from "@/components/TreeSelect";

const userTaskForm = {
  dataType: '',
  assignee: '',
  candidateUsers: '',
  candidateGroups: '',
  text: ''
};

// 功能配置 / 通知配置 使用的扩展属性名称前缀
const FUNC_KEYS = {
  approve: 'func_approve',
  approveTemplate: 'func_approve_template',
  approveReceiver: 'func_approve_receiver',
  reject: 'func_reject',
  addSign: 'func_add_sign',
  transfer: 'func_transfer',
  copy: 'func_copy',
  copyTemplate: 'func_copy_template'
};
const NOTIFY_KEYS = {
  arriveTemplate: 'notify_arrive_template',
  arriveReceiver: 'notify_arrive_receiver',
  completeTemplate: 'notify_complete_template',
  completeReceiver: 'notify_complete_receiver'
};

export default {
  name: "UserTaskNodeConfig",
  components: { TreeSelect },
  props: {
    id: String,
    type: String
  },
  inject: {
    prefix: "prefix"
  },
  data() {
    return {
      nodeName: '',
      dataType: 'USERS',
      selectedUser: { ids: [], text: [] },
      userOpen: false,
      deptName: undefined,
      deptOptions: [],
      deptProps: { children: "children", label: "label" },
      userTableList: [],
      userTotal: 0,
      selectedUserDate: [],
      roleOptions: [],
      roleIds: [],
      deptTreeData: [],
      deptIds: [],
      queryParams: { deptId: undefined, pageNum: 1, pageSize: 10 },
      showMultiFlog: false,
      isSequential: false,
      multiLoopType: 'Null',
      // 功能配置
      funcConfig: {
        approve: true,
        approveExpand: false,
        approveTemplate: '',
        approveReceiver: '',
        reject: true,
        addSign: true,
        transfer: false,
        copy: true,
        copyExpand: false,
        copyTemplate: ''
      },
      // 通知配置
      notifyConfig: {
        arriveExpand: false,
        arriveTemplate: '',
        arriveReceiver: '',
        completeExpand: false,
        completeTemplate: '',
        completeReceiver: ''
      }
    };
  },
  watch: {
    id: {
      immediate: true,
      handler() {
        this.bpmnElement = window.bpmnInstances.bpmnElement;
        this.$nextTick(() => this.resetTaskForm());
      }
    },
    deptName(val) {
      this.$refs.tree && this.$refs.tree.filter(val);
    }
  },
  beforeDestroy() {
    this.bpmnElement = null;
  },
  methods: {
    resetTaskForm() {
      const bpmnElementObj = this.bpmnElement?.businessObject;
      if (!bpmnElementObj) return;
      this.clearOptionsData();
      this.nodeName = bpmnElementObj.name || '';
      this.dataType = bpmnElementObj['dataType'] || 'USERS';
      if (this.dataType === 'USERS') {
        let userIdData = bpmnElementObj['candidateUsers'] || bpmnElementObj['assignee'];
        let userText = bpmnElementObj['text'] || [];
        if (userIdData && userIdData.toString().length > 0 && userText && userText.length > 0) {
          this.selectedUser.ids = userIdData?.toString().split(',');
          this.selectedUser.text = userText?.split(',');
        }
        if (this.selectedUser.ids.length > 1) {
          this.showMultiFlog = true;
        }
      } else if (this.dataType === 'ROLES') {
        this.getRoleOptions();
        let roleIdData = bpmnElementObj['candidateGroups'] || [];
        if (roleIdData && roleIdData.length > 0) {
          this.roleIds = roleIdData.split(',');
        }
        this.showMultiFlog = true;
      } else if (this.dataType === 'DEPTS') {
        this.getDeptTreeData();
        let deptIdData = bpmnElementObj['candidateGroups'] || [];
        if (deptIdData && deptIdData.length > 0) {
          this.deptIds = deptIdData.split(',');
        }
        this.showMultiFlog = true;
      }
      this.getElementLoop(bpmnElementObj);
      this.resetPropertiesConfig();
    },
    clearOptionsData() {
      this.selectedUser.ids = [];
      this.selectedUser.text = [];
      this.roleIds = [];
      this.deptIds = [];
    },
    changeNodeName() {
      window.bpmnInstances.modeling.updateProperties(this.bpmnElement, { name: this.nodeName });
    },
    updateElementTask() {
      const taskAttr = Object.create(null);
      for (let key in userTaskForm) {
        taskAttr[key] = userTaskForm[key];
      }
      window.bpmnInstances.modeling.updateProperties(this.bpmnElement, taskAttr);
    },
    /* ------------ 经办人相关 ------------ */
    getDeptOptions() {
      return new Promise((resolve, reject) => {
        if (!this.deptOptions || this.deptOptions.length <= 0) {
          deptTreeSelect().then(response => {
            this.deptOptions = response.data;
            resolve();
          });
        } else {
          reject();
        }
      });
    },
    getDeptTreeData() {
      function refactorTree(data) {
        return data.map(node => {
          let treeData = { id: `DEPT${node.id}`, label: node.label, parentId: node.parentId, weight: node.weight };
          if (node.children && node.children.length > 0) {
            treeData.children = refactorTree(node.children);
          }
          return treeData;
        });
      }
      return new Promise((resolve, reject) => {
        if (!this.deptTreeData || this.deptTreeData.length <= 0) {
          this.getDeptOptions().then(() => {
            this.deptTreeData = refactorTree(this.deptOptions);
            resolve();
          }).catch(() => reject());
        } else {
          resolve();
        }
      });
    },
    getRoleOptions() {
      if (!this.roleOptions || this.roleOptions.length <= 0) {
        listRole().then(response => this.roleOptions = response.rows);
      }
    },
    getUserList() {
      listUser(this.queryParams).then(response => {
        this.userTableList = response.rows;
        this.userTotal = response.total;
      });
    },
    filterNode(value, data) {
      if (!value) return true;
      return data.label.indexOf(value) !== -1;
    },
    handleNodeClick(data) {
      this.queryParams.deptId = data.id;
      this.getUserList();
    },
    handleSelectionChange(selection) {
      this.selectedUserDate = selection;
    },
    onSelectUsers() {
      this.selectedUserDate = [];
      this.$refs.multipleTable?.clearSelection();
      this.getDeptOptions();
      this.userOpen = true;
    },
    handleTaskUserComplete() {
      if (!this.selectedUserDate || this.selectedUserDate.length <= 0) {
        this.$modal.msgError('请选择用户');
        return;
      }
      userTaskForm.dataType = 'USERS';
      this.selectedUser.text = this.selectedUserDate.map(k => k.nickName) || [];
      this.selectedUser.ids = this.selectedUserDate.map(k => k.userId) || [];
      if (this.selectedUserDate.length === 1) {
        let data = this.selectedUserDate[0];
        userTaskForm.assignee = data.userId;
        userTaskForm.text = data.nickName;
        userTaskForm.candidateUsers = null;
        this.showMultiFlog = false;
        this.multiLoopType = 'Null';
        this.changeMultiLoopType();
      } else {
        userTaskForm.candidateUsers = this.selectedUserDate.map(k => k.userId).join() || null;
        userTaskForm.text = this.selectedUserDate.map(k => k.nickName).join() || null;
        userTaskForm.assignee = null;
        this.showMultiFlog = true;
      }
      this.updateElementTask();
      this.userOpen = false;
    },
    changeSelectRoles(val) {
      let groups = null;
      let text = null;
      if (val && val.length > 0) {
        userTaskForm.dataType = 'ROLES';
        groups = val.join() || null;
        let textArr = this.roleOptions.filter(k => val.indexOf(`ROLE${k.roleId}`) >= 0);
        text = textArr?.map(k => k.roleName).join() || null;
      } else {
        userTaskForm.dataType = null;
        this.multiLoopType = 'Null';
      }
      userTaskForm.candidateGroups = groups;
      userTaskForm.text = text;
      this.updateElementTask();
      this.changeMultiLoopType();
    },
    checkedDeptChange(checkedIds) {
      let groups = null;
      let text = null;
      this.deptIds = checkedIds;
      if (checkedIds && checkedIds.length > 0) {
        userTaskForm.dataType = 'DEPTS';
        groups = checkedIds.join() || null;
        let textArr = [];
        let treeStarkData = JSON.parse(JSON.stringify(this.deptTreeData));
        checkedIds.forEach(id => {
          let stark = [];
          stark = stark.concat(treeStarkData);
          while (stark.length) {
            let temp = stark.shift();
            if (temp.children) {
              stark = temp.children.concat(stark);
            }
            if (id === temp.id) {
              textArr.push(temp);
            }
          }
        });
        text = textArr?.map(k => k.label).join() || null;
      } else {
        userTaskForm.dataType = null;
        this.multiLoopType = 'Null';
      }
      userTaskForm.candidateGroups = groups;
      userTaskForm.text = text;
      this.updateElementTask();
      this.changeMultiLoopType();
    },
    changeDataType(val) {
      if (val === 'ROLES' || val === 'DEPTS' || (val === 'USERS' && this.selectedUser.ids.length > 1)) {
        this.showMultiFlog = true;
      } else {
        this.showMultiFlog = false;
      }
      this.multiLoopType = 'Null';
      this.changeMultiLoopType();
      Object.keys(userTaskForm).forEach(key => userTaskForm[key] = null);
      userTaskForm.dataType = val;
      if (val === 'USERS') {
        if (this.selectedUser && this.selectedUser.ids && this.selectedUser.ids.length > 0) {
          if (this.selectedUser.ids.length === 1) {
            userTaskForm.assignee = this.selectedUser.ids[0];
          } else {
            userTaskForm.candidateUsers = this.selectedUser.ids.join();
          }
          userTaskForm.text = this.selectedUser.text?.join() || null;
        }
      } else if (val === 'ROLES') {
        this.getRoleOptions();
        if (this.roleIds && this.roleIds.length > 0) {
          userTaskForm.candidateGroups = this.roleIds.join() || null;
          let textArr = this.roleOptions.filter(k => this.roleIds.indexOf(`ROLE${k.roleId}`) >= 0);
          userTaskForm.text = textArr?.map(k => k.roleName).join() || null;
        }
      } else if (val === 'DEPTS') {
        this.getDeptTreeData();
        if (this.deptIds && this.deptIds.length > 0) {
          userTaskForm.candidateGroups = this.deptIds.join() || null;
          let textArr = [];
          let treeStarkData = JSON.parse(JSON.stringify(this.deptTreeData));
          this.deptIds.forEach(id => {
            let stark = [];
            stark = stark.concat(treeStarkData);
            while (stark.length) {
              let temp = stark.shift();
              if (temp.children) {
                stark = temp.children.concat(stark);
              }
              if (id === temp.id) {
                textArr.push(temp);
              }
            }
          });
          userTaskForm.text = textArr?.map(k => k.label).join() || null;
        }
      } else if (val === 'INITIATOR') {
        userTaskForm.assignee = "${initiator}";
        userTaskForm.text = "流程发起人";
      }
      this.updateElementTask();
    },
    /* ------------ 审批模式（多实例） ------------ */
    getElementLoop(businessObject) {
      if (!businessObject.loopCharacteristics) {
        this.multiLoopType = "Null";
        return;
      }
      this.isSequential = businessObject.loopCharacteristics.isSequential;
      if (businessObject.loopCharacteristics.completionCondition) {
        if (businessObject.loopCharacteristics.completionCondition.body === "${nrOfCompletedInstances >= nrOfInstances}") {
          this.multiLoopType = "SequentialMultiInstance";
        } else {
          this.multiLoopType = "ParallelMultiInstance";
        }
      }
    },
    selectMode(mode) {
      this.multiLoopType = mode;
      this.changeMultiLoopType();
    },
    changeMultiLoopType() {
      if (this.multiLoopType === "Null") {
        window.bpmnInstances.modeling.updateProperties(this.bpmnElement, { loopCharacteristics: null, assignee: null });
        return;
      }
      this.multiLoopInstance = window.bpmnInstances.moddle.create("bpmn:MultiInstanceLoopCharacteristics", { isSequential: this.isSequential });
      window.bpmnInstances.modeling.updateProperties(this.bpmnElement, {
        loopCharacteristics: this.multiLoopInstance,
        assignee: '${assignee}'
      });
      let completionCondition = null;
      if (this.multiLoopType === "SequentialMultiInstance") {
        completionCondition = window.bpmnInstances.moddle.create("bpmn:FormalExpression", { body: "${nrOfCompletedInstances >= nrOfInstances}" });
      }
      if (this.multiLoopType === "ParallelMultiInstance") {
        completionCondition = window.bpmnInstances.moddle.create("bpmn:FormalExpression", { body: "${nrOfCompletedInstances > 0}" });
      }
      window.bpmnInstances.modeling.updateModdleProperties(this.bpmnElement, this.multiLoopInstance, {
        collection: '${multiInstanceHandler.getUserIds(execution)}',
        elementVariable: 'assignee',
        completionCondition
      });
    },
    /* ------------ 功能配置 / 通知配置（flowable:Properties 扩展） ------------ */
    // 读取扩展属性并回填表单
    resetPropertiesConfig() {
      const props = this.getPropertyMap();
      // 功能配置
      this.funcConfig.approve = this.boolVal(props[FUNC_KEYS.approve], true);
      this.funcConfig.approveTemplate = props[FUNC_KEYS.approveTemplate] || '';
      this.funcConfig.approveReceiver = props[FUNC_KEYS.approveReceiver] || '';
      this.funcConfig.reject = this.boolVal(props[FUNC_KEYS.reject], true);
      this.funcConfig.addSign = this.boolVal(props[FUNC_KEYS.addSign], true);
      this.funcConfig.transfer = this.boolVal(props[FUNC_KEYS.transfer], false);
      this.funcConfig.copy = this.boolVal(props[FUNC_KEYS.copy], true);
      this.funcConfig.copyTemplate = props[FUNC_KEYS.copyTemplate] || '';
      // 通知配置
      this.notifyConfig.arriveTemplate = props[NOTIFY_KEYS.arriveTemplate] || '';
      this.notifyConfig.arriveReceiver = props[NOTIFY_KEYS.arriveReceiver] || '';
      this.notifyConfig.completeTemplate = props[NOTIFY_KEYS.completeTemplate] || '';
      this.notifyConfig.completeReceiver = props[NOTIFY_KEYS.completeReceiver] || '';
    },
    boolVal(val, defaultVal) {
      if (val === undefined || val === null || val === '') return defaultVal;
      return val === 'true' || val === true;
    },
    // 获取当前节点已保存的扩展属性（name -> value）
    getPropertyMap() {
      const map = {};
      const values = this.bpmnElement?.businessObject?.extensionElements?.values ?? [];
      values
        .filter(ex => ex.$type === `${this.prefix}:Properties`)
        .forEach(properties => {
          (properties.values || []).forEach(p => {
            map[p.name] = p.value;
          });
        });
      return map;
    },
    // 将功能配置/通知配置写回扩展属性，并同步任务监听器
    updateProperties() {
      const prefix = this.prefix;
      const moddle = window.bpmnInstances.moddle;
      const configMap = {
        [FUNC_KEYS.approve]: String(this.funcConfig.approve),
        [FUNC_KEYS.approveTemplate]: this.funcConfig.approveTemplate || '',
        [FUNC_KEYS.approveReceiver]: this.funcConfig.approveReceiver || '',
        [FUNC_KEYS.reject]: String(this.funcConfig.reject),
        [FUNC_KEYS.addSign]: String(this.funcConfig.addSign),
        [FUNC_KEYS.transfer]: String(this.funcConfig.transfer),
        [FUNC_KEYS.copy]: String(this.funcConfig.copy),
        [FUNC_KEYS.copyTemplate]: this.funcConfig.copyTemplate || '',
        [NOTIFY_KEYS.arriveTemplate]: this.notifyConfig.arriveTemplate || '',
        [NOTIFY_KEYS.arriveReceiver]: this.notifyConfig.arriveReceiver || '',
        [NOTIFY_KEYS.completeTemplate]: this.notifyConfig.completeTemplate || '',
        [NOTIFY_KEYS.completeReceiver]: this.notifyConfig.completeReceiver || ''
      };
      const managedKeys = new Set(Object.keys(configMap));
      const allExtensions = this.bpmnElement?.businessObject?.extensionElements?.values ?? [];

      // 分类：保留其它扩展元素与其它属性，丢弃本组件旧的通知监听器（稍后重建）
      const otherExtensions = [];
      const keptProperties = [];
      allExtensions.forEach(ex => {
        if (ex.$type === `${prefix}:Properties`) {
          (ex.values || []).forEach(p => {
            if (!managedKeys.has(p.name)) {
              keptProperties.push(moddle.create(`${prefix}:Property`, { name: p.name, value: p.value }));
            }
          });
        } else if (ex.$type === `${prefix}:TaskListener` && this.isManagedNotifyListener(ex)) {
          // 丢弃旧的通知监听器
        } else {
          otherExtensions.push(ex);
        }
      });

      // 本组件管理的属性
      const managedProperties = Object.keys(configMap).map(name =>
        moddle.create(`${prefix}:Property`, { name, value: configMap[name] })
      );
      const propertiesObject = moddle.create(`${prefix}:Properties`, {
        values: keptProperties.concat(managedProperties)
      });

      // 根据通知配置生成任务监听器（节点到达=create，节点完成=complete）
      const notifyListeners = this.buildNotifyListeners();

      const extensions = moddle.create("bpmn:ExtensionElements", {
        values: otherExtensions.concat([propertiesObject]).concat(notifyListeners)
      });
      window.bpmnInstances.modeling.updateProperties(this.bpmnElement, {
        extensionElements: extensions
      });
    },
    // 判断是否为本组件生成的通知监听器（通过注入字段 notifyType 标识）
    isManagedNotifyListener(listener) {
      const fields = listener.fields || [];
      return fields.some(f => f.name === 'notifyType');
    },
    // 根据通知配置构建任务监听器（节点到达=create，节点完成=complete）
    buildNotifyListeners() {
      const prefix = this.prefix;
      const moddle = window.bpmnInstances.moddle;
      const listeners = [];
      const makeField = (name, value) => moddle.create(`${prefix}:Field`, { name, string: value || '' });
      // 节点到达 -> 任务创建事件
      if ((this.notifyConfig.arriveTemplate && this.notifyConfig.arriveTemplate.length) ||
          (this.notifyConfig.arriveReceiver && this.notifyConfig.arriveReceiver.length)) {
        listeners.push(moddle.create(`${prefix}:TaskListener`, {
          event: 'create',
          delegateExpression: '${userTaskListener}',
          fields: [
            makeField('notifyType', 'nodeArrive'),
            makeField('notifyTemplate', this.notifyConfig.arriveTemplate),
            makeField('notifyUsers', this.notifyConfig.arriveReceiver)
          ]
        }));
      }
      // 节点完成 -> 任务完成事件
      if ((this.notifyConfig.completeTemplate && this.notifyConfig.completeTemplate.length) ||
          (this.notifyConfig.completeReceiver && this.notifyConfig.completeReceiver.length)) {
        listeners.push(moddle.create(`${prefix}:TaskListener`, {
          event: 'complete',
          delegateExpression: '${userTaskListener}',
          fields: [
            makeField('notifyType', 'nodeComplete'),
            makeField('notifyTemplate', this.notifyConfig.completeTemplate),
            makeField('notifyUsers', this.notifyConfig.completeReceiver)
          ]
        }));
      }
      return listeners;
    }
  }
};
</script>

<style scoped lang="scss">
$primary: #2563eb;
$border: #e4e7ed;

.node-config {
  padding: 4px 2px 12px;
  font-size: 14px;
  color: #303133;
}

/* 基础表单 */
.nc-form-item {
  margin-bottom: 16px;
}
.nc-label {
  display: block;
  margin-bottom: 8px;
  font-size: 14px;
  color: #303133;
}

/* 经办人 假输入框 */
.nc-assignee {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 32px;
  padding: 0 12px;
  border: 1px solid $border;
  border-radius: 4px;
  cursor: pointer;
  background: #fff;
  &:hover {
    border-color: #c0c4cc;
  }
  .nc-assignee-text {
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }
  .nc-placeholder {
    color: #c0c4cc;
  }
  .nc-suffix {
    color: #c0c4cc;
    font-size: 14px;
  }
}

/* 审批模式卡片 */
.nc-mode-cards {
  display: flex;
  gap: 12px;
}
.nc-mode-card {
  flex: 1;
  padding: 12px 14px;
  border: 1px solid $border;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  &.active {
    border-color: $primary;
    background: #f5f8ff;
  }
  .nc-mode-radio {
    display: flex;
    align-items: center;
    margin-bottom: 6px;
    .el-icon-success {
      color: $primary;
      font-size: 16px;
      margin-right: 6px;
    }
    .nc-radio-empty {
      display: inline-block;
      width: 14px;
      height: 14px;
      border: 1px solid #c0c4cc;
      border-radius: 50%;
      margin-right: 6px;
    }
    .nc-mode-title {
      font-weight: 500;
    }
  }
  .nc-mode-desc {
    font-size: 12px;
    color: #909399;
    line-height: 1.4;
  }
}

/* 分区标题 */
.nc-section {
  margin-top: 24px;
}
.nc-section-title {
  position: relative;
  padding-left: 10px;
  margin-bottom: 14px;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  &::before {
    content: "";
    position: absolute;
    left: 0;
    top: 2px;
    width: 3px;
    height: 16px;
    background: $primary;
    border-radius: 2px;
  }
}

/* 功能配置卡片 */
.nc-func-card,
.nc-notify-card {
  border: 1px solid $border;
  border-radius: 6px;
  padding: 10px 14px;
  margin-bottom: 10px;
}
.nc-func-row,
.nc-notify-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.nc-func-left {
  display: flex;
  align-items: center;
  .nc-func-name {
    margin-left: 10px;
  }
}
.nc-notify-name {
  font-weight: 500;
}
.nc-config-btn {
  color: $primary;
  cursor: pointer;
  font-size: 13px;
  i {
    margin-right: 2px;
  }
  &:hover {
    opacity: 0.8;
  }
}
.nc-func-detail {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed $border;
}
.nc-detail-row {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
  &:last-child {
    margin-bottom: 0;
  }
  .nc-detail-label {
    width: 72px;
    text-align: right;
    color: #606266;
    font-size: 13px;
    flex-shrink: 0;
  }
}
</style>
