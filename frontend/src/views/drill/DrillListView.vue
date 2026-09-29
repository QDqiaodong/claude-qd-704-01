<template>
  <div class="fp-panel">
    <div class="fp-panel-head">
      <span class="fp-panel-title">消防演练 · 现场指令与响应打点</span>
      <span class="fp-panel-tip">
        场次开始即冻结指令链；倒计时以服务端时间为准；每次动作只追加事件，刷新 / 重启 / 多页操作口径一致
      </span>
      <div class="fp-space">
        <el-select v-model="statusFilter" size="small" style="width: 130px" placeholder="全部状态" clearable @change="load">
          <el-option v-for="s in statusOptions" :key="s.code" :label="s.label" :value="s.code" />
        </el-select>
        <el-button size="small" plain @click="load">刷新</el-button>
        <el-button size="small" plain @click="openScriptMgr">常用脚本</el-button>
        <el-button size="small" type="primary" @click="openCreate">+ 新建演练场次</el-button>
      </div>
    </div>

    <!-- 常用脚本管理 -->
    <el-dialog v-model="scriptMgrVisible" title="常用演练脚本（与场次相互独立，修改不影响已开始场次）" width="760px">
      <el-table :data="scripts" border size="small">
        <el-table-column prop="name" label="脚本名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="scenario" label="适用场景" min-width="220" show-overflow-tooltip />
        <el-table-column label="卡数" width="70">
          <template #default="{ row }">{{ row.cardCount }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="viewScript(row)">查看卡片</el-button>
            <el-button size="small" link type="danger" @click="removeScript(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="scripts.length === 0" description="暂无脚本" :image-size="60" />
      <template #footer>
        <el-button @click="scriptMgrVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 脚本卡查看 -->
    <el-dialog v-model="scriptDetailVisible" :title="scriptDetail ? '脚本 · ' + scriptDetail.name : ''" width="720px">
      <el-table v-if="scriptDetail" :data="scriptDetail.cards" border size="small">
        <el-table-column label="顺序" width="60">
          <template #default="{ row }">#{{ row.seqNo }}</template>
        </el-table-column>
        <el-table-column prop="title" label="指令" min-width="180" show-overflow-tooltip />
        <el-table-column prop="targetPosCode" label="目标岗位代号" width="120" />
        <el-table-column label="时限" width="70">
          <template #default="{ row }">{{ row.timeLimitSec }}s</template>
        </el-table-column>
        <el-table-column label="前置" width="90">
          <template #default="{ row }">{{ (row.prereqSeqs || []).map((s) => '#' + s).join('、') || '无' }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <div class="fp-panel-body">
      <el-table :data="rows" border stripe row-key="id">
        <el-table-column prop="drillNo" label="演练编号" width="130" />
        <el-table-column label="状态" width="92">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)" effect="dark">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="演练名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="buildingName" label="场地建筑" width="170" />
        <el-table-column label="指令进度" width="210">
          <template #default="{ row }">
            <span class="dr-progress">
              <el-tag size="small" type="success" effect="plain">确认 {{ row.confirmedCount }}</el-tag>
              <el-tag size="small" type="danger" effect="plain">超时 {{ row.timeoutCount }}</el-tag>
              <el-tag size="small" type="info" effect="plain">跳过 {{ row.skippedCount }}</el-tag>
              <el-tag v-if="row.waitingCount" size="small" type="warning" effect="dark">等待 {{ row.waitingCount }}</el-tag>
              <el-tag v-if="row.pendingCount" size="small" type="info">待下达 {{ row.pendingCount }}</el-tag>
              <span class="dr-progress-total">/ {{ row.cardTotal }}</span>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="冻结" width="66">
          <template #default="{ row }">
            <el-tag v-if="row.chainFrozen" size="small" type="warning">已冻结</el-tag>
            <span v-else class="dr-muted">编排中</span>
          </template>
        </el-table-column>
        <el-table-column prop="organizerName" label="组织者" width="90" />
        <el-table-column label="开始/结束" width="230">
          <template #default="{ row }">
            <div class="dr-time-cell">{{ fmt(row.startedAt) }}</div>
            <div class="dr-time-cell dr-muted">{{ row.finishedAt ? fmt(row.finishedAt) : '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="open(row)">
              {{ ['DRAFT'].includes(row.status) ? '编排' : (['READY', 'RUNNING', 'PAUSED'].includes(row.status) ? '执行' : '时间线') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新建演练 -->
    <el-dialog v-model="createVisible" title="新建演练场次" width="520px">
      <el-form label-width="88px">
        <el-form-item label="演练名称" required>
          <el-input v-model="form.title" maxlength="128" placeholder="如：2026 秋季灭火疏散演练" />
        </el-form-item>
        <el-form-item label="场地建筑">
          <el-select v-model="form.buildingId" clearable filterable placeholder="可不选" style="width: 100%">
            <el-option v-for="b in fp.buildings.value" :key="b.id" :label="b.code + ' · ' + b.name" :value="b.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="场景说明">
          <el-input v-model="form.scenario" type="textarea" :rows="2" maxlength="255"
            placeholder="起火部位 / 假设条件（选填）" />
        </el-form-item>
        <el-form-item label="初始内容">
          <el-radio-group v-model="form.mode">
            <el-radio label="blank">空白编排</el-radio>
            <el-radio label="script" :disabled="scripts.length === 0">套用常用脚本</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.mode === 'script'" label="选择脚本">
          <el-select v-model="form.scriptId" style="width: 100%" placeholder="选择常用脚本">
            <el-option v-for="s in scripts" :key="s.id"
              :label="s.name + '（' + s.cardCount + ' 张卡）'" :value="s.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">创建并进入编排</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { inject, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { drillApi } from '../../api'

const fp = inject('fp')
const nav = useRouter()
const rows = ref([])
const statusFilter = ref('')
const scripts = ref([])
const createVisible = ref(false)
const scriptMgrVisible = ref(false)
const scriptDetailVisible = ref(false)
const scriptDetail = ref(null)
const creating = ref(false)
const form = ref({ title: '', buildingId: null, scenario: '', mode: 'blank', scriptId: null })

const statusOptions = [
  { code: 'DRAFT', label: '草稿' },
  { code: 'READY', label: '就绪' },
  { code: 'RUNNING', label: '进行中' },
  { code: 'PAUSED', label: '暂停' },
  { code: 'COMPLETED', label: '已完成' },
  { code: 'CANCELED', label: '已取消' }
]

function statusTag(s) {
  return { DRAFT: 'info', READY: 'warning', RUNNING: 'success', PAUSED: 'warning', COMPLETED: 'success', CANCELED: 'info' }[s] || 'info'
}

function fmt(t) {
  return t ? t.replace('T', ' ').slice(0, 19) : ''
}

async function load() {
  rows.value = await drillApi.list(statusFilter.value || undefined)
}

function open(row) {
  if (row.status === 'DRAFT') {
    nav.push(`/drills/${row.id}/plan`)
  } else {
    nav.push(`/drills/${row.id}/run`)
  }
}

async function openCreate() {
  form.value = { title: '', buildingId: fp.buildingId.value || null, scenario: '', mode: 'blank', scriptId: null }
  try {
    scripts.value = await drillApi.scripts()
  } catch (e) {
    scripts.value = []
  }
  createVisible.value = true
}

async function viewScript(row) {
  scriptDetail.value = await drillApi.scriptDetail(row.id)
  scriptDetailVisible.value = true
}

async function openScriptMgr() {
  scripts.value = await drillApi.scripts()
  scriptMgrVisible.value = true
}

async function removeScript(row) {
  try {
    await ElMessageBox.confirm(`删除脚本「${row.name}」不影响已套用该脚本的演练场次，确认删除？`, '删除脚本', { type: 'warning' })
  } catch (e) {
    return
  }
  await drillApi.deleteScript(row.id)
  ElMessage.success('脚本已删除')
  scripts.value = await drillApi.scripts()
}

async function submitCreate() {
  if (!form.value.title.trim()) {
    return
  }
  creating.value = true
  try {
    const payload = {
      title: form.value.title.trim(),
      buildingId: form.value.buildingId,
      scenario: form.value.scenario || null
    }
    if (form.value.mode === 'script' && form.value.scriptId) {
      payload.scriptId = form.value.scriptId
    }
    const d = await drillApi.create(payload)
    createVisible.value = false
    nav.push(`/drills/${d.id}/plan`)
  } finally {
    creating.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.dr-progress {
  display: inline-flex;
  gap: 4px;
  flex-wrap: wrap;
  align-items: center;
}
.dr-progress-total {
  font-size: 12px;
  color: var(--fp-text-sub);
}
.dr-muted {
  color: var(--fp-text-sub);
  font-size: 12px;
}
.dr-time-cell {
  font-size: 12px;
  line-height: 18px;
}
</style>
