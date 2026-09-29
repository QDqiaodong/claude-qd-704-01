<template>
  <div class="fp-panel">
    <div class="fp-panel-head">
      <span class="fp-panel-title">设施停用许可</span>
      <span class="fp-panel-tip">
        选点停用 · 逐项补偿措施 · 批准生效 · 复位核验 · 关闭解除标记；原状态（正常/故障/待更换/停用）始终保留
      </span>
      <div class="fp-space">
        <el-select v-model="statusFilter" size="small" style="width: 140px" placeholder="全部状态" clearable @change="load">
          <el-option v-for="s in statusOptions" :key="s.code" :label="s.label" :value="s.code" />
        </el-select>
        <el-button size="small" type="primary" @click="openCreate">+ 新增停用许可</el-button>
        <el-button size="small" plain @click="load">刷新</el-button>
      </div>
    </div>

    <div class="fp-panel-body">
      <el-table
        :data="rows"
        border
        stripe
        :row-class-name="rowClass"
        @row-click="openDetail"
        row-key="id"
      >
        <el-table-column prop="permitNo" label="许可编号" width="130" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)" effect="dark">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="风险" width="150">
          <template #default="{ row }">
            <el-tag v-if="row.expired" size="small" type="danger">已超期未复位</el-tag>
            <el-tag v-else-if="row.expiringSoon" size="small" type="warning">即将到期</el-tag>
            <el-tag v-else-if="row.allResetPassed" size="small" type="success">核验合格待关闭</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="buildingName" label="建筑" width="170" />
        <el-table-column prop="reason" label="停用原因" min-width="220" show-overflow-tooltip />
        <el-table-column label="计划起止" width="200">
          <template #default="{ row }">{{ row.planStart }} ~ {{ row.planEnd }}</template>
        </el-table-column>
        <el-table-column prop="siteOwnerName" label="现场负责人" width="100" />
        <el-table-column label="范围/措施" width="130">
          <template #default="{ row }">
            {{ row.pointCount }} 点位 · {{ row.measureConfirmedCount }}/{{ row.measureCount }} 措施
          </template>
        </el-table-column>
        <el-table-column label="未结维保" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.unfinishedOrderCount > 0 ? 'danger' : 'info'">
              {{ row.unfinishedOrderCount }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click.stop="openDetail(row)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <PermitFormDialog v-model="formVisible" :permit="editingPermit" @saved="onSaved" />
    <PermitDetailDrawer
      v-model="drawerVisible"
      :permit-id="activeId"
      @changed="load"
      @edit="onEdit"
    />
  </div>
</template>

<script setup>
import { inject, onMounted, ref, watch } from 'vue'
import { permitApi } from '../api'
import PermitFormDialog from './PermitFormDialog.vue'
import PermitDetailDrawer from './PermitDetailDrawer.vue'

const fp = inject('fp')
const rows = ref([])
const statusFilter = ref('')
const formVisible = ref(false)
const drawerVisible = ref(false)
const activeId = ref(null)
const editingPermit = ref(null)

const statusOptions = [
  { code: 'DRAFT', label: '草稿' },
  { code: 'PENDING_APPROVAL', label: '待批准' },
  { code: 'EFFECTIVE', label: '生效' },
  { code: 'PENDING_RESET', label: '待复位' },
  { code: 'CLOSED', label: '已关闭' },
  { code: 'REVOKED', label: '已撤销' }
]

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

function rowClass({ row }) {
  if (row.expired) return 'fp-row-danger'
  return ''
}

async function load() {
  rows.value = await permitApi.list({
    buildingId: fp.buildingId.value,
    status: statusFilter.value || undefined
  })
}

function openCreate() {
  editingPermit.value = null
  formVisible.value = true
}

async function onEdit(detail) {
  drawerVisible.value = false
  editingPermit.value = detail
  formVisible.value = true
}

function onSaved(detail) {
  load()
  if (detail && ['PENDING_APPROVAL', 'EFFECTIVE', 'PENDING_RESET'].includes(detail.status)) {
    activeId.value = detail.id
    drawerVisible.value = true
  }
}

function openDetail(row) {
  activeId.value = row.id
  drawerVisible.value = true
}

watch(() => fp.buildingId.value, load)
onMounted(load)
</script>
