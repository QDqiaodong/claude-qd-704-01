<template>
  <div v-loading="loading">
    <!-- 场次横幅：状态 / 当前节点 / 场次操作 -->
    <div class="fp-run-banner">
      <div>
        <div class="fp-run-title">
          {{ detail.title }}
          <el-tag size="small" :type="SESSION_STATUS_TAG[detail.status] || 'info'" effect="dark" style="margin-left:8px">
            {{ detail.statusName }}
          </el-tag>
        </div>
        <div class="fp-run-no">
          {{ detail.drillNo }} · 组织者 {{ detail.organizer }}
          <template v-if="detail.startedAt"> · 开始 {{ fmtTime(detail.startedAt) }}</template>
        </div>
      </div>
      <div style="font-size:13px;color:var(--fp-text-sub)">
        当前等待节点：
        <b v-if="currentCards.length" style="color:var(--fp-warn)">
          {{ currentCards.map((c) => '#' + c.seqNo).join('、') }}
        </b>
        <b v-else style="color:var(--fp-ok)">无</b>
        ｜下一可执行：
        <b v-if="nextCard" style="color:var(--fp-brand)">#{{ nextCard.seqNo }}</b>
        <b v-else style="color:var(--fp-text-sub)">无</b>
      </div>
      <div class="fp-run-actions">
        <el-button size="small" @click="goList">返回列表</el-button>
        <el-button size="small" @click="load">刷新</el-button>
        <el-button size="small" @click="goEdit" v-if="detail.status === 'DRAFT'">去编排</el-button>
        <el-button size="small" type="success" v-if="detail.status === 'READY'"
                   :loading="busy" @click="sessionAction('start')">开始演练</el-button>
        <el-button size="small" type="warning" v-if="detail.status === 'RUNNING'"
                   :loading="busy" @click="sessionAction('pause')">暂停（冻结剩余）</el-button>
        <el-button size="small" type="success" v-if="detail.status === 'PAUSED'"
                   :loading="busy" @click="sessionAction('resume')">恢复（继续倒计时）</el-button>
        <el-button size="small" type="danger" plain
                   v-if="['DRAFT', 'READY', 'RUNNING', 'PAUSED'].includes(detail.status)"
                   @click="onCancel">取消演练</el-button>
        <el-button size="small" type="primary"
                   v-if="['RUNNING', 'PAUSED'].includes(detail.status)"
                   @click="onFinish">结束演练</el-button>
      </div>
    </div>

    <!-- 暂停 / 结束缺项提示条 -->
    <el-alert v-if="detail.status === 'PAUSED'" type="warning" show-icon :closable="false" style="margin-top:10px"
              title="演练已暂停：所有等待响应卡的倒计时已冻结，恢复后从剩余秒数继续；暂停期间不能确认。" />
    <el-alert v-if="detail.status === 'FINISHED'" type="success" show-icon :closable="false" style="margin-top:10px"
              :title="`演练已于 ${fmtTime(detail.finishedAt)} 由 ${detail.finishedBy} 结束`" />
    <el-alert v-if="detail.status === 'CANCELED'" type="info" show-icon :closable="false" style="margin-top:10px"
              :title="`演练已取消：${detail.cancelReason || ''}`" />
    <div v-if="['RUNNING', 'PAUSED'].includes(detail.status) && detail.finishBlockers.length"
          class="fp-finish-tip">
      结束演练还差以下 {{ detail.finishBlockers.length }} 项：
      <ul><li v-for="(b, i) in detail.finishBlockers" :key="i">{{ b }}</li></ul>
    </div>
    <el-alert v-if="['RUNNING', 'PAUSED'].includes(detail.status) && !detail.finishBlockers.length"
              type="success" show-icon :closable="false" style="margin-top:10px"
              title="所有指令卡均已到明确终态且处置结论齐全，可以结束演练。" />

    <div class="fp-drill-grid" style="margin-top:12px">
      <!-- 左：指令链 -->
      <div class="fp-drill-col">
        <div class="fp-post-card">
          <div class="fp-post-head">
            现场指令链（{{ cards.length }} 张）
            <span style="margin-left:auto;font-weight:400;font-size:12px;color:var(--fp-text-sub)">
              服务端时间 {{ fmtClock(detail.serverTime) }} · {{ tickText }}
            </span>
          </div>
        </div>

        <div v-for="card in cards" :key="card.id"
             class="fp-node-card"
             :class="{
               'is-current': card.status === 'WAITING',
               'is-next': card.nextExecutable,
               'is-blocked': card.status === 'PENDING' && card.blockers.length
             }">
          <div class="fp-node-head">
            <span class="fp-node-seq">{{ card.seqNo }}</span>
            <span class="fp-node-title">{{ card.title }}</span>
            <el-tag size="small" :type="CARD_STATUS_TAG[card.status] || 'info'" effect="dark">
              {{ card.statusName }}
            </el-tag>
            <el-tag v-if="card.nextExecutable" size="small" type="primary">下一可执行节点</el-tag>
            <el-tag size="small" effect="plain" style="margin-left:auto">
              {{ card.targetPostName }}（{{ card.targetPostCode }}）
            </el-tag>
          </div>

          <div class="fp-node-body">{{ card.content }}</div>
          <div class="fp-node-hint" v-if="card.sceneHint">现场提示：{{ card.sceneHint }}</div>

          <div class="fp-node-meta">
            <span>响应时限：<b>{{ card.timeLimitSeconds }}</b> 秒</span>
            <span v-if="card.issuedAt">下达：{{ fmtTime(card.issuedAt) }}（{{ card.issuedBy }}）</span>
            <span v-if="card.prereqSeqs.length">前置节点：{{ card.prereqSeqs.map((s) => '#' + s).join('、') }}</span>
          </div>

          <!-- 等待响应：服务端时间计算的剩余秒数 -->
          <div v-if="card.status === 'WAITING'" style="display:flex;align-items:center;gap:14px;margin-top:10px;flex-wrap:wrap">
            <div>
              <div style="font-size:12px;color:var(--fp-text-sub)">剩余秒数（服务端口径）</div>
              <span class="fp-countdown"
                    :class="{ 'is-urgent': remain(card) !== null && remain(card) <= 10 && detail.status === 'RUNNING',
                              'is-frozen': detail.status === 'PAUSED' }">
                {{ remain(card) === null ? '—' : fmtSeconds(remain(card)) }}
              </span>
              <el-tag v-if="detail.status === 'PAUSED'" size="small" type="info" style="margin-left:8px">
                已冻结 {{ card.remainingFrozenSeconds }}s
              </el-tag>
              <el-tag v-else size="small" type="warning" effect="plain" style="margin-left:8px">
                截止 {{ fmtClock(card.deadline) }}
              </el-tag>
            </div>
            <el-button size="small" type="success"
                       :disabled="detail.status !== 'RUNNING' || busy"
                       @click="openConfirm(card)">岗位确认</el-button>
            <el-button size="small" type="danger" plain :disabled="busy" @click="openSkip(card)">跳过</el-button>
          </div>

          <!-- 已确认：实际动作 -->
          <div v-if="card.status === 'CONFIRMED'" style="margin-top:8px;font-size:13px">
            <el-tag size="small" type="success" effect="plain">确认</el-tag>
            <span style="margin-left:6px">{{ card.confirmedBy }} · {{ fmtTime(card.confirmedAt) }}</span>
            <div style="margin-top:4px">实际动作：{{ card.actualAction }}</div>
            <div v-if="card.confirmRemark" style="color:var(--fp-text-sub)">备注：{{ card.confirmRemark }}</div>
          </div>

          <!-- 已超时 -->
          <div v-if="card.status === 'TIMEOUT'" style="margin-top:8px;font-size:13px">
            <el-tag size="small" type="danger" effect="plain">已超时</el-tag>
            <span style="margin-left:6px">判定时间 {{ fmtTime(card.timedOutAt) }}</span>
            <div style="margin-top:6px">
              处置结论：
              <span v-if="card.disposition">{{ card.disposition }}
                <span style="color:var(--fp-text-sub)">（{{ card.dispositionBy }} · {{ fmtTime(card.dispositionAt) }}）</span>
              </span>
              <el-button v-else size="small" type="warning" @click="openDispose(card)">填写处置结论</el-button>
              <el-button v-if="card.disposition" size="small" link type="primary" @click="openDispose(card)">补充</el-button>
            </div>
          </div>

          <!-- 已跳过 -->
          <div v-if="card.status === 'SKIPPED'" style="margin-top:8px;font-size:13px">
            <el-tag size="small" type="danger" effect="plain">已跳过</el-tag>
            <span style="margin-left:6px">{{ card.skippedBy }} · {{ fmtTime(card.skippedAt) }}</span>
            <div style="margin-top:4px">跳过原因：{{ card.skipReason }}</div>
            <div style="margin-top:4px">
              处置结论：
              <span v-if="card.disposition">{{ card.disposition }}
                <span style="color:var(--fp-text-sub)">（{{ card.dispositionBy }} · {{ fmtTime(card.dispositionAt) }}）</span>
              </span>
              <el-button v-else size="small" type="warning" @click="openDispose(card)">填写处置结论</el-button>
              <el-button v-if="card.disposition" size="small" link type="primary" @click="openDispose(card)">补充</el-button>
            </div>
          </div>

          <!-- 阻塞来源：明确显示，不能悄悄越过 -->
          <div v-if="card.blockers.length" class="fp-blocker-list">
            <b>⛔ 被 {{ card.blockers.length }} 个前置节点阻塞，暂不能下达：</b>
            <div v-for="b in card.blockers" :key="b.prereqId">· {{ b.reason }}</div>
          </div>

          <!-- 待下达：可下达按钮 -->
          <div v-if="card.status === 'PENDING'" style="margin-top:10px">
            <el-button size="small" type="primary"
                       :disabled="!card.issuable || busy"
                       @click="onIssue(card)">
              {{ card.issuable ? '下达指令' : '前置未满足，不可下达' }}
            </el-button>
            <el-button size="small" type="danger" plain :disabled="busy" @click="openSkip(card)">跳过节点</el-button>
          </div>

          <!-- 该卡的响应尝试（含迟到尝试） -->
          <div v-if="card.attempts.length" style="margin-top:8px;border-top:1px dashed var(--fp-line);padding-top:6px">
            <div v-for="a in card.attempts" :key="a.id" style="font-size:12.5px;color:var(--fp-text-sub)">
              <el-tag size="small" :type="a.late ? 'danger' : 'success'" effect="plain">
                {{ a.resultName }}
              </el-tag>
              {{ a.operator }} · {{ fmtTime(a.createdAt) }}：{{ a.actualAction }}
              <span v-if="a.remark">（{{ a.remark }}）</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 右：岗位状态 + 完整时间线 -->
      <div class="fp-drill-col">
        <div class="fp-post-card">
          <div class="fp-post-head">各岗位状态（{{ detail.posts.length }} 岗）</div>
          <div v-for="p in detail.posts" :key="p.id" class="fp-post-card" style="margin-top:8px;background:#fdfaf8">
            <div class="fp-post-head">
              {{ p.postName }}
              <el-tag size="small" effect="plain">{{ p.postCode }}</el-tag>
              <span v-if="p.assignee" style="font-weight:400;font-size:12px;color:var(--fp-text-sub)">
                {{ p.assignee }}
              </span>
            </div>
            <div class="fp-post-stats">
              <span class="fp-post-stat">待下达 <b>{{ p.pendingCount }}</b></span>
              <span class="fp-post-stat">等待 <b>{{ p.waitingCount }}</b></span>
              <span class="fp-post-stat">确认 <b>{{ p.confirmedCount }}</b></span>
              <span class="fp-post-stat">超时 <b>{{ p.timeoutCount }}</b></span>
              <span class="fp-post-stat">跳过 <b>{{ p.skippedCount }}</b></span>
            </div>
            <!-- 岗位独立响应记录 -->
            <div v-if="p.attempts.length" style="margin-top:6px;font-size:12px;color:var(--fp-text-sub)">
              <div v-for="a in p.attempts" :key="a.id" style="margin-top:2px">
                <el-tag size="small" :type="a.late ? 'danger' : 'success'" effect="plain">{{ a.resultName }}</el-tag>
                #{{ a.cardSeqNo }} {{ a.cardTitle }} · {{ fmtClock(a.createdAt) }}
              </div>
            </div>
          </div>
        </div>

        <div class="fp-post-card">
          <div class="fp-post-head">
            完整事件时间线（{{ events.length }}）
            <span style="margin-left:auto;font-weight:400;font-size:12px;color:var(--fp-text-sub)">只追加</span>
          </div>
          <div class="fp-timeline" style="margin-top:8px">
            <div v-for="e in reversedEvents" :key="e.id" class="fp-tl-item">
              <span class="fp-tl-dot" :class="eventDotClass(e.action)"></span>
              <div class="fp-tl-main">
                <div class="fp-tl-title">
                  {{ e.actionName }}
                  <span v-if="e.cardSeqNo != null" style="font-weight:400;color:var(--fp-text-sub)">
                    · #{{ e.cardSeqNo }} {{ e.cardTitle }}
                  </span>
                </div>
                <div class="fp-tl-note">{{ e.note }}</div>
                <div class="fp-tl-state" v-if="e.fromStatus || e.toStatus">
                  状态：
                  {{ e.fromStatusName || '—' }}
                  →
                  {{ e.toStatusName || '—' }}
                </div>
                <div class="fp-tl-time">{{ fmtTime(e.createdAt) }} · {{ e.operator }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 确认弹窗：实际动作必填、备注可选 -->
    <el-dialog v-model="confirmVisible" title="岗位响应确认" width="480px">
      <div style="font-size:13px;color:var(--fp-text-sub);margin-bottom:8px" v-if="activeCard">
        #{{ activeCard.seqNo }} {{ activeCard.title }} → {{ activeCard.targetPostName }}
      </div>
      <el-form label-width="84px">
        <el-form-item label="实际动作" required>
          <el-input v-model="confirmForm.actualAction" type="textarea" :rows="3" maxlength="500" show-word-limit
                    placeholder="例如：已到现场确认火情，切断一层电源，使用 4kg 干粉灭火器扑救初期火灾" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="confirmForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit
                    placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="confirmVisible = false">取消</el-button>
        <el-button type="success" :loading="busy" @click="submitConfirm">提交确认</el-button>
      </template>
    </el-dialog>

    <!-- 跳过弹窗：原因必填 -->
    <el-dialog v-model="skipVisible" title="跳过节点" width="480px">
      <div style="font-size:13px;color:var(--fp-danger);margin-bottom:8px" v-if="activeCard">
        跳过 #{{ activeCard.seqNo }}「{{ activeCard.title }}」后，依赖它的下游卡会明确显示阻塞来源。
      </div>
      <el-input v-model="skipForm.reason" type="textarea" :rows="3" maxlength="500" show-word-limit
                placeholder="必须填写跳过原因，例如：该岗位人员临时缺岗，由邻岗代行，本节点不单独计时" />
      <template #footer>
        <el-button @click="skipVisible = false">取消</el-button>
        <el-button type="danger" :loading="busy" @click="submitSkip">确认跳过</el-button>
      </template>
    </el-dialog>

    <!-- 处置结论弹窗 -->
    <el-dialog v-model="disposeVisible" title="处置结论" width="480px">
      <div style="font-size:13px;color:var(--fp-text-sub);margin-bottom:8px" v-if="activeCard">
        #{{ activeCard.seqNo }}「{{ activeCard.title }}」当前为「{{ activeCard.statusName }}」，
        结束演练前必须填写处置结论。
      </div>
      <el-input v-model="disposeForm.disposition" type="textarea" :rows="3" maxlength="500" show-word-limit
                placeholder="例如：超时原因为对讲机信道占用，已更换备用信道并复盘；人员动作补做合格" />
      <template #footer>
        <el-button @click="disposeVisible = false">取消</el-button>
        <el-button type="primary" :loading="busy" @click="submitDispose">保存处置结论</el-button>
      </template>
    </el-dialog>

    <!-- 取消弹窗 -->
    <el-dialog v-model="cancelVisible" title="取消演练" width="440px">
      <el-input v-model="cancelForm.reason" type="textarea" :rows="3" maxlength="255" show-word-limit
                placeholder="取消演练必须填写原因" />
      <template #footer>
        <el-button @click="cancelVisible = false">再想想</el-button>
        <el-button type="danger" :loading="busy" @click="submitCancel">确认取消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { drillApi, newActionKey } from '../../api'
import {
  CARD_STATUS_TAG, SESSION_STATUS_TAG, eventDotClass,
  fmtClock, fmtSeconds, fmtTime, serverClockOffset
} from './drill-util'

const route = useRoute()
const nav = useRouter()
const id = Number(route.params.id)

const loading = ref(false)
const busy = ref(false)
const detail = ref({
  status: 'DRAFT', title: '', drillNo: '', serverTime: null,
  cards: [], posts: [], events: [], finishBlockers: []
})

const tick = ref(0)
let timer = null
let pollTimer = null
let clockOffset = 0

const cards = computed(() => detail.value.cards || [])
const events = computed(() => detail.value.events || [])
const currentCards = computed(() => cards.value.filter((c) => c.status === 'WAITING'))
const nextCard = computed(() => cards.value.find((c) => c.nextExecutable) || null)
const reversedEvents = computed(() => [...events.value].reverse())
const tickText = computed(() => {
  tick.value
  return detail.value.status === 'PAUSED' ? '倒计时冻结中' : '倒计时进行中'
})

function remain(card) {
  tick.value // 依赖本地 tick 触发重算
  if (detail.value.status === 'PAUSED' || card.remainingFrozenSeconds != null) {
    return card.remainingFrozenSeconds
  }
  if (card.deadline) {
    const ms = new Date(card.deadline).getTime() - (Date.now() + clockOffset)
    return Math.max(0, Math.floor(ms / 1000))
  }
  return card.remainingSeconds
}

async function load(silent = false) {
  if (!silent) loading.value = true
  try {
    const d = await drillApi.detail(id)
    // 用服务端返回时间校时，前端只做展示，真实超时以后端扫描为准
    if (d.serverTime) clockOffset = serverClockOffset(d.serverTime)
    detail.value = d
  } finally {
    loading.value = false
  }
}

/**
 * 统一执行动作：成功后立即拉取详情。重复点击由后端 actionKey 幂等保证，
 * 这里给每次逻辑点击生成一个 key 并在按钮 pending 期间锁住，避免双击发两请求；
 * 即使两个执行页面同时操作，服务端行锁 + 唯一约束也只推进一次。
 */
async function runRequest(promiseFactory) {
  if (busy.value) return
  busy.value = true
  try {
    detail.value = await promiseFactory()
  } finally {
    busy.value = false
  }
}

async function sessionAction(kind) {
  const key = newActionKey()
  await runRequest(() => drillApi[kind](id, { actionKey: key }))
  ElMessage.success(kind === 'start' ? '演练已开始' : kind === 'pause' ? '已暂停，剩余时间冻结' : '已恢复')
}

async function onIssue(card) {
  await runRequest(() => drillApi.issue(id, card.id, { actionKey: newActionKey() }))
  ElMessage.success(`#${card.seqNo} 指令已下达`)
}

// ---- 确认 ----
const confirmVisible = ref(false)
const activeCard = ref(null)
const confirmForm = ref({ actualAction: '', remark: '' })

function openConfirm(card) {
  activeCard.value = card
  confirmForm.value = { actualAction: '', remark: '' }
  confirmVisible.value = true
}

async function submitConfirm() {
  if (!confirmForm.value.actualAction.trim()) {
    ElMessage.warning('必须填写实际动作')
    return
  }
  await runRequest(() => drillApi.confirm(id, activeCard.value.id, {
    actionKey: newActionKey(),
    actualAction: confirmForm.value.actualAction,
    remark: confirmForm.value.remark || null
  }))
  confirmVisible.value = false
  ElMessage.success('响应已记录')
}

// ---- 跳过 ----
const skipVisible = ref(false)
const skipForm = ref({ reason: '' })

function openSkip(card) {
  activeCard.value = card
  skipForm.value = { reason: '' }
  skipVisible.value = true
}

async function submitSkip() {
  if (!skipForm.value.reason.trim()) {
    ElMessage.warning('跳过节点必须填写原因')
    return
  }
  await runRequest(() => drillApi.skip(id, activeCard.value.id, {
    actionKey: newActionKey(), reason: skipForm.value.reason
  }))
  skipVisible.value = false
  ElMessage.success('节点已跳过，下游依赖卡已标注阻塞')
}

// ---- 处置 ----
const disposeVisible = ref(false)
const disposeForm = ref({ disposition: '' })

function openDispose(card) {
  activeCard.value = card
  disposeForm.value = { disposition: card.disposition || '' }
  disposeVisible.value = true
}

async function submitDispose() {
  if (!disposeForm.value.disposition.trim()) {
    ElMessage.warning('处置结论不能为空')
    return
  }
  await runRequest(() => drillApi.dispose(id, activeCard.value.id, {
    actionKey: newActionKey(), disposition: disposeForm.value.disposition
  }))
  disposeVisible.value = false
  ElMessage.success('处置结论已保存')
}

// ---- 取消 / 结束 ----
const cancelVisible = ref(false)
const cancelForm = ref({ reason: '' })

function onCancel() {
  cancelForm.value = { reason: '' }
  cancelVisible.value = true
}

async function submitCancel() {
  if (!cancelForm.value.reason.trim()) {
    ElMessage.warning('取消演练必须填写原因')
    return
  }
  await runRequest(() => drillApi.cancel(id, {
    actionKey: newActionKey(), reason: cancelForm.value.reason
  }))
  cancelVisible.value = false
  ElMessage.success('演练已取消')
}

async function onFinish() {
  if (detail.value.finishBlockers.length) {
    ElMessage.warning(`还有 ${detail.value.finishBlockers.length} 项缺项，请先处理`)
    return
  }
  await ElMessageBox.confirm('确认所有指令卡已到终态且处置结论齐全，结束本场演练？', '结束演练', {
    type: 'warning', confirmButtonText: '结束', cancelButtonText: '取消'
  })
  await runRequest(() => drillApi.finish(id, { actionKey: newActionKey() }))
  ElMessage.success('演练已结束')
}

function goList() {
  nav.push('/drills')
}

function goEdit() {
  nav.push(`/drills/${id}/edit`)
}

onMounted(async () => {
  await load()
  // 本地秒心跳：仅驱动剩余秒数字样跳变，不改任何状态
  timer = setInterval(() => { tick.value++ }, 1000)
  // 轮询拉服务端真相：超时翻转、多页面交替操作、暂停恢复后 3 秒内一致
  pollTimer = setInterval(() => {
    if (['RUNNING', 'PAUSED'].includes(detail.value.status)) load(true)
  }, 3000)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
  if (pollTimer) clearInterval(pollTimer)
})
</script>
