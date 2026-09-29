<template>
  <el-drawer
    :model-value="modelValue"
    :title="detail ? detail.permitNo + ' · 设施停用许可' : '设施停用许可'"
    size="72%"
    :before-close="close"
  >
    <div v-if="detail" class="pd-wrap">
      <!-- 头部状态条 -->
      <div class="pd-head">
        <el-tag size="large" :type="statusTag(detail.status)" effect="dark">{{ detail.statusName }}</el-tag>
        <el-tag v-if="detail.expired" size="large" type="danger" effect="plain">已超期未复位</el-tag>
        <el-tag v-else-if="detail.expiringSoon" size="large" type="warning" effect="plain">即将到期</el-tag>
        <el-tag v-if="detail.allResetPassed" size="large" type="success" effect="plain">复位核验全部合格·待关闭</el-tag>
        <span class="pd-head-version">数据版本 v{{ detail.version }}</span>
      </div>

      <el-descriptions :column="2" size="small" border style="margin-top: 10px">
        <el-descriptions-item label="所属建筑" :span="2">{{ detail.buildingName }}</el-descriptions-item>
        <el-descriptions-item label="停用原因" :span="2">{{ detail.reason }}</el-descriptions-item>
        <el-descriptions-item label="计划起止">
          {{ detail.planStart }} ~ {{ detail.planEnd }}
        </el-descriptions-item>
        <el-descriptions-item label="现场负责人">{{ detail.siteOwnerName }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detail.createdBy }}</el-descriptions-item>
        <el-descriptions-item label="范围锁定">
          <el-tag size="small" :type="detail.scopeLocked ? 'danger' : 'info'">
            {{ detail.scopeLocked ? '已锁定（不可改点位）' : '未锁定（草稿可改）' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="批准">{{ detail.approvedBy || '—' }} {{ detail.approvedAt ? fmt(detail.approvedAt) : '' }}</el-descriptions-item>
        <el-descriptions-item label="关闭">{{ detail.closedBy || '—' }} {{ detail.closedAt ? fmt(detail.closedAt) : '' }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.rejectReason" label="拒绝意见" :span="2">
          <span style="color: #c62828">{{ detail.rejectReason }}</span>
        </el-descriptions-item>
        <el-descriptions-item v-if="detail.revokeReason" label="撤销原因" :span="2">
          <span style="color: #607d8b">{{ detail.revokeReason }}</span>
        </el-descriptions-item>
        <el-descriptions-item v-if="detail.lastVerifyFailReason" label="最近核验失败" :span="2">
          <span style="color: #c62828">{{ detail.lastVerifyFailReason }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <!-- 动作按钮条：按角色与状态出现 -->
      <div class="pd-actions">
        <el-button v-if="can('edit')" size="small" @click="$emit('edit', detail)">编辑草稿</el-button>
        <el-button v-if="can('submit')" size="small" type="primary" @click="act('submit')">提交批准</el-button>
        <el-button v-if="can('approve')" size="small" type="success" @click="act('approve')">批准生效</el-button>
        <el-button v-if="can('approve')" size="small" type="warning" @click="openReject">拒绝退回</el-button>
        <el-button v-if="can('revoke')" size="small" type="info" @click="openRevoke">撤销许可</el-button>
        <el-button v-if="can('reset')" size="small" type="warning" @click="openReset">
          {{ detail.status === 'PENDING_RESET' ? '重新发起复位核验' : '发起复位核验' }}
        </el-button>
        <el-button v-if="can('close')" size="small" type="success" @click="openClose">关闭许可并解除标记</el-button>
        <el-button size="small" @click="reload">刷新</el-button>
      </div>

      <!-- 受影响范围 -->
      <div class="pd-section-title">
        受影响范围（{{ detail.points.length }} 个点位 · 许可停用为叠加标记，原状态保留）
      </div>
      <el-table :data="detail.points" size="small" border :row-class-name="pointRowClass">
        <el-table-column prop="pointCode" label="点位编号" width="130" />
        <el-table-column prop="floorCode" label="楼层" width="70" />
        <el-table-column prop="facilityTypeName" label="设施" width="100" />
        <el-table-column prop="locationDesc" label="位置" min-width="160" />
        <el-table-column label="原状态（保留）" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="pointStatusType(row.originStatus)">{{ row.originStatusName || '—' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="许可停用" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.marked" size="small" type="danger" effect="dark">许可停用</el-tag>
            <span v-else style="color: #9e9e9e; font-size: 12px">未挂起</span>
          </template>
        </el-table-column>
        <el-table-column label="最新核验" width="200">
          <template #default="{ row }">
            <template v-if="row.lastResult">
              <el-tag size="small" :type="row.lastResult === 'PASS' ? 'success' : 'danger'">
                {{ row.lastResultName }}
              </el-tag>
              <span v-if="row.lastFailReason" style="font-size: 12px; color: #c62828; margin-left: 4px">
                {{ row.lastFailReason }}
              </span>
            </template>
            <span v-else style="color: #9e9e9e; font-size: 12px">未核验</span>
          </template>
        </el-table-column>
      </el-table>

      <!-- 补偿措施 -->
      <div class="pd-section-title">
        补偿措施（{{ detail.measures.length }} 项 · 已收口 {{ confirmedCount }}/{{ detail.measures.length }}）
      </div>
      <el-table :data="detail.measures" size="small" border>
        <el-table-column prop="measureTypeName" label="类别" width="110" />
        <el-table-column prop="content" label="内容" min-width="220" />
        <el-table-column prop="specDesc" label="数量/频次" width="160" />
        <el-table-column label="完成情况" width="240">
          <template #default="{ row }">
            <template v-if="row.confirmed">
              <el-tag size="small" type="success">已收口</el-tag>
              <span style="font-size: 12px; color: #607d8b; margin-left: 6px">
                {{ row.confirmedBy }} {{ fmt(row.confirmedAt) }}
              </span>
              <div v-if="row.confirmRemark" style="font-size: 12px; color: #7a6b66">{{ row.confirmRemark }}</div>
            </template>
            <template v-else>
              <el-tag size="small" type="warning">待确认</el-tag>
              <el-button
                v-if="can('measure')"
                size="small" link type="primary"
                style="margin-left: 8px"
                @click="confirmMeasure(row)"
              >现场确认收口</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <!-- 关联维保单 -->
      <div class="pd-section-title">
        关联未完成维保单（{{ detail.orders.length }} 张 · 复位前必须全部处理）
      </div>
      <el-table :data="detail.orders" size="small" border v-if="detail.orders.length">
        <el-table-column prop="orderNo" label="维保单号" width="140" />
        <el-table-column prop="statusName" label="状态" width="90" />
        <el-table-column prop="faultDesc" label="故障描述" min-width="180" />
        <el-table-column label="处理情况" min-width="220">
          <template #default="{ row }">
            <el-tag v-if="row.handled" size="small" type="success">已处理</el-tag>
            <el-tag v-else size="small" type="danger">未处理（卡复位）</el-tag>
            <span v-if="row.handleNote" style="font-size: 12px; color: #607d8b; margin-left: 6px">{{ row.handleNote }}</span>
            <el-button
              v-if="!row.handled && can('measure')"
              size="small" link type="primary"
              style="margin-left: 8px"
              @click="openWaiver(row)"
            >登记处理说明</el-button>
            <span v-if="!row.handled && !can('measure')" style="font-size: 12px; color: #7a6b66; margin-left: 8px">
              请先在维保看板推进，或由本许可现场负责人登记说明
            </span>
          </template>
        </el-table-column>
      </el-table>
      <div v-else class="pd-empty">没有关联的未完成维保单</div>

      <!-- 动作留痕 -->
      <div class="pd-section-title">动作留痕（按顺序追溯：操作者 / 时间 / 前后状态 / 说明）</div>
      <el-timeline>
        <el-timeline-item
          v-for="log in detail.logs"
          :key="log.id"
          :timestamp="fmt(log.createdAt)"
          placement="top"
          :type="logType(log.action)"
        >
          <div class="pd-log">
            <el-tag size="small" effect="plain">{{ log.actionName }}</el-tag>
            <span class="pd-log-operator">{{ log.operator }}（{{ log.operatorRoleName }}）</span>
            <span v-if="log.fromStatusName || log.toStatusName" class="pd-log-state">
              {{ log.fromStatusName || '—' }} → {{ log.toStatusName || '—' }}
            </span>
            <div class="pd-log-remark">{{ log.remark }}</div>
          </div>
        </el-timeline-item>
      </el-timeline>
    </div>

    <!-- 拒绝 -->
    <el-dialog v-model="rejectVisible" title="拒绝许可" width="440px" append-to-body>
      <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="必须填写拒绝意见，将退回草稿（范围锁定）" />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="warning" @click="submitReject">确认拒绝</el-button>
      </template>
    </el-dialog>

    <!-- 撤销 -->
    <el-dialog v-model="revokeVisible" title="撤销许可" width="440px" append-to-body>
      <el-alert type="info" :closable="false" style="margin-bottom: 10px"
        title="仅草稿 / 待批准可撤销；生效后的许可必须走复位关闭流程" />
      <el-input v-model="revokeReason" type="textarea" :rows="3" placeholder="必须填写撤销原因" />
      <template #footer>
        <el-button @click="revokeVisible = false">取消</el-button>
        <el-button type="info" @click="submitRevoke">确认撤销</el-button>
      </template>
    </el-dialog>

    <!-- 维保单处理说明 -->
    <el-dialog v-model="waiverVisible" title="关联维保单处理说明" width="460px" append-to-body>
      <div style="font-size: 13px; color: #7a6b66; margin-bottom: 8px">
        {{ waiverOrder && waiverOrder.orderNo }}（{{ waiverOrder && waiverOrder.statusName }}）：
        {{ waiverOrder && waiverOrder.faultDesc }}
      </div>
      <el-input v-model="waiverNote" type="textarea" :rows="3"
        placeholder="如：已另案跟踪 / 转第三方维保，与本次停用复位无关 / 停用后继续观察" />
      <template #footer>
        <el-button @click="waiverVisible = false">取消</el-button>
        <el-button type="primary" @click="submitWaiver">登记并视为已处理</el-button>
      </template>
    </el-dialog>

    <!-- 复位核验 -->
    <el-dialog v-model="resetVisible" title="逐点位复位核验" width="720px" append-to-body :close-on-click-modal="false">
      <el-alert
        :type="resetBlocked ? 'error' : 'success'"
        :closable="false"
        style="margin-bottom: 10px"
        :title="resetBlocked ? resetBlockMsg : '前置条件已满足：补偿措施已收口、关联未完成维保单已处理'"
        show-icon
      />
      <el-table :data="resetRows" size="small" border>
        <el-table-column prop="pointCode" label="点位" width="130" />
        <el-table-column prop="facilityTypeName" label="设施" width="100" />
        <el-table-column prop="locationDesc" label="位置" min-width="150" />
        <el-table-column label="核验结论" width="110">
          <template #default="{ row }">
            <el-radio-group v-model="row.result" size="small">
              <el-radio-button label="PASS">合格</el-radio-button>
              <el-radio-button label="FAIL">不合格</el-radio-button>
            </el-radio-group>
          </template>
        </el-table-column>
        <el-table-column label="核验说明 / 失败原因" min-width="220">
          <template #default="{ row }">
            <el-input
              v-if="row.result === 'FAIL'"
              v-model="row.failReason"
              size="small"
              placeholder="不合格必须填写失败原因"
            />
            <el-input v-else v-model="row.verifyDesc" size="small" placeholder="合格说明（选填）" />
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top: 8px">
        <el-input v-model="resetRemark" size="small" placeholder="本轮核验备注（选填）" />
      </div>
      <template #footer>
        <el-button @click="resetVisible = false">取消</el-button>
        <el-button type="warning" :disabled="resetBlocked" @click="submitReset">提交本轮核验</el-button>
      </template>
    </el-dialog>

    <!-- 关闭 -->
    <el-dialog v-model="closeVisible" title="关闭停用许可" width="440px" append-to-body>
      <el-alert type="warning" :closable="false" style="margin-bottom: 10px"
        title="关闭后将在同一事务中解除全部点位的「许可停用」标记；原状态保持不变。关闭不可撤销。" />
      <el-input v-model="closeRemark" type="textarea" :rows="2" placeholder="关闭说明（选填）" />
      <template #footer>
        <el-button @click="closeVisible = false">取消</el-button>
        <el-button type="success" @click="submitClose">确认关闭</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { permitApi } from '../api'
import { currentUser } from '../auth'

const props = defineProps({
  modelValue: Boolean,
  permitId: { type: Number, default: null }
})
const emit = defineEmits(['update:modelValue', 'changed', 'edit'])

const detail = ref(null)
const rejectVisible = ref(false)
const rejectReason = ref('')
const revokeVisible = ref(false)
const revokeReason = ref('')
const waiverVisible = ref(false)
const waiverOrder = ref(null)
const waiverNote = ref('')
const resetVisible = ref(false)
const resetRows = ref([])
const resetRemark = ref('')
const closeVisible = ref(false)
const closeRemark = ref('')

const confirmedCount = computed(() => (detail.value ? detail.value.measures.filter((m) => m.confirmed).length : 0))

const role = computed(() => currentUser.value && currentUser.value.role)
const isOwnerOfPermit = computed(() =>
  detail.value && currentUser.value && currentUser.value.id === detail.value.siteOwnerId)

function can(action) {
  if (!detail.value || !currentUser.value) return false
  const s = detail.value.status
  if (action === 'edit') return role.value === 'FIRE_MANAGER' && s === 'DRAFT'
  if (action === 'submit') return role.value === 'FIRE_MANAGER' && s === 'DRAFT'
  if (action === 'approve') return role.value === 'SAFETY_OFFICER' && s === 'PENDING_APPROVAL'
  if (action === 'revoke') return role.value === 'FIRE_MANAGER' && (s === 'DRAFT' || s === 'PENDING_APPROVAL')
  if (action === 'measure') {
    return role.value === 'SITE_OWNER' && isOwnerOfPermit.value
      && (s === 'EFFECTIVE' || s === 'PENDING_RESET')
  }
  if (action === 'reset') {
    return role.value === 'SITE_OWNER' && isOwnerOfPermit.value
      && (s === 'EFFECTIVE' || s === 'PENDING_RESET')
  }
  if (action === 'close') return role.value === 'FIRE_MANAGER' && s === 'PENDING_RESET'
  return false
}

const resetBlocked = computed(() => {
  if (!detail.value) return false
  return detail.value.measures.some((m) => !m.confirmed) || detail.value.orders.some((o) => !o.handled)
})
const resetBlockMsg = computed(() => {
  if (!detail.value) return ''
  const m = detail.value.measures.filter((x) => !x.confirmed).length
  const o = detail.value.orders.filter((x) => !x.handled).length
  return `前置条件未满足：${m} 项补偿措施未收口、${o} 张关联维保单未处理`
})

function statusTag(status) {
  return {
    DRAFT: 'info',
    PENDING_APPROVAL: 'warning',
    EFFECTIVE: 'success',
    PENDING_RESET: 'warning',
    CLOSED: 'success',
    REVOKED: 'info'
  }[status] || 'info'
}

function pointStatusType(status) {
  if (status === 'FAULT') return 'danger'
  if (status === 'REPLACE') return 'warning'
  if (status === 'DISABLED') return 'info'
  return 'success'
}

function pointRowClass({ row }) {
  return row.marked ? 'fp-row-danger' : ''
}

function logType(action) {
  if (action === 'APPROVE' || action === 'CLOSE') return 'success'
  if (action === 'REJECT' || action === 'REVOKE') return 'info'
  if (action === 'RESET_VERIFY') return 'warning'
  return 'primary'
}

function fmt(dt) {
  return dt ? dt.replace('T', ' ').slice(0, 16) : ''
}

function close() {
  emit('update:modelValue', false)
}

async function reload() {
  if (props.permitId) {
    detail.value = await permitApi.detail(props.permitId)
  }
}

watch(
  () => [props.modelValue, props.permitId],
  async ([visible]) => {
    if (visible && props.permitId) {
      await reload()
    }
    if (!visible) {
      detail.value = null
    }
  }
)

async function act(name) {
  try {
    if (name === 'approve') {
      await ElMessageBox.confirm('批准后立即生效并给范围内点位挂「许可停用」标记（原状态保留），确认批准？', '批准确认', {
        type: 'warning'
      })
      detail.value = await permitApi.approve(detail.value.id)
    } else if (name === 'submit') {
      if (detail.value.scopeLocked) {
        await ElMessageBox.confirm('许可范围已锁定，提交后仍不能更改点位清单；确认按原范围重新提交批准？', '重新提交确认', {
          type: 'warning'
        })
      }
      detail.value = await permitApi.submit(detail.value.id)
    }
    ElMessage.success('操作成功')
    emit('changed')
  } catch (e) {
    if (e !== 'cancel') await reload()
  }
}

function openReject() {
  rejectReason.value = ''
  rejectVisible.value = true
}

async function submitReject() {
  if (!rejectReason.value.trim()) return ElMessage.warning('请填写拒绝意见')
  detail.value = await permitApi.reject(detail.value.id, { reason: rejectReason.value })
  rejectVisible.value = false
  ElMessage.success('已拒绝并退回草稿')
  emit('changed')
}

function openRevoke() {
  revokeReason.value = ''
  revokeVisible.value = true
}

async function submitRevoke() {
  if (!revokeReason.value.trim()) return ElMessage.warning('请填写撤销原因')
  detail.value = await permitApi.revoke(detail.value.id, { reason: revokeReason.value })
  revokeVisible.value = false
  ElMessage.success('许可已撤销')
  emit('changed')
}

async function confirmMeasure(row) {
  const { value } = await ElMessageBox.prompt('确认该项补偿措施已现场收口（可填说明）', '措施确认', {
    confirmButtonText: '确认收口',
    cancelButtonText: '取消',
    inputValue: row.confirmRemark || '',
    inputType: 'textarea'
  }).catch(() => ({ value: null }))
  if (value === null || value === undefined) return
  detail.value = await permitApi.confirmMeasure(detail.value.id, row.id, { remark: value || null })
  ElMessage.success('措施已确认收口')
  emit('changed')
}

function openWaiver(order) {
  waiverOrder.value = order
  waiverNote.value = order.handleNote || ''
  waiverVisible.value = true
}

async function submitWaiver() {
  if (!waiverNote.value.trim()) return ElMessage.warning('请填写处理说明')
  detail.value = await permitApi.waiveOrder(detail.value.id, waiverOrder.value.orderId, {
    handleNote: waiverNote.value
  })
  waiverVisible.value = false
  ElMessage.success('已登记处理说明')
  emit('changed')
}

function openReset() {
  resetRows.value = detail.value.points.map((p) => ({
    pointId: p.pointId,
    pointCode: p.pointCode,
    facilityTypeName: p.facilityTypeName,
    locationDesc: p.locationDesc,
    result: p.lastResult === 'FAIL' ? '' : (p.lastResult || ''),
    verifyDesc: '',
    failReason: ''
  }))
  resetRemark.value = ''
  resetVisible.value = true
}

async function submitReset() {
  if (resetRows.value.some((r) => !r.result)) return ElMessage.warning('必须逐个点位选择合格 / 不合格')
  if (resetRows.value.some((r) => r.result === 'FAIL' && !r.failReason.trim())) {
    return ElMessage.warning('不合格点位必须填写失败原因')
  }
  detail.value = await permitApi.resetVerify(detail.value.id, {
    version: detail.value.version,
    remark: resetRemark.value || null,
    items: resetRows.value
  })
  resetVisible.value = false
  const failed = resetRows.value.filter((r) => r.result === 'FAIL').length
  if (failed) {
    ElMessage.warning(`已提交：${failed} 个点位不合格，许可保持待复位，整改后可再次核验`)
  } else {
    ElMessage.success('全部合格，许可待消防主管关闭')
  }
  emit('changed')
}

function openClose() {
  closeRemark.value = ''
  closeVisible.value = true
}

async function submitClose() {
  detail.value = await permitApi.close(detail.value.id, {
    version: detail.value.version,
    remark: closeRemark.value || null
  })
  closeVisible.value = false
  ElMessage.success('许可已关闭，全部点位许可停用标记已解除')
  emit('changed')
}

defineExpose({ reload })
</script>

<style scoped>
.pd-wrap {
  padding: 0 6px 20px;
}
.pd-head {
  display: flex;
  gap: 8px;
  align-items: center;
}
.pd-head-version {
  margin-left: auto;
  font-size: 12px;
  color: #90a4ae;
}
.pd-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  padding: 12px 0;
}
.pd-section-title {
  margin: 16px 0 8px;
  font-weight: 700;
  color: #a3310f;
  font-size: 14px;
}
.pd-empty {
  font-size: 12px;
  color: #9e9e9e;
  padding: 8px 0;
}
.pd-log {
  font-size: 13px;
}
.pd-log-operator {
  margin-left: 8px;
  font-weight: 600;
}
.pd-log-state {
  margin-left: 8px;
  color: #7a6b66;
  font-size: 12px;
}
.pd-log-remark {
  color: #5d4b45;
  margin-top: 2px;
}
</style>
