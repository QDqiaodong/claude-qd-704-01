<template>
  <div class="fp-panel">
    <div class="fp-panel-head">
      <span class="fp-panel-title">演练编排 · {{ form.title || '未命名演练' }}</span>
      <span class="fp-panel-tip">
        {{ detail.drillNo }} ·
        <el-tag size="small" :type="SESSION_STATUS_TAG[detail.status] || 'info'" effect="dark">{{ detail.statusName }}</el-tag>
        <span v-if="frozen" class="fp-freeze-tag">本场已开始，指令链已冻结，以下只读</span>
      </span>
      <div class="fp-space">
        <el-button size="small" @click="goList">返回列表</el-button>
        <template v-if="canEdit">
          <el-button size="small" plain @click="openTemplates">从常用脚本导入</el-button>
          <el-button size="small" type="primary" :loading="saving" @click="onSave(false)">保存草稿</el-button>
          <el-button size="small" type="warning" :loading="saving" @click="onSave(true)">保存并标记就绪</el-button>
        </template>
        <template v-if="detail.status === 'READY'">
          <el-button size="small" @click="onReopen">退回草稿继续改</el-button>
          <el-button size="small" type="success" :loading="saving" @click="onStart">开始演练（冻结指令链）</el-button>
        </template>
        <template v-else>
          <el-button size="small" type="primary" @click="goRun">进入执行页</el-button>
        </template>
      </div>
    </div>

    <div class="fp-panel-body" v-loading="loading">
      <!-- 场次信息 -->
      <el-form label-width="92px" class="fp-form-grid" style="margin-bottom: 6px">
        <el-form-item label="演练名称" required>
          <el-input v-model="form.title" :disabled="!canEdit" maxlength="128" />
        </el-form-item>
        <el-form-item label="关联建筑">
          <el-select v-model="form.buildingId" :disabled="!canEdit" clearable filterable placeholder="可空">
            <el-option v-for="b in buildings" :key="b.id" :label="b.code + ' · ' + b.name" :value="b.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="场景说明" style="grid-column: 1 / -1">
          <el-input v-model="form.scenario" :disabled="!canEdit" type="textarea" :rows="2" maxlength="500"
                    placeholder="例如：商业综合体一层餐饮区初起火灾，按发现 → 核实 → 广播 → 疏散 → 清点 → 终止的流程演练" />
        </el-form-item>
      </el-form>

      <el-row :gutter="14">
        <!-- 岗位 -->
        <el-col :span="8">
          <div class="fp-edit-card">
            <div class="fp-edit-card-head">
              <b>参与岗位（{{ form.posts.length }}）</b>
              <el-button v-if="canEdit" size="small" plain style="margin-left:auto" @click="addPost">+ 岗位</el-button>
            </div>
            <div v-for="(p, i) in form.posts" :key="i" style="border-bottom:1px dashed var(--fp-line);padding:8px 0">
              <el-input v-model="p.postCode" :disabled="!canEdit" size="small" placeholder="岗位编码（如 CTRL）"
                        style="margin-bottom:6px" />
              <el-input v-model="p.postName" :disabled="!canEdit" size="small" placeholder="岗位名称（如 消控室值班岗）"
                        style="margin-bottom:6px" />
              <el-input v-model="p.assignee" :disabled="!canEdit" size="small" placeholder="岗位人员（可选）"
                        style="margin-bottom:6px" />
              <div style="display:flex;gap:6px">
                <el-input v-model="p.contact" :disabled="!canEdit" size="small" placeholder="联系方式（可选）" />
                <el-button v-if="canEdit" size="small" link type="danger" @click="removePost(i)">删除</el-button>
              </div>
            </div>
            <el-empty v-if="!form.posts.length" description="还没有岗位" :image-size="60" />
          </div>
        </el-col>

        <!-- 指令卡 -->
        <el-col :span="16">
          <div class="fp-edit-card-head">
            <b>现场指令卡（{{ form.cards.length }}）</b>
            <el-button v-if="canEdit" size="small" plain style="margin-left:auto" @click="addCard">+ 指令卡</el-button>
          </div>
          <div v-for="(c, i) in form.cards" :key="i" class="fp-edit-card">
            <div class="fp-edit-card-head">
              <span class="fp-node-seq">{{ c.seqNo || i + 1 }}</span>
              <el-input-number v-model="c.seqNo" :disabled="!canEdit" :min="1" :max="999" controls-position="right"
                              size="small" style="width:108px" />
              <el-input v-model="c.title" :disabled="!canEdit" size="small" placeholder="指令标题" style="flex:1" />
              <el-button v-if="canEdit" size="small" link type="danger" @click="removeCard(i)">删除</el-button>
            </div>
            <el-input v-model="c.content" :disabled="!canEdit" type="textarea" :rows="2" size="small"
                      placeholder="指令内容" style="margin-bottom:6px" />
            <el-input v-model="c.sceneHint" :disabled="!canEdit" size="small" placeholder="现场提示（可选）"
                      style="margin-bottom:6px" />
            <div style="display:flex;gap:10px;flex-wrap:wrap;align-items:center">
              <span style="font-size:13px;color:var(--fp-text-sub)">目标岗位</span>
              <el-select v-model="c.targetPostCode" :disabled="!canEdit" size="small" style="width:200px"
                         placeholder="选择岗位">
                <el-option v-for="p in form.posts" :key="p.postCode"
                           :label="p.postName + '（' + p.postCode + '）'" :value="p.postCode" />
              </el-select>
              <span style="font-size:13px;color:var(--fp-text-sub)">响应时限</span>
              <el-input-number v-model="c.timeLimitSeconds" :disabled="!canEdit" :min="1" :max="3600"
                               :step="10" controls-position="right" size="small" style="width:130px" />
              <span style="font-size:12px;color:#999">秒</span>
            </div>
            <div style="display:flex;gap:10px;flex-wrap:wrap;align-items:flex-start;margin-top:8px">
              <span style="font-size:13px;color:var(--fp-text-sub);padding-top:4px">前置节点</span>
              <el-select v-model="c.prereqIndexes" :disabled="!canEdit" size="small" multiple collapse-tags
                         collapse-tags-tooltip placeholder="无前置（首张卡留空）" style="flex:1;min-width:260px">
                <el-option v-for="other in otherCardOptions(i)" :key="other.index"
                           :label="'#' + (other.seqNo) + ' ' + (other.title || '未命名')"
                           :value="other.index" />
              </el-select>
            </div>
          </div>
          <el-empty v-if="!form.cards.length" description="还没有指令卡，点右上角添加或从脚本导入" />
        </el-col>
      </el-row>
    </div>

    <!-- 脚本导入抽屉 -->
    <el-drawer v-model="templateVisible" title="常用演练脚本" size="520px">
      <div v-loading="templateLoading">
        <el-alert type="info" :closable="false" show-icon style="margin-bottom:10px"
                  title="导入会把脚本的岗位与指令链复制到本草稿（同编码岗位合并）；场次开始后再改脚本不影响本场。" />
        <div v-for="t in templates" :key="t.id" class="fp-post-card" style="margin-bottom:10px">
          <div class="fp-post-head">
            <span>{{ t.name }}</span>
            <el-tag size="small" effect="plain">{{ t.postCount }} 岗 / {{ t.cardCount }} 卡</el-tag>
            <el-button size="small" type="primary" plain style="margin-left:auto"
                       :disabled="!canEdit" @click="importTemplate(t.id)">导入</el-button>
          </div>
          <div style="font-size:12.5px;color:var(--fp-text-sub);margin-top:4px">{{ t.description }}</div>
        </div>
        <el-empty v-if="!templates.length" description="暂无常用脚本" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { buildingApi, drillApi, newActionKey } from '../../api'
import { SESSION_STATUS_TAG } from './drill-util'

const route = useRoute()
const nav = useRouter()
const id = Number(route.params.id)

const loading = ref(false)
const saving = ref(false)
const detail = ref({ status: 'DRAFT', drillNo: '' })
const buildings = ref([])

const form = ref({ title: '', buildingId: null, scenario: '', posts: [], cards: [] })

const templateVisible = ref(false)
const templateLoading = ref(false)
const templates = ref([])

const canEdit = computed(() => detail.value.status === 'DRAFT')
const frozen = computed(() => ['RUNNING', 'PAUSED', 'FINISHED', 'CANCELED'].includes(detail.value.status))

async function load() {
  loading.value = true
  try {
    detail.value = await drillApi.detail(id)
    fillFormFromDetail(detail.value)
  } finally {
    loading.value = false
  }
}

/** 详情里的卡是冻结后的数据形状，编排表单只取编排字段 */
function fillFormFromDetail(d) {
  form.value.title = d.title
  form.value.buildingId = d.buildingId
  form.value.scenario = d.scenario
  form.value.posts = d.posts.map((p) => ({
    postCode: p.postCode,
    postName: p.postName,
    assignee: p.assignee || '',
    contact: p.contact || ''
  }))
  // 开始前卡上只有 targetPostId，编码快照在开始时才写入，两种都兜住
  const postCodeById = new Map(d.posts.map((p) => [p.id, p.postCode]))
  form.value.cards = d.cards.map((c) => ({
    seqNo: c.seqNo,
    title: c.title,
    content: c.content,
    sceneHint: c.sceneHint || '',
    targetPostCode: c.targetPostCode || postCodeById.get(c.targetPostId) || '',
    timeLimitSeconds: c.timeLimitSeconds,
    prereqIndexes: []
  }))
  // 前置节点按卡顺序号映射回当前下标
  d.cards.forEach((c, i) => {
    form.value.cards[i].prereqIndexes = (c.prereqSeqs || [])
      .map((seq) => d.cards.findIndex((x) => x.seqNo === seq))
      .filter((x) => x >= 0)
  })
}

function addPost() {
  form.value.posts.push({ postCode: '', postName: '', assignee: '', contact: '' })
}

/** 前置节点可选卡：排除自身 */
function otherCardOptions(selfIndex) {
  return form.value.cards
    .map((c, index) => ({ index, seqNo: c.seqNo || index + 1, title: c.title }))
    .filter((c) => c.index !== selfIndex)
}

function removePost(i) {
  const code = form.value.posts[i].postCode
  form.value.posts.splice(i, 1)
  // 清理指向该岗位的卡目标
  form.value.cards.forEach((c) => {
    if (c.targetPostCode === code) c.targetPostCode = ''
  })
}

function addCard() {
  const seq = form.value.cards.length + 1
  form.value.cards.push({
    seqNo: seq,
    title: '',
    content: '',
    sceneHint: '',
    targetPostCode: form.value.posts[0]?.postCode || '',
    timeLimitSeconds: 60,
    prereqIndexes: form.value.cards.length ? [form.value.cards.length - 1] : []
  })
}

function removeCard(i) {
  form.value.cards.splice(i, 1)
  form.value.cards.forEach((c) => {
    c.prereqIndexes = c.prereqIndexes
      .filter((x) => x !== i)
      .map((x) => (x > i ? x - 1 : x))
  })
}

/** 组装整单全量保存请求；clientId 用下标，前置引用也是下标，整单内一一对应 */
function buildPayload() {
  const payload = {
    title: form.value.title,
    buildingId: form.value.buildingId || null,
    scenario: form.value.scenario || null,
    posts: form.value.posts.map((p, i) => ({
      postCode: p.postCode, postName: p.postName, assignee: p.assignee || null,
      contact: p.contact || null, sortNo: i + 1
    })),
    cards: form.value.cards.map((c, i) => ({
      clientId: 'c' + i,
      seqNo: c.seqNo || i + 1,
      title: c.title,
      content: c.content,
      sceneHint: c.sceneHint || null,
      targetPostCode: c.targetPostCode,
      timeLimitSeconds: c.timeLimitSeconds,
      prereqClientIds: (c.prereqIndexes || []).map((pi) => 'c' + pi)
    }))
  }
  return payload
}

/** 提交前的轻量校验（最终以后端为准） */
function validateForm() {
  if (!form.value.title || !form.value.title.trim()) {
    ElMessage.warning('请填写演练名称')
    return false
  }
  if (!form.value.posts.length) {
    ElMessage.warning('请至少录入一个参与岗位')
    return false
  }
  const codes = new Set()
  for (const p of form.value.posts) {
    if (!p.postCode || !p.postCode.trim() || !p.postName || !p.postName.trim()) {
      ElMessage.warning('每个岗位都要填写编码与名称')
      return false
    }
    if (codes.has(p.postCode)) {
      ElMessage.warning(`岗位编码「${p.postCode}」重复`)
      return false
    }
    codes.add(p.postCode)
  }
  if (!form.value.cards.length) {
    ElMessage.warning('请至少编排一张现场指令卡')
    return false
  }
  const seqs = new Set()
  for (const c of form.value.cards) {
    if (!c.title || !c.title.trim() || !c.content || !c.content.trim()) {
      ElMessage.warning(`第 ${c.seqNo} 张指令卡缺少标题或内容`)
      return false
    }
    if (!c.targetPostCode) {
      ElMessage.warning(`第 ${c.seqNo} 张指令卡未选择目标岗位`)
      return false
    }
    if (seqs.has(c.seqNo)) {
      ElMessage.warning(`指令卡顺序号 ${c.seqNo} 重复`)
      return false
    }
    seqs.add(c.seqNo)
  }
  return true
}

async function onSave(markReady) {
  if (!validateForm()) {
    return
  }
  saving.value = true
  try {
    const saved = await drillApi.saveDraft(id, buildPayload())
    detail.value = saved
    ElMessage.success('编排已保存')
    if (markReady) {
      await drillApi.ready(id, { actionKey: newActionKey() })
      ElMessage.success('演练已就绪')
      await load()
    }
  } finally {
    saving.value = false
  }
}

async function onReopen() {
  saving.value = true
  try {
    detail.value = await drillApi.reopen(id, { actionKey: newActionKey() })
    ElMessage.success('已退回草稿，可以继续编排')
  } finally {
    saving.value = false
  }
}

async function onStart() {
  await ElMessageBox.confirm(
    '开始后本场指令链将被冻结：之后修改常用脚本或本编排都不会改变本场，确认开始？',
    '开始演练', { type: 'warning', confirmButtonText: '开始', cancelButtonText: '再想想' }
  )
  saving.value = true
  try {
    await drillApi.start(id, { actionKey: newActionKey() })
    ElMessage.success('演练已开始，指令链已冻结')
    nav.replace(`/drills/${id}/run`)
  } finally {
    saving.value = false
  }
}

async function openTemplates() {
  templateVisible.value = true
  templateLoading.value = true
  try {
    templates.value = await drillApi.templateList()
  } finally {
    templateLoading.value = false
  }
}

async function importTemplate(templateId) {
  const tpl = await drillApi.templateDetail(templateId)
  // 岗位：同编码合并（不重复添加），新岗位追加
  const existingCodes = new Set(form.value.posts.map((p) => p.postCode))
  for (const p of tpl.posts) {
    if (!existingCodes.has(p.postCode)) {
      form.value.posts.push({ postCode: p.postCode, postName: p.postName, assignee: '', contact: '' })
      existingCodes.add(p.postCode)
    }
  }
  // 卡：按脚本顺序追加到当前卡清单后
  const base = form.value.cards.length
  const localBySeq = new Map(tpl.cards.map((c, i) => [c.seqNo, base + i]))
  tpl.cards.forEach((c, i) => {
    form.value.cards.push({
      seqNo: base + i + 1,
      title: c.title,
      content: c.content,
      sceneHint: c.sceneHint || '',
      targetPostCode: existingCodes.has(c.targetPostCode) ? c.targetPostCode : (form.value.posts[0]?.postCode || ''),
      timeLimitSeconds: c.timeLimitSeconds,
      prereqIndexes: (c.prereqSeqs || []).map((s) => localBySeq.get(s)).filter((x) => x !== undefined)
    })
  })
  ElMessage.success(`已导入脚本「${tpl.name}」，请保存草稿`)
  templateVisible.value = false
}

function goList() {
  nav.push('/drills')
}

function goRun() {
  nav.push(`/drills/${id}/run`)
}

onMounted(async () => {
  buildings.value = await buildingApi.list()
  await load()
})
</script>
