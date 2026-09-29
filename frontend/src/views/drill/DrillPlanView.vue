<template>
  <div class="dr-plan">
    <!-- 表头 -->
    <div class="fp-panel">
      <div class="fp-panel-head dr-head">
        <div>
          <el-button size="small" plain @click="goList">← 场次列表</el-button>
          <span class="fp-panel-title" style="margin-left: 8px">编排 · {{ detail.drillNo }} {{ detail.title }}</span>
        </div>
        <div class="dr-head-actions">
          <el-tag :type="statusTag(detail.status)" effect="dark">{{ detail.statusName }}</el-tag>
          <el-tag v-if="detail.chainFrozen" type="warning" effect="dark">指令链已冻结</el-tag>
          <template v-if="!frozen">
            <el-dropdown trigger="click" @command="onApplyScript" @visible-change="(v) => v && loadScripts()">
              <el-button size="small" plain>套用常用脚本 ▾</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-for="s in scripts" :key="s.id" :command="s.id">
                    {{ s.name }}（{{ s.cardCount }} 张卡）
                  </el-dropdown-item>
                  <el-dropdown-item v-if="scripts.length === 0" disabled>暂无脚本</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button size="small" type="primary" :loading="saving" @click="onSave">保存编排</el-button>
            <el-button size="small" type="warning" :loading="acting" @click="onReady">保存并就绪（冻结）</el-button>
            <el-button size="small" type="success" :loading="acting" @click="onStartAndRun">保存并直接开始</el-button>
          </template>
          <el-button v-else size="small" type="success" @click="goRun">进入执行页 →</el-button>
        </div>
      </div>
      <div class="fp-panel-body">
        <div class="dr-meta">
          <span>组织者：<b>{{ detail.organizerName }}</b></span>
          <span>场地：{{ detail.buildingName || '—' }}</span>
          <span>创建：{{ fmt(detail.createdAt) }}</span>
        </div>
        <div class="dr-freeze-tip">
          就绪 / 开始后本场指令链整体冻结；常用脚本之后的修改不会回写已冻结场次。
          每张卡可设多个前置节点 —— 只有前置<strong>全部已确认</strong>的卡才能下达；前置被跳过会明确标出阻塞来源。
        </div>
      </div>
    </div>

    <!-- 冻结后只读展示编排 -->
    <div class="dr-grid">
      <div class="fp-panel">
        <div class="fp-panel-head"><span class="fp-panel-title">参与岗位（{{ positions.length }}）</span></div>
        <div class="fp-panel-body">
          <el-table :data="positions" border size="small">
            <el-table-column label="代号" width="90">
              <template #default="{ row }">
                <el-input v-if="!frozen" v-model="row.posCode" size="small" maxlength="32" />
                <span v-else>{{ row.posCode }}</span>
              </template>
            </el-table-column>
            <el-table-column label="岗位名称" min-width="130">
              <template #default="{ row }">
                <el-input v-if="!frozen" v-model="row.posName" size="small" maxlength="64" />
                <span v-else>{{ row.posName }}</span>
              </template>
            </el-table-column>
            <el-table-column label="岗位人员" min-width="120">
              <template #default="{ row }">
                <el-input v-if="!frozen" v-model="row.memberName" size="small" maxlength="32"
                  placeholder="姓名（选填）" />
                <span v-else>{{ row.memberName || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column v-if="!frozen" label="绑定系统用户" width="170">
              <template #default="{ row }">
                <el-select v-model="row.userId" size="small" clearable placeholder="不绑定" style="width: 100%">
                  <el-option v-for="u in siteUsers" :key="u.id" :label="u.displayName" :value="u.id" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column v-if="!frozen" label="操作" width="64">
              <template #default="{ $index }">
                <el-button size="small" link type="danger" @click="removePosition($index)">删</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-button v-if="!frozen" size="small" plain style="margin-top: 8px" @click="addPosition">+ 新增岗位</el-button>
        </div>
      </div>

      <div class="fp-panel">
        <div class="fp-panel-head">
          <span class="fp-panel-title">现场指令卡（{{ cards.length }}）· 前置链</span>
        </div>
        <div class="fp-panel-body">
          <el-table :data="cards" border size="small" :row-class-name="cardRowClass">
            <el-table-column label="顺序" width="74">
              <template #default="{ row }">
                <el-input-number v-if="!frozen" v-model="row.seqNo" :min="1" :max="999" size="small"
                  controls-position="right" style="width: 62px" />
                <b v-else>#{{ row.seqNo }}</b>
              </template>
            </el-table-column>
            <el-table-column label="指令 / 现场提示" min-width="230">
              <template #default="{ row }">
                <template v-if="!frozen">
                  <el-input v-model="row.title" size="small" maxlength="128" placeholder="指令标题" />
                  <el-input v-model="row.onSiteHint" size="small" maxlength="255" style="margin-top: 4px"
                    placeholder="现场提示（选填）" />
                </template>
                <div v-else>
                  <b>{{ row.title }}</b>
                  <div class="dr-muted">{{ row.onSiteHint || '—' }}</div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="目标岗位" width="140">
              <template #default="{ row }">
                <el-select v-if="!frozen" v-model="row.targetPosCode" size="small" style="width: 100%">
                  <el-option v-for="p in positions" :key="p.posCode" :label="p.posName" :value="p.posCode" />
                </el-select>
                <span v-else>{{ row.targetPosName }}</span>
              </template>
            </el-table-column>
            <el-table-column label="时限(秒)" width="100">
              <template #default="{ row }">
                <el-input-number v-if="!frozen" v-model="row.timeLimitSec" :min="1" :max="7200"
                  size="small" controls-position="right" style="width: 86px" />
                <span v-else>{{ row.timeLimitSec }}s</span>
              </template>
            </el-table-column>
            <el-table-column label="前置节点(顺序号)" min-width="170">
              <template #default="{ row }">
                <el-select v-if="!frozen" v-model="row.prereqSeqs" multiple size="small" style="width: 100%"
                  placeholder="无前置">
                  <el-option v-for="c in cards.filter((x) => x.seqNo !== row.seqNo)" :key="c.seqNo"
                    :label="'#' + c.seqNo + ' ' + (c.title || '未命名')" :value="c.seqNo" />
                </el-select>
                <span v-else>{{ (row.prereqs || []).map((p) => '#' + p.seqNo).join('、') || '无前置' }}</span>
              </template>
            </el-table-column>
            <el-table-column v-if="!frozen" label="操作" width="64">
              <template #default="{ $index }">
                <el-button size="small" link type="danger" @click="removeCard($index)">删</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-button v-if="!frozen" size="small" plain style="margin-top: 8px" @click="addCard">+ 新增指令卡</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { drillApi, userApi, newActionId } from '../../api'

const route = useRoute()
const nav = useRouter()
const fp = inject('fp')
const drillId = Number(route.params.id)

const detail = ref({ status: 'DRAFT', statusName: '草稿', chainFrozen: false, title: '', drillNo: '' })
const positions = ref([])
const cards = ref([])
const scripts = ref([])
const siteUsers = ref([])
const saving = ref(false)
const acting = ref(false)

const frozen = computed(() => detail.value.chainFrozen || !['DRAFT'].includes(detail.value.status))

function statusTag(s) {
  return { DRAFT: 'info', READY: 'warning', RUNNING: 'success', PAUSED: 'warning', COMPLETED: 'success', CANCELED: 'info' }[s] || 'info'
}
function fmt(t) {
  return t ? t.replace('T', ' ').slice(0, 19) : ''
}
function cardRowClass({ row }) {
  return row.prereqSeqs && row.prereqSeqs.length ? 'dr-row-prereq' : ''
}

function goList() {
  nav.push('/drills')
}
function goRun() {
  nav.push(`/drills/${drillId}/run`)
}

async function load() {
  const d = await drillApi.detail(drillId)
  detail.value = d
  positions.value = (d.positions || []).map((p) => ({
    posCode: p.posCode,
    posName: p.posName,
    memberName: p.memberName || '',
    userId: p.userId,
    contact: p.contact || ''
  }))
  cards.value = (d.cards || []).map((c) => ({
    seqNo: c.seqNo,
    title: c.title,
    targetPosCode: c.targetPosCode,
    targetPosName: c.targetPosName,
    timeLimitSec: c.timeLimitSec,
    onSiteHint: c.onSiteHint || '',
    prereqSeqs: (c.prereqs || []).map((p) => p.seqNo)
  }))
  if (!['DRAFT'].includes(d.status)) {
    // 冻结后直接由执行页承担，编排页只读
  }
}

async function loadUsers() {
  try {
    siteUsers.value = await userApi.list()
    siteUsers.value = siteUsers.value.filter((u) => u.role === 'SITE_OWNER')
  } catch (e) {
    siteUsers.value = []
  }
}

async function loadScripts() {
  scripts.value = await drillApi.scripts()
}

function addPosition() {
  positions.value.push({ posCode: '', posName: '', memberName: '', userId: null, contact: '' })
}
function removePosition(i) {
  const code = positions.value[i].posCode
  positions.value.splice(i, 1)
  cards.value.forEach((c) => {
    if (c.targetPosCode === code) c.targetPosCode = ''
  })
}
function addCard() {
  const used = new Set(cards.value.map((c) => c.seqNo))
  let seq = 1
  while (used.has(seq)) seq++
  cards.value.push({
    seqNo: seq,
    title: '',
    targetPosCode: positions.value[0] ? positions.value[0].posCode : '',
    timeLimitSec: 60,
    onSiteHint: '',
    prereqSeqs: []
  })
}
function removeCard(i) {
  const seq = cards.value[i].seqNo
  cards.value.splice(i, 1)
  cards.value.forEach((c) => {
    c.prereqSeqs = (c.prereqSeqs || []).filter((x) => x !== seq)
  })
}

function validateDraft() {
  if (positions.value.length === 0) throw new Error('请至少录入一个参与岗位')
  const codes = new Set()
  for (const p of positions.value) {
    if (!p.posCode || !p.posName.trim()) throw new Error('岗位代号与名称必填')
    if (codes.has(p.posCode.trim())) throw new Error('岗位代号重复：' + p.posCode)
    codes.add(p.posCode.trim())
  }
  if (cards.value.length === 0) throw new Error('请至少编排一张现场指令卡')
  const seqs = new Set()
  for (const c of cards.value) {
    if (!c.seqNo || c.seqNo <= 0) throw new Error('指令卡顺序号必须为正整数')
    if (seqs.has(c.seqNo)) throw new Error('指令卡顺序号重复：' + c.seqNo)
    seqs.add(c.seqNo)
    if (!c.title.trim()) throw new Error(`第 ${c.seqNo} 张卡标题必填`)
    if (!c.targetPosCode) throw new Error(`第 ${c.seqNo} 张卡必须选择目标岗位`)
    if (!c.timeLimitSec || c.timeLimitSec <= 0) throw new Error(`第 ${c.seqNo} 张卡时限必须为正整数秒`)
    for (const pre of c.prereqSeqs || []) {
      if (pre === c.seqNo || !seqs.has(pre)) throw new Error(`第 ${c.seqNo} 张卡前置顺序号非法：${pre}`)
    }
  }
}

function payload() {
  return {
    clientActionId: newActionId(),
    positions: positions.value.map((p) => ({
      posCode: p.posCode.trim(),
      posName: p.posName.trim(),
      memberName: p.memberName || null,
      userId: p.userId,
      contact: p.contact || null
    })),
    cards: [...cards.value]
      .sort((a, b) => a.seqNo - b.seqNo)
      .map((c) => ({
        seqNo: c.seqNo,
        title: c.title.trim(),
        targetPosCode: c.targetPosCode,
        timeLimitSec: c.timeLimitSec,
        onSiteHint: c.onSiteHint || null,
        prereqSeqs: (c.prereqSeqs || []).slice().sort((a, b) => a - b)
      }))
  }
}

async function onSave() {
  try {
    validateDraft()
  } catch (e) {
    ElMessage.warning(e.message)
    return
  }
  saving.value = true
  try {
    await drillApi.saveDraft(drillId, payload())
    ElMessage.success('编排已保存')
    await load()
  } finally {
    saving.value = false
  }
}

async function saveThenAction(action) {
  try {
    validateDraft()
  } catch (e) {
    ElMessage.warning(e.message)
    return false
  }
  await drillApi.saveDraft(drillId, payload())
  await load()
  return true
}

async function onReady() {
  if (!(await saveThenAction('ready'))) return
  acting.value = true
  try {
    await drillApi.ready(drillId, { clientActionId: newActionId() })
    ElMessage.success('已就绪，指令链冻结')
    nav.push(`/drills/${drillId}/run`)
  } finally {
    acting.value = false
  }
}

async function onStartAndRun() {
  if (!(await saveThenAction('start'))) return
  acting.value = true
  try {
    await drillApi.start(drillId, { clientActionId: newActionId() })
    ElMessage.success('演练已开始')
    nav.push(`/drills/${drillId}/run`)
  } finally {
    acting.value = false
  }
}

async function onApplyScript(scriptId) {
  try {
    await ElMessageBox.confirm('套用脚本会用脚本内容替换当前草稿的岗位与指令卡（仅草稿可操作），是否继续？',
      '套用常用脚本', { type: 'warning' })
  } catch (e) {
    return
  }
  await drillApi.applyScript(drillId, { scriptId, clientActionId: newActionId() })
  ElMessage.success('已套用脚本（独立副本）')
  await load()
}

onMounted(async () => {
  await Promise.all([load(), loadUsers(), loadScripts()])
  if (frozen.value) {
    ElMessage.info('该场次指令链已冻结，编排只读')
  }
})
</script>

<style scoped>
.dr-head {
  flex-wrap: wrap;
  gap: 10px;
}
.dr-head-actions {
  margin-left: auto;
  display: flex;
  gap: 8px;
  align-items: center;
}
.dr-meta {
  display: flex;
  gap: 22px;
  font-size: 13px;
  color: var(--fp-text-sub);
  padding-bottom: 8px;
}
.dr-freeze-tip {
  background: var(--fp-warn-soft);
  border: 1px dashed #e8c37a;
  color: #8a5a00;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 12.5px;
}
.dr-grid {
  display: grid;
  grid-template-columns: minmax(320px, 0.9fr) minmax(520px, 1.6fr);
  gap: 14px;
  margin-top: 14px;
}
.dr-muted {
  color: var(--fp-text-sub);
  font-size: 12px;
}
:deep(.dr-row-prereq) td {
  background: #fffaf2 !important;
}
@media (max-width: 1200px) {
  .dr-grid {
    grid-template-columns: 1fr;
  }
}
</style>
