<template>
  <div class="fp-panel">
    <div class="fp-panel-head">
      <span class="fp-panel-title">消防演练现场指令与响应打点</span>
      <span class="fp-panel-tip">
        创建场次 → 录入岗位 → 编排指令链 → 就绪 → 开始冻结 → 执行打点（下达 / 确认 / 暂停恢复 / 超时 / 跳过阻塞）→ 结束
      </span>
      <div class="fp-space">
        <el-select v-model="statusFilter" size="small" style="width: 130px" placeholder="全部状态" clearable @change="load">
          <el-option v-for="s in statusOptions" :key="s.code" :label="s.label" :value="s.code" />
        </el-select>
        <el-button size="small" plain @click="load">刷新</el-button>
        <el-button size="small" type="primary" @click="onCreate">+ 新建演练场次</el-button>
      </div>
    </div>

    <div class="fp-panel-body">
      <el-table :data="rows" border stripe row-key="id" v-loading="loading">
        <el-table-column prop="drillNo" label="演练编号" width="130" />
        <el-table-column prop="title" label="演练名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="92">
          <template #default="{ row }">
            <el-tag size="small" :type="SESSION_STATUS_TAG[row.status] || 'info'" effect="dark">
              {{ row.statusName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="buildingName" label="关联建筑" width="160" />
        <el-table-column prop="organizer" label="组织者" width="90" />
        <el-table-column label="岗/卡" width="90">
          <template #default="{ row }">{{ row.postCount }} 岗 / {{ row.cardCount }} 卡</template>
        </el-table-column>
        <el-table-column label="打点进展" min-width="230">
          <template #default="{ row }">
            <el-tag size="small" type="warning" effect="plain" v-if="row.waitingCount">等待 {{ row.waitingCount }}</el-tag>
            <el-tag size="small" type="success" effect="plain" v-if="row.confirmedCount">确认 {{ row.confirmedCount }}</el-tag>
            <el-tag size="small" type="danger" effect="plain" v-if="row.timeoutCount">超时 {{ row.timeoutCount }}</el-tag>
            <el-tag size="small" type="danger" effect="plain" v-if="row.skippedCount">跳过 {{ row.skippedCount }}</el-tag>
            <el-tag size="small" type="danger" v-if="row.undisposedCount" style="margin-left:4px">
              {{ row.undisposedCount }} 条缺处置
            </el-tag>
            <span v-if="!row.waitingCount && !row.confirmedCount && !row.timeoutCount && !row.skippedCount" style="color:#999">
              尚未开始打点
            </span>
          </template>
        </el-table-column>
        <el-table-column label="开始 / 结束时间" width="280">
          <template #default="{ row }">
            <div style="font-size:12px;line-height:1.7">
              开始：{{ fmtTime(row.startedAt) }}<br />
              结束：{{ fmtTime(row.finishedAt) }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              link
              type="primary"
              @click="open(row)"
            >
              {{ row.status === 'DRAFT' ? '继续编排' : (['RUNNING', 'PAUSED'].includes(row.status) ? '进入执行' : '查看') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { drillApi } from '../../api'
import { SESSION_STATUS_TAG, fmtTime } from './drill-util'

const nav = useRouter()
const rows = ref([])
const loading = ref(false)
const statusFilter = ref('')

const statusOptions = [
  { code: 'DRAFT', label: '草稿' },
  { code: 'READY', label: '就绪' },
  { code: 'RUNNING', label: '进行中' },
  { code: 'PAUSED', label: '暂停' },
  { code: 'FINISHED', label: '已完成' },
  { code: 'CANCELED', label: '已取消' }
]

async function load() {
  loading.value = true
  try {
    rows.value = await drillApi.list(statusFilter.value || undefined)
  } finally {
    loading.value = false
  }
}

async function onCreate() {
  const detail = await drillApi.create({})
  nav.push(`/drills/${detail.id}/edit`)
}

function open(row) {
  if (row.status === 'DRAFT' || row.status === 'READY') {
    nav.push(`/drills/${row.id}/edit`)
  } else {
    nav.push(`/drills/${row.id}/run`)
  }
}

onMounted(load)
</script>
