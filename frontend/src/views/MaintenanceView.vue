<template>
  <div class="fp-panel">
    <div class="fp-panel-head">
      <span class="fp-panel-title">维保看板</span>
      <span class="fp-panel-tip">
        三列：待派工 / 处理中（已派工 + 处理中）/ 已完成（已完成 + 已验收）· 卡片上直接推进状态
      </span>
      <div class="fp-space">
        <span class="fp-panel-tip">单据统计：合计金额 ¥{{ board.totalAmount || 0 }}</span>
        <el-button size="small" type="primary" plain @click="openCreate">+ 登记维保单</el-button>
        <el-button size="small" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="fp-panel-body">
      <div class="fp-board">
        <div v-for="col in columns" :key="col.key" class="fp-board-col">
          <div class="fp-board-head">
            <span>{{ col.title }}</span>
            <span class="fp-board-count">{{ col.list.length }} 单</span>
          </div>
          <div v-for="o in col.list" :key="o.id" class="fp-card" :class="'status-' + o.status">
            <div style="display: flex; align-items: center; gap: 6px">
              <span class="fp-card-no">{{ o.orderNo }}</span>
              <el-tag size="small" effect="dark">{{ o.orderTypeName }}</el-tag>
              <el-tag size="small" :type="statusTag(o.status)">{{ o.statusName }}</el-tag>
            </div>
            <div class="fp-card-row">
              <span>设施：</span><b>{{ o.targetName || '—' }}</b>
            </div>
            <div class="fp-card-row">
              <span>{{ o.buildingName || '未关联建筑' }} · {{ o.pointCode ? '点位 ' + o.pointCode : (o.extCode ? '气瓶 ' + o.extCode : '') }}</span>
            </div>
            <div class="fp-card-row" v-if="o.disablePermitNo">
              <el-tag size="small" type="danger" effect="plain">
                {{ o.disablePermitNo }} 许可停用中：维保照常流转，完工也不会自动恢复为可用
              </el-tag>
            </div>
            <div class="fp-card-row" style="color: #2c2320">
              <span>故障：</span>{{ o.faultDesc || '—' }}
            </div>
            <div class="fp-card-row" v-if="o.solution">
              <span>处理：</span>{{ o.solution }}
            </div>
            <div class="fp-card-row">
              <span>服务商：{{ o.vendor || '待指派' }} · 责任人：{{ o.ownerPerson || '待指派' }}</span>
            </div>
            <div class="fp-card-row">
              <span>材料费 ¥{{ o.materialFee }}</span>
              <span>人工费 ¥{{ o.laborFee }}</span>
              <span class="fp-card-amount">合计 ¥{{ o.totalFee }}</span>
            </div>
            <div class="fp-card-row" v-if="o.finishDate">
              <span>完工日期：{{ o.finishDate }}</span>
            </div>
            <div class="fp-card-actions">
              <el-button v-if="o.status === 'PENDING'" size="small" type="primary" @click="openAssign(o)">派工</el-button>
              <el-button v-if="o.status === 'ASSIGNED'" size="small" type="primary" @click="advance(o, 'WORKING')">开工</el-button>
              <el-button v-if="o.status === 'WORKING'" size="small" type="warning" @click="openFinish(o)">完工登记</el-button>
              <el-button v-if="o.status === 'DONE'" size="small" type="success" @click="advance(o, 'ACCEPTED')">验收</el-button>
              <el-button v-if="o.status === 'ACCEPTED'" size="small" disabled>已闭环</el-button>
            </div>
          </div>
          <el-empty v-if="col.list.length === 0" description="暂无工单" :image-size="48" />
        </div>
      </div>
    </div>

    <el-dialog v-model="assignVisible" title="派工" width="440px">
      <el-form :model="assignForm" label-width="90px">
        <el-form-item label="服务商">
          <el-input v-model="assignForm.vendor" placeholder="如 泰盛消防维保" />
        </el-form-item>
        <el-form-item label="责任人">
          <el-input v-model="assignForm.ownerPerson" placeholder="如 周大勇" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAssign">确认派工</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="finishVisible" title="完工登记" width="440px">
      <el-form :model="finishForm" label-width="90px">
        <el-form-item label="处理措施">
          <el-input v-model="finishForm.solution" type="textarea" :rows="3" placeholder="完工必填" />
        </el-form-item>
        <el-form-item label="材料费">
          <el-input-number v-model="finishForm.materialFee" :min="0" :step="10" />
        </el-form-item>
        <el-form-item label="人工费">
          <el-input-number v-model="finishForm.laborFee" :min="0" :step="10" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="finishVisible = false">取消</el-button>
        <el-button type="primary" @click="submitFinish">确认完工</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="createVisible" title="登记维保单" width="520px">
      <el-form :model="createForm" label-width="90px">
        <el-form-item label="维保类型">
          <el-select v-model="createForm.orderType" style="width: 100%">
            <el-option label="维修" value="REPAIR" />
            <el-option label="更换" value="REPLACE" />
            <el-option label="充装" value="REFILL" />
            <el-option label="检测" value="TEST" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联点位">
          <el-select v-model="createForm.pointId" filterable clearable placeholder="按点位编号搜索" style="width: 100%">
            <el-option v-for="p in points" :key="p.id" :label="p.pointCode + ' · ' + p.facilityTypeName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联气瓶">
          <el-select v-model="createForm.extId" filterable clearable placeholder="按钢印号搜索" style="width: 100%">
            <el-option v-for="e in cylinders" :key="e.id" :label="e.steelNo + ' · ' + e.cylTypeName" :value="e.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="故障描述">
          <el-input v-model="createForm.faultDesc" type="textarea" :rows="3" />
        </el-form-item>
        <div class="fp-form-grid">
          <el-form-item label="服务商">
            <el-input v-model="createForm.vendor" />
          </el-form-item>
          <el-form-item label="责任人">
            <el-input v-model="createForm.ownerPerson" />
          </el-form-item>
          <el-form-item label="材料费">
            <el-input-number v-model="createForm.materialFee" :min="0" :step="10" />
          </el-form-item>
          <el-form-item label="人工费">
            <el-input-number v-model="createForm.laborFee" :min="0" :step="10" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, reactive, ref } from 'vue'
import { extinguisherApi, facilityApi, maintenanceApi } from '../api'

const fp = inject('fp')

const board = ref({ pending: [], working: [], done: [], totalAmount: 0 })
const points = ref([])
const cylinders = ref([])
const assignVisible = ref(false)
const finishVisible = ref(false)
const createVisible = ref(false)

const assignForm = reactive({ id: null, vendor: '', ownerPerson: '' })
const finishForm = reactive({ id: null, solution: '', materialFee: 0, laborFee: 0 })
const createForm = reactive({
  orderType: 'REPAIR',
  pointId: null,
  extId: null,
  faultDesc: '',
  vendor: '',
  ownerPerson: '',
  materialFee: 0,
  laborFee: 0
})

const columns = computed(() => [
  { key: 'pending', title: '待派工', list: board.value.pending || [] },
  { key: 'working', title: '处理中', list: board.value.working || [] },
  { key: 'done', title: '已完成', list: board.value.done || [] }
])

function statusTag(status) {
  if (status === 'PENDING') return 'warning'
  if (status === 'ASSIGNED' || status === 'WORKING') return 'primary'
  if (status === 'ACCEPTED') return 'info'
  return 'success'
}

async function load() {
  board.value = await maintenanceApi.board()
}

function openAssign(order) {
  assignForm.id = order.id
  assignForm.vendor = order.vendor || ''
  assignForm.ownerPerson = order.ownerPerson || ''
  assignVisible.value = true
}

async function submitAssign() {
  await maintenanceApi.assign(assignForm.id, {
    vendor: assignForm.vendor,
    ownerPerson: assignForm.ownerPerson
  })
  assignVisible.value = false
  await load()
  await fp.reloadOverview()
}

async function advance(order, target) {
  await maintenanceApi.advance(order.id, { target })
  await load()
  await fp.reloadOverview()
}

function openFinish(order) {
  finishForm.id = order.id
  finishForm.solution = order.solution || ''
  finishForm.materialFee = Number(order.materialFee || 0)
  finishForm.laborFee = Number(order.laborFee || 0)
  finishVisible.value = true
}

async function submitFinish() {
  await maintenanceApi.advance(finishForm.id, {
    target: 'DONE',
    solution: finishForm.solution,
    materialFee: finishForm.materialFee,
    laborFee: finishForm.laborFee
  })
  finishVisible.value = false
  await load()
  await fp.reloadOverview()
}

async function openCreate() {
  if (fp.buildingId.value) {
    points.value = await facilityApi.list({ buildingId: fp.buildingId.value })
    cylinders.value = await extinguisherApi.list(fp.buildingId.value)
  }
  Object.assign(createForm, {
    orderType: 'REPAIR',
    pointId: null,
    extId: null,
    faultDesc: '',
    vendor: '',
    ownerPerson: '',
    materialFee: 0,
    laborFee: 0
  })
  createVisible.value = true
}

async function submitCreate() {
  await maintenanceApi.create({
    ...createForm,
    pointId: createForm.pointId || null,
    extId: createForm.extId || null
  })
  createVisible.value = false
  await load()
  await fp.reloadOverview()
}

onMounted(load)
</script>
