<template>
  <div class="dr-run">
    <!-- 顶部状态条 -->
    <div class="fp-panel">
      <div class="fp-panel-head dr-run-head">
        <div>
          <el-button size="small" plain @click="goList">← 场次列表</el-button>
          <el-button size="small" plain @click="goPlan">编排页</el-button>
          <span class="fp-panel-title" style="margin-left: 8px">
            执行 · {{ d.drillNo }} {{ d.title }}
          </span>
        </div>
        <div class="dr-run-actions">
          <el-tag :type="statusTag(d.status)" effect="dark" size="large">{{ d.statusName }}</el-tag>
          <el-tag v-if="d.chainFrozen" type="warning">指令链已冻结</el-tag>
          <el-button v-if="d.canStart" size="small" type="success" :loading="acting" @click="act('start')">
            {{ d.status === 'DRAFT' ? '开始演练' : '继续进入进行中' }}
          </el-button>
          <el-button v-if="d.canPause" size="small" type="warning" :loading="acting" @click="act('pause')">暂停</el-button>
          <el-button v-if="d.canResume" size="small" type="primary" :loading="acting" @click="act('resume')">恢复</el-button>
          <el-button v-if="d.canCancel" size="small" plain type="danger" :loading="acting" @click="onCancel">取消演练</el-button>
          <el-button v-if="canFinishNow" size="small" type="success" :loading="acting" @click="onFinish">结束演练</el-button>
          <el-button size="small" plain @click="reload">刷新</el-button>
        </div>
      </div>
      <div class="fp-panel-body">
        <div class="dr-run-meta">
          <span>组织者 <b>{{ d.organizerName }}</b></span>
          <span>场地 {{ d.buildingName || '—' }}</span>
          <span>开始 {{ fmt(d.startedAt) }}</span>
          <span v-if="d.pausedAt">暂停于 {{ fmt(d.pausedAt) }}</span>
          <span>累计暂停 {{ d.pausedTotalSec }}s</span>
          <span class="dr-clock">服务端 {{ clockText }}</span>
        </div>
        <div v-if="d.status === 'PAUSED'" class="dr-pause-banner">
          ⏸ 演练已暂停：所有剩余响应时间已冻结，恢复后从剩余值继续，暂停期间不计时、不会超时
        </div>
        <div v-if="d.status === 'READY'" class="dr-ready-banner">
          指令链已冻结、等待开始。开始后倒计时以服务端时间为准，浏览器刷新 / 关闭重开不影响计时
        </div>
        <div v-if="d.finishBlockers && d.finishBlockers.length" class="dr-blocker-banner">
          <div>结束演练前还有 {{ d.finishBlockers.length }} 项缺项：</div>
          <div v-for="b in d.finishBlockers" :key="b.cardId" class="dr-blocker-item">
            #{{ b.seqNo }}「{{ b.title }}」· {{ b.statusName }} —— {{ b.reason }}
          </div>
        </div>
      </div>
    </div>

    <!-- 当前节点 -->
    <div class="fp-panel dr-current-panel">
      <div class="fp-panel-head"><span class="fp-panel-title">📍 当前节点</span></div>
      <div class="fp-panel-body">
        <template v-if="d.currentCard">
          <div class="dr-current" :class="{ 'is-paused': d.status === 'PAUSED', 'is-danger': liveRemaining <= 10 && d.timingRunning }">
            <div class="dr-current-main">
              <div class="dr-current-seq">#{{ d.currentCard.seqNo }}</div>
              <div class="dr-current-body">
                <div class="dr-current-title">{{ d.currentCard.title }}</div>
                <div class="dr-current-hint" v-if="d.currentCard.onSiteHint">现场提示：{{ d.currentCard.onSiteHint }}</div>
                <div class="dr-current-target">
                  目标岗位：<b>{{ d.currentCard.targetPosName }}（{{ d.currentCard.targetPosCode }}）</b>
                  <span v-if="d.currentCard.targetMemberName"> · {{ d.currentCard.targetMemberName }}</span>
                </div>
              </div>
            </div>
            <div class="dr-current-count">
              <div class="dr-count-num">{{ formatRemain(liveRemaining) }}</div>
              <div class="dr-count-label">
                {{ d.status === 'PAUSED' ? '剩余秒数（已冻结）' : '剩余秒数（服务端时间）' }}
              </div>
              <div class="dr-count-limit">时限 {{ d.currentCard.timeLimitSec }}s · 截止 {{ timeOnly(d.currentCard.deadlineAt) }}</div>
            </div>
            <div class="dr-current-actions">
              <el-button type="primary" :disabled="!canConfirmCurrent" @click="openConfirm(d.currentCard)">
                {{ confirmLabel(d.currentCard) }}
              </el-button>
              <el-button plain type="danger" @click="openSkip(d.currentCard)">跳过节点</el-button>
            </div>
          </div>
        </template>
        <div v-else class="dr-empty-current">
          <span v-if="d.status === 'RUNNING' || d.status === 'PAUSED'">当前没有等待响应的卡 —— 请在下方「下一可执行节点」下达指令</span>
          <span v-else-if="d.status === 'READY'">点击右上角「开始演练」启动指令链</span>
          <span v-else>{{ d.statusName }}：指令链{{ allTerminal ? '已全部终态' : '未启动' }}</span>
        </div>
      </div>
    </div>

    <div class="dr-two-col">
      <!-- 左：节点链 -->
      <div class="fp-panel">
        <div class="fp-panel-head">
          <span class="fp-panel-title">现场指令链（{{ d.cardTotal }}）</span>
          <span class="fp-panel-tip">前置全部「已确认」才允许下达；被跳过 / 未处置超时的前置会标出阻塞来源</span>
        </div>
        <div class="fp-panel-body dr-chain">
          <div v-for="c in d.cards" :key="c.id" class="dr-node" :class="nodeClass(c)">
            <div class="dr-node-top">
              <span class="dr-node-seq">#{{ c.seqNo }}</span>
              <span class="dr-node-title">{{ c.title }}</span>
              <el-tag size="small" :type="cardTag(c.status)" effect="dark">{{ c.statusName }}</el-tag>
              <span v-if="c.lateAttempt" class="dr-late-flag">有迟到尝试</span>
            </div>
            <div class="dr-node-meta">
              <span>→ {{ c.targetPosName }}（{{ c.targetPosCode }}）</span>
              <span>时限 {{ c.timeLimitSec }}s</span>
              <span v-if="c.status === 'WAITING'" class="dr-node-remain"
                :class="{ danger: c === d.currentCard && liveRemaining <= 10 }">
                剩余 {{ formatRemain(c === d.currentCard ? liveRemaining : c.remainingSec) }}
              </span>
            </div>
            <div v-if="c.onSiteHint" class="dr-node-hint">{{ c.onSiteHint }}</div>
            <div v-if="c.prereqs && c.prereqs.length" class="dr-prereqs">
              前置：
              <el-tooltip v-for="p in c.prereqs" :key="p.cardId" placement="top"
                :disabled="!p.dispositionNote" :content="'处置结论：' + p.dispositionNote">
                <el-tag size="small"
                  :type="p.satisfied ? 'success' : (p.blocking ? 'danger' : (p.disposed ? 'warning' : 'info'))"
                  effect="plain">
                  #{{ p.seqNo }} {{ p.statusName }}<span v-if="p.disposed">·已处置</span>
                </el-tag>
              </el-tooltip>
            </div>
            <div v-if="c.blockedBy && c.blockedBy.length" class="dr-block-source">
              ⛔ 阻塞来源：
              <span v-for="(p, i) in c.blockedBy" :key="p.cardId">
                #{{ p.seqNo }}「{{ p.title }}」{{ p.statusName }}<template v-if="i < c.blockedBy.length - 1">、</template>
              </span>
              <span class="dr-block-advice">不能越过，须先登记处置结论或把本卡一并跳过</span>
            </div>
            <div v-if="c.status === 'CONFIRMED'" class="dr-node-result ok">
              {{ c.confirmedBy }} 确认 · {{ timeOnly(c.confirmedAt) }} · {{ c.actualAction }}
            </div>
            <div v-if="c.status === 'TIMEOUT'" class="dr-node-result danger">
              超时于 {{ fmt(c.timedOutAt) }}
              <template v-if="c.dispositionNote">；处置：{{ c.dispositionNote }}（{{ c.dispositionBy }}）</template>
            </div>
            <div v-if="c.status === 'SKIPPED'" class="dr-node-result warn">
              {{ c.blockedByCascade ? '级联跳过' : '跳过' }}（{{ c.skippedBy }}）：{{ c.skipReason }}
              <template v-if="c.dispositionNote"><br />处置：{{ c.dispositionNote }}（{{ c.dispositionBy }}）</template>
            </div>
            <div class="dr-node-actions">
              <el-button v-if="c.canIssue" size="small" type="primary" :loading="acting" @click="onIssue(c)">下达</el-button>
              <el-button v-if="c.status === 'WAITING'" size="small" @click="openConfirm(c)">岗位确认</el-button>
              <el-button v-if="c.status === 'PENDING' || c.status === 'WAITING'" size="small" plain type="danger"
                @click="openSkip(c)">跳过</el-button>
              <el-button v-if="(c.status === 'TIMEOUT' || c.status === 'SKIPPED') && !c.dispositionNote"
                size="small" type="warning" @click="openDisposition(c)">补处置结论</el-button>
              <el-button v-if="c.status === 'TIMEOUT' && c.dispositionNote && (d.status === 'RUNNING' || d.status === 'PAUSED')"
                size="small" link @click="openDisposition(c)">修改处置结论</el-button>
            </div>
          </div>
        </div>
      </div>

      <!-- 右：岗位 + 下一可执行 -->
      <div class="dr-side">
        <div class="fp-panel">
          <div class="fp-panel-head"><span class="fp-panel-title">下一可执行节点</span></div>
          <div class="fp-panel-body">
            <div v-if="nextExecutable.length === 0" class="dr-muted">暂无可下达卡（前置未满足或当前节点等待响应中）</div>
            <div v-for="c in nextExecutable" :key="'nx-' + c.id" class="dr-next">
              <div>
                <b>#{{ c.seqNo }} {{ c.title }}</b>
                <div class="dr-muted">→ {{ c.targetPosName }} · 时限 {{ c.timeLimitSec }}s</div>
              </div>
              <el-button size="small" type="primary" :disabled="!c.canIssue" :loading="acting" @click="onIssue(c)">
                {{ c.canIssue ? '下达' : '等待当前节点' }}
              </el-button>
            </div>
            <div v-if="d.currentCard" class="dr-muted dr-next-hint">
              当前节点「{{ d.currentCard.title }}」等待响应中 —— 下列卡片前置已满足，但须等当前卡确认 / 超时处置 / 跳过后才能下达
            </div>
          </div>
        </div>

        <div class="fp-panel">
          <div class="fp-panel-head"><span class="fp-panel-title">各岗位状态（{{ d.positions.length }}）</span></div>
          <div class="fp-panel-body dr-positions">
            <div v-for="p in d.positions" :key="p.id" class="dr-pos" :class="{ me: p.boundToCurrentUser }">
              <div class="dr-pos-head">
                <b>{{ p.posName }}</b>
                <span class="dr-muted">{{ p.posCode }}</span>
                <el-tag v-if="p.boundToCurrentUser" size="small" type="success">我的岗位</el-tag>
              </div>
              <div class="dr-pos-member">{{ p.memberName || '未指定人员' }}{{ p.contact ? ' · ' + p.contact : '' }}</div>
              <div class="dr-pos-stats">
                <span class="ok">确认 {{ p.confirmedCards }}</span>
                <span class="danger">超时 {{ p.timeoutCards }}</span>
                <span class="warn">跳过 {{ p.skippedCards }}</span>
                <span>等待 {{ p.waitingCards }}</span>
                <span class="dr-muted">待下达 {{ p.pendingCards }}</span>
              </div>
              <div v-if="p.currentCardTitle" class="dr-pos-current">正在响应：{{ p.currentCardTitle }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 完整时间线 -->
    <div class="fp-panel">
      <div class="fp-panel-head">
        <span class="fp-panel-title">完整时间线（只追加事件 + 岗位响应，按服务端时间排序）</span>
        <span class="fp-panel-tip">{{ d.events.length }} 条事件 · {{ d.responses.length }} 条响应记录</span>
      </div>
      <div class="fp-panel-body">
        <el-timeline>
          <el-timeline-item v-for="t in d.timeline" :key="t.key" :timestamp="fmt(t.occurredAt)" placement="top"
            :type="timelineType(t)" :hollow="t.kind === 'LATE'">
            <div class="dr-tl">
              <el-tag size="small" :type="timelineType(t)">{{ timelineKindName(t) }}</el-tag>
              <b v-if="t.cardSeqNo != null"> #{{ t.cardSeqNo }}<span v-if="t.cardTitle">「{{ t.cardTitle }}」</span></b>
              <span class="dr-tl-operator">{{ t.operator }}<span v-if="t.operatorRole && t.operatorRole !== 'SYSTEM'">（{{ roleName(t.operatorRole) }}）</span></span>
            </div>
            <div class="dr-tl-note">
              <template v-if="t.kind === 'EVENT'">{{ t.eventTypeName }}：{{ t.note }}</template>
              <template v-else>{{ t.posName }}（{{ t.posCode }}）{{ t.kind === 'LATE' ? '迟到确认' : '按时确认' }}：{{ t.note }}</template>
            </div>
            <div v-if="t.kind === 'EVENT' && t.fromStatusName" class="dr-tl-transition">
              {{ t.fromStatusName }} <span class="dr-arrow">→</span> {{ t.toStatusName }}
            </div>
          </el-timeline-item>
        </el-timeline>
      </div>
    </div>

    <!-- 确认弹窗 -->
    <el-dialog v-model="confirmVisible" title="岗位响应确认" width="480px">
      <div v-if="activeCard" class="dr-confirm-card">
        <div><b>#{{ activeCard.seqNo }} {{ activeCard.title }}</b></div>
        <div class="dr-muted">目标：{{ activeCard.targetPosName }}（{{ activeCard.targetPosCode }}）</div>
      </div>
      <el-form label-width="86px" style="margin-top: 10px">
        <el-form-item label="实际动作" required>
          <el-input v-model="confirmForm.actualAction" type="textarea" :rows="3" maxlength="255" show-word-limit
            placeholder="岗位实际执行的动作，如：已携 4kg 干粉灭火器到达着火点，距火源 3 米对准火焰根部扫射" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="confirmForm.remark" maxlength="255" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="confirmVisible = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="submitConfirm">提交确认</el-button>
      </template>
    </el-dialog>

    <!-- 跳过弹窗 -->
    <el-dialog v-model="skipVisible" title="跳过现场节点" width="480px">
      <div v-if="activeCard" class="dr-confirm-card">
        <div><b>#{{ activeCard.seqNo }} {{ activeCard.title }}</b></div>
        <div class="dr-muted">跳过必须填写原因；依赖该节点的卡将明确显示阻塞来源</div>
      </div>
      <el-form label-width="86px" style="margin-top: 10px">
        <el-form-item label="跳过原因" required>
          <el-input v-model="skipForm.reason" type="textarea" :rows="3" maxlength="255" show-word-limit
            placeholder="如：现场模拟人员未到位，该节点不具备执行条件" />
        </el-form-item>
        <el-form-item label="级联跳过">
          <el-checkbox v-model="skipForm.cascade">把因本节点跳过而被阻塞的下游待下达卡一并跳过（原因中注明来源）</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="skipVisible = false">取消</el-button>
        <el-button type="danger" :loading="acting" @click="submitSkip">确认跳过</el-button>
      </template>
    </el-dialog>

    <!-- 处置结论弹窗 -->
    <el-dialog v-model="dispositionVisible" title="登记处置结论" width="480px">
      <div v-if="activeCard" class="dr-confirm-card">
        <div><b>#{{ activeCard.seqNo }} {{ activeCard.title }}</b></div>
        <div class="dr-muted">
          {{ activeCard.status === 'TIMEOUT' ? '该卡已超时' : '该卡已跳过' }}，结束演练前必须为每条超时/跳过记录填写处置结论
        </div>
      </div>
      <el-form label-width="86px" style="margin-top: 10px">
        <el-form-item label="处置结论" required>
          <el-input v-model="dispositionForm.note" type="textarea" :rows="3" maxlength="255" show-word-limit
            placeholder="如：超时原因为通讯延迟，已现场补做并纳入复训；不影响后续节点" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dispositionVisible = false">取消</el-button>
        <el-button type="warning" :loading="acting" @click="submitDisposition">登记结论</el-button>
      </template>
    </el-dialog>

    <!-- 取消演练 -->
    <el-dialog v-model="cancelVisible" title="取消演练" width="440px">
      <el-input v-model="cancelReason" type="textarea" :rows="3" maxlength="255" show-word-limit
        placeholder="取消原因（必填）" />
      <template #footer>
        <el-button @click="cancelVisible = false">返回</el-button>
        <el-button type="danger" :loading="acting" @click="submitCancel">确认取消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { drillApi, newActionId } from '../../api'
import { currentUser } from '../../auth'

const route = useRoute()
const nav = useRouter()
const drillId = Number(route.params.id)

const d = ref(blankDetail())
const acting = ref(false)
const tick = ref(0)
let timer = null
let pollTimer = null

const confirmVisible = ref(false)
const skipVisible = ref(false)
const dispositionVisible = ref(false)
const cancelVisible = ref(false)
const activeCard = ref(null)
const confirmForm = ref({ actualAction: '', remark: '' })
const skipForm = ref({ reason: '', cascade: true })
const dispositionForm = ref({ note: '' })
const cancelReason = ref('')

function blankDetail() {
  return {
    status: 'DRAFT', statusName: '草稿', chainFrozen: false, timingRunning: false,
    positions: [], cards: [], events: [], responses: [], timeline: [],
    currentCard: null, nextExecutableSeqs: [], finishBlockers: [],
    canStart: false, canPause: false, canResume: false, canCancel: false,
    cardTotal: 0, pausedTotalSec: 0
  }
}

// ---- 服务端时间对齐：以最后一次详情的 serverTime 为锚，本地只做平滑递减 ----
const serverAnchor = ref({ server: null, local: Date.now() })
function setServerClock(serverTime) {
  if (serverTime) {
    serverAnchor.value = { server: new Date(serverTime).getTime(), local: Date.now() }
  }
}
const serverNow = computed(() => {
  if (!serverAnchor.value.server) return Date.now()
  return serverAnchor.value.server + (Date.now() - serverAnchor.value.local)
})
const clockText = computed(() => {
  tick.value
  return new Date(serverNow.value).toLocaleTimeString('zh-CN', { hour12: false })
})

const liveRemaining = computed(() => {
  tick.value // 依赖 1s 心跳刷新
  const c = d.value.currentCard
  if (!c) return null
  if (d.value.status === 'PAUSED') return c.remainingSec // 冻结值
  const deadline = c.deadlineAt ? new Date(c.deadlineAt).getTime() : null
  if (!deadline) return c.remainingSec
  return Math.max(0, Math.floor((deadline - serverNow.value) / 1000))
})

const nextExecutable = computed(() =>
  (d.value.cards || []).filter((c) => d.value.nextExecutableSeqs.includes(c.seqNo)))

const canFinishNow = computed(() => d.value.canFinish)
const allTerminal = computed(() => (d.value.cards || []).length > 0
  && d.value.cards.every((c) => ['CONFIRMED', 'TIMEOUT', 'SKIPPED'].includes(c.status)))

const canConfirmCurrent = computed(() => {
  const c = d.value.currentCard
  if (!c) return false
  if (d.value.status !== 'RUNNING') return false
  // 绑定了用户就只允许本人确认；否则现场负责人可确认（后端再鉴权一次）
  const pos = (d.value.positions || []).find((p) => p.id === c.targetPositionId)
  if (pos && pos.userId) return !!currentUser.value && pos.userId === currentUser.value.id
  return !!currentUser.value && currentUser.value.role === 'SITE_OWNER'
})

function statusTag(s) {
  return { DRAFT: 'info', READY: 'warning', RUNNING: 'success', PAUSED: 'warning', COMPLETED: 'success', CANCELED: 'info' }[s] || 'info'
}
function cardTag(s) {
  return { PENDING: 'info', WAITING: 'warning', CONFIRMED: 'success', TIMEOUT: 'danger', SKIPPED: 'info' }[s] || 'info'
}
function nodeClass(c) {
  return {
    'is-current': d.value.currentCard && c.id === d.value.currentCard.id,
    'is-confirmed': c.status === 'CONFIRMED',
    'is-timeout': c.status === 'TIMEOUT',
    'is-skipped': c.status === 'SKIPPED',
    'is-blocked': c.blocked,
    'is-issueable': c.canIssue
  }
}
function fmt(t) {
  return t ? String(t).replace('T', ' ').slice(0, 19) : ''
}
function timeOnly(t) {
  return t ? String(t).replace('T', ' ').slice(11, 19) : ''
}
function formatRemain(sec) {
  if (sec == null) return '—'
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}
function confirmLabel(c) {
  return canConfirmCurrent.value ? '岗位确认（实际动作）' : '岗位确认'
}
function roleName(r) {
  return { FIRE_MANAGER: '消防主管', SAFETY_OFFICER: '安全负责人', SITE_OWNER: '现场负责人' }[r] || r
}
function timelineType(t) {
  if (t.kind === 'LATE') return 'danger'
  return {
    CREATE: 'primary', READY: 'warning', START: 'success', ISSUE: 'primary',
    CONFIRM: 'success', TIMEOUT: 'danger', PAUSE: 'warning', RESUME: 'primary',
    SKIP: 'info', CANCEL: 'info', FINISH: 'success', DISPOSE: 'warning',
    LATE_ATTEMPT: 'danger', SAVE_DRAFT: 'info', RESPONSE: 'success'
  }[t.eventType] || 'info'
}
function timelineKindName(t) {
  if (t.kind === 'RESPONSE') return '岗位按时响应'
  if (t.kind === 'LATE') return '迟到确认（不改终态）'
  return t.eventTypeName
}

async function reload() {
  const data = await drillApi.detail(drillId)
  d.value = data
  setServerClock(data.serverTime)
}

function goList() {
  nav.push('/drills')
}
function goPlan() {
  nav.push(`/drills/${drillId}/plan`)
}

// ---- 动作 ----
async function act(kind) {
  acting.value = true
  try {
    d.value = await drillApi[kind](drillId, { clientActionId: newActionId() })
    setServerClock(d.value.serverTime)
    ElMessage.success({ start: '已开始', pause: '已暂停，剩余时间冻结', resume: '已恢复，从剩余值继续' }[kind] || '操作成功')
  } finally {
    acting.value = false
  }
}

async function onIssue(c) {
  acting.value = true
  try {
    d.value = await drillApi.issueCard(drillId, c.id, { clientActionId: newActionId() })
    setServerClock(d.value.serverTime)
  } finally {
    acting.value = false
  }
}

function openConfirm(c) {
  activeCard.value = c
  confirmForm.value = { actualAction: '', remark: '' }
  confirmVisible.value = true
}
async function submitConfirm() {
  if (!confirmForm.value.actualAction.trim()) {
    ElMessage.warning('请填写实际动作')
    return
  }
  acting.value = true
  try {
    d.value = await drillApi.confirmCard(drillId, activeCard.value.id, {
      clientActionId: newActionId(),
      actualAction: confirmForm.value.actualAction.trim(),
      remark: confirmForm.value.remark || null
    })
    setServerClock(d.value.serverTime)
    confirmVisible.value = false
    ElMessage.success('响应已记录')
  } finally {
    acting.value = false
  }
}

function openSkip(c) {
  activeCard.value = c
  skipForm.value = { reason: '', cascade: true }
  skipVisible.value = true
}
async function submitSkip() {
  if (!skipForm.value.reason.trim()) {
    ElMessage.warning('跳过必须填写原因')
    return
  }
  acting.value = true
  try {
    d.value = await drillApi.skipCard(drillId, activeCard.value.id, {
      clientActionId: newActionId(),
      reason: skipForm.value.reason.trim(),
      cascadeBlocked: skipForm.value.cascade
    })
    setServerClock(d.value.serverTime)
    skipVisible.value = false
    ElMessage.success('节点已跳过')
  } finally {
    acting.value = false
  }
}

function openDisposition(c) {
  activeCard.value = c
  dispositionForm.value = { note: c.dispositionNote || '' }
  dispositionVisible.value = true
}
async function submitDisposition() {
  if (!dispositionForm.value.note.trim()) {
    ElMessage.warning('处置结论不能为空')
    return
  }
  acting.value = true
  try {
    d.value = await drillApi.disposition(drillId, activeCard.value.id, {
      clientActionId: newActionId(),
      note: dispositionForm.value.note.trim()
    })
    setServerClock(d.value.serverTime)
    dispositionVisible.value = false
    ElMessage.success('处置结论已登记')
  } finally {
    acting.value = false
  }
}

function onCancel() {
  cancelReason.value = ''
  cancelVisible.value = true
}
async function submitCancel() {
  if (!cancelReason.value.trim()) {
    ElMessage.warning('取消原因必填')
    return
  }
  acting.value = true
  try {
    d.value = await drillApi.cancel(drillId, {
      clientActionId: newActionId(),
      reason: cancelReason.value.trim()
    })
    setServerClock(d.value.serverTime)
    cancelVisible.value = false
  } finally {
    acting.value = false
  }
}

async function onFinish() {
  let remark = ''
  try {
    const r = await ElMessageBox.prompt('全部指令卡均已终态且超时/跳过处置结论齐全。可填写结束说明（选填）', '结束演练', {
      confirmButtonText: '确认结束',
      cancelButtonText: '再看看',
      inputType: 'textarea',
      inputValue: ''
    })
    remark = r.value || ''
  } catch (e) {
    return
  }
  acting.value = true
  try {
    d.value = await drillApi.finish(drillId, { clientActionId: newActionId(), remark })
    setServerClock(d.value.serverTime)
    ElMessage.success('演练已结束')
  } finally {
    acting.value = false
  }
}

onMounted(async () => {
  try {
    await reload()
  } catch (e) {
    return
  }
  // 1s 心跳：仅重算本地倒计时；2s 轮询详情同步状态 / 超时 / 时间线 / 其他页面的操作
  timer = setInterval(() => { tick.value++ }, 1000)
  pollTimer = setInterval(() => {
    if (!acting.value && ['RUNNING', 'PAUSED', 'READY'].includes(d.value.status)) {
      reload().catch(() => {})
    }
  }, 2000)
})

onBeforeUnmount(() => {
  clearInterval(timer)
  clearInterval(pollTimer)
})
</script>

<style scoped>
.dr-run-head { flex-wrap: wrap; gap: 10px; }
.dr-run-actions { margin-left: auto; display: flex; gap: 8px; align-items: center; }
.dr-run-meta { display: flex; gap: 20px; flex-wrap: wrap; font-size: 12.5px; color: var(--fp-text-sub); }
.dr-clock { margin-left: auto; font-variant-numeric: tabular-nums; }
.dr-pause-banner, .dr-ready-banner, .dr-blocker-banner {
  margin-top: 10px; padding: 8px 12px; border-radius: 8px; font-size: 13px;
}
.dr-pause-banner { background: var(--fp-warn-soft); border: 1px solid #e8c37a; color: #8a5a00; }
.dr-ready-banner { background: #eef4fb; border: 1px solid #b9d2f0; color: #27507e; }
.dr-blocker-banner { background: var(--fp-danger-soft); border: 1px solid #eab6b6; color: var(--fp-danger); }
.dr-blocker-item { font-size: 12.5px; margin-top: 2px; }

.dr-current {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 16px;
  align-items: center;
  border: 2px solid var(--fp-warn);
  border-radius: 12px;
  padding: 14px 18px;
  background: linear-gradient(135deg, #fffaf0, #fff5e6);
}
.dr-current.is-paused { border-color: #b9b9b9; background: #f6f6f6; }
.dr-current.is-danger { border-color: var(--fp-danger); animation: dr-blink 1s steps(2, end) infinite; }
@keyframes dr-blink { 50% { background: var(--fp-danger-soft); } }
.dr-current-main { display: flex; gap: 14px; align-items: flex-start; }
.dr-current-seq {
  font-size: 26px; font-weight: 800; color: var(--fp-brand-deep);
  background: var(--fp-brand-soft); border-radius: 10px; padding: 6px 10px;
}
.dr-current-title { font-size: 17px; font-weight: 700; }
.dr-current-hint { color: var(--fp-text-sub); font-size: 13px; margin-top: 3px; }
.dr-current-target { margin-top: 6px; font-size: 13px; }
.dr-current-count { text-align: center; }
.dr-count-num {
  font-size: 40px; font-weight: 800; line-height: 1.05; color: var(--fp-brand-deep);
  font-variant-numeric: tabular-nums;
}
.is-danger .dr-count-num { color: var(--fp-danger); }
.dr-count-label { font-size: 12px; color: var(--fp-text-sub); }
.dr-count-limit { font-size: 11.5px; color: var(--fp-text-sub); margin-top: 2px; }
.dr-current-actions { display: flex; flex-direction: column; gap: 8px; }
.dr-empty-current { color: var(--fp-text-sub); padding: 14px 4px; text-align: center; }

.dr-two-col {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(300px, 1fr);
  gap: 14px;
  margin-top: 14px;
}
.dr-side { display: flex; flex-direction: column; gap: 14px; }

.dr-chain { display: flex; flex-direction: column; gap: 10px; }
.dr-node {
  border: 1px solid var(--fp-line);
  border-left-width: 4px;
  border-radius: 10px;
  padding: 9px 12px;
  background: #fff;
}
.dr-node.is-current { border-left-color: var(--fp-warn); background: #fffdf5; box-shadow: 0 0 0 2px #fdeec8; }
.dr-node.is-confirmed { border-left-color: var(--fp-ok); }
.dr-node.is-timeout { border-left-color: var(--fp-danger); background: #fff8f8; }
.dr-node.is-skipped { border-left-color: #9e9e9e; opacity: 0.92; }
.dr-node.is-blocked { border-color: var(--fp-danger); border-left-color: var(--fp-danger); }
.dr-node.is-issueable { box-shadow: 0 0 0 2px var(--fp-brand-soft); }
.dr-node-top { display: flex; align-items: center; gap: 8px; }
.dr-node-seq { font-weight: 800; color: var(--fp-brand-deep); }
.dr-node-title { font-weight: 600; flex: 1; }
.dr-late-flag { font-size: 11px; color: var(--fp-danger); border: 1px solid var(--fp-danger); border-radius: 8px; padding: 0 6px; }
.dr-node-meta { display: flex; gap: 14px; font-size: 12px; color: var(--fp-text-sub); margin-top: 4px; }
.dr-node-remain { font-weight: 700; color: var(--fp-brand-deep); font-variant-numeric: tabular-nums; }
.dr-node-remain.danger { color: var(--fp-danger); }
.dr-node-hint { font-size: 12px; color: var(--fp-text-sub); margin-top: 3px; }
.dr-prereqs { margin-top: 6px; font-size: 12px; display: flex; gap: 4px; flex-wrap: wrap; align-items: center; }
.dr-block-source {
  margin-top: 6px; font-size: 12.5px; color: var(--fp-danger);
  background: var(--fp-danger-soft); border-radius: 6px; padding: 5px 8px;
}
.dr-block-advice { display: block; font-size: 11.5px; margin-top: 2px; }
.dr-node-result { margin-top: 6px; font-size: 12.5px; padding: 5px 8px; border-radius: 6px; }
.dr-node-result.ok { background: #eef7ee; color: var(--fp-ok); }
.dr-node-result.danger { background: var(--fp-danger-soft); color: var(--fp-danger); }
.dr-node-result.warn { background: #f3f3f3; color: #616161; }
.dr-node-actions { margin-top: 7px; display: flex; gap: 6px; flex-wrap: wrap; }

.dr-next { display: flex; justify-content: space-between; align-items: center; padding: 8px 4px; border-bottom: 1px dashed var(--fp-line); }
.dr-next-hint { margin-top: 8px; font-size: 12px; }
.dr-positions { display: flex; flex-direction: column; gap: 8px; }
.dr-pos { border: 1px solid var(--fp-line); border-radius: 10px; padding: 8px 10px; }
.dr-pos.me { border-color: var(--fp-ok); background: #f4faf4; }
.dr-pos-head { display: flex; gap: 8px; align-items: center; }
.dr-pos-member { font-size: 12px; color: var(--fp-text-sub); margin: 2px 0 5px; }
.dr-pos-stats { display: flex; gap: 10px; font-size: 12px; flex-wrap: wrap; }
.dr-pos-stats .ok { color: var(--fp-ok); }
.dr-pos-stats .danger { color: var(--fp-danger); }
.dr-pos-stats .warn { color: #b26a00; }
.dr-pos-current { margin-top: 4px; font-size: 12px; color: var(--fp-brand-deep); font-weight: 600; }

.dr-tl { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.dr-tl-operator { color: var(--fp-text-sub); font-size: 12.5px; }
.dr-tl-note { font-size: 13px; margin-top: 3px; }
.dr-tl-transition { font-size: 12px; color: var(--fp-text-sub); margin-top: 2px; }
.dr-arrow { color: var(--fp-brand); font-weight: 700; }
.dr-muted { color: var(--fp-text-sub); font-size: 12.5px; }
.dr-confirm-card { background: var(--fp-brand-soft); border-radius: 8px; padding: 8px 10px; }

@media (max-width: 1200px) {
  .dr-two-col { grid-template-columns: 1fr; }
  .dr-current { grid-template-columns: 1fr; }
  .dr-current-actions { flex-direction: row; }
}
</style>
