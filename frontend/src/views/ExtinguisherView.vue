<template>
  <div class="fp-panel">
    <div class="fp-panel-head">
      <span class="fp-panel-title">灭火器与气瓶台账</span>
      <span class="fp-panel-tip">水压试验 / 报废日期倒计时，临期与到期标红；充装后会按今天重算下次月检日</span>
      <div class="fp-space">
        <el-button size="small" type="primary" plain @click="openCreate">+ 登记新瓶</el-button>
        <el-button size="small" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="fp-panel-body">
      <div style="display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 12px">
        <span class="fp-badge ok">在册 {{ summary.total || 0 }} 只</span>
        <span class="fp-badge ok">正常 {{ summary.normal || 0 }}</span>
        <span class="fp-badge warn">待充装 {{ summary.refill || 0 }}</span>
        <span class="fp-badge warn">待水压 {{ summary.hydro || 0 }}</span>
        <span class="fp-badge muted">已报废 {{ summary.scrap || 0 }}</span>
        <span class="fp-badge danger">临期预警 {{ summary.soonExpire || 0 }}</span>
      </div>

      <el-table :data="rows" border stripe :row-class-name="rowClass">
        <el-table-column prop="extNo" label="台账编码" width="150" />
        <el-table-column prop="steelNo" label="钢印编号" width="150" />
        <el-table-column label="类型/充装量" width="130">
          <template #default="{ row }">{{ row.cylTypeName }} {{ row.chargeKg }}kg</template>
        </el-table-column>
        <el-table-column prop="buildingName" label="所属建筑" min-width="160" />
        <el-table-column prop="floorCode" label="楼层" width="70" />
        <el-table-column prop="pointCode" label="存放点位" width="150" />
        <el-table-column prop="lastFillDate" label="上次充装" width="120" />
        <el-table-column label="水压试验到期" width="160">
          <template #default="{ row }">
            <div>{{ row.hydroTestDue || '—' }}</div>
            <span class="fp-badge" :class="badge(row.hydroDaysLeft, 120).cls">{{ badge(row.hydroDaysLeft, 120).text }}</span>
          </template>
        </el-table-column>
        <el-table-column label="报废日期" width="160">
          <template #default="{ row }">
            <div>{{ row.scrapDate || '—' }}</div>
            <span class="fp-badge" :class="badge(row.scrapDaysLeft, 365).cls">{{ badge(row.scrapDaysLeft, 365).text }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="nextCheckDate" label="下次月检" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button size="small" link type="success" :disabled="row.status === 'SCRAP'" @click="refill(row)">充装</el-button>
            <el-button size="small" link type="warning" :disabled="row.status === 'SCRAP'" @click="hydro(row)">送检</el-button>
            <el-button size="small" link type="danger" :disabled="row.status === 'SCRAP'" @click="scrap(row)">报废</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="createVisible" title="登记新瓶（台账编码与月检周期自动生成）" width="620px">
      <el-form :model="form" label-width="100px">
        <div class="fp-form-grid">
          <el-form-item label="钢印编号">
            <el-input v-model="form.steelNo" placeholder="如 GP-2026-0001" />
          </el-form-item>
          <el-form-item label="类型">
            <el-select v-model="form.cylType" style="width: 100%">
              <el-option label="干粉" value="POWDER" />
              <el-option label="二氧化碳" value="CO2" />
              <el-option label="水基" value="WATER" />
              <el-option label="泡沫" value="FOAM" />
            </el-select>
          </el-form-item>
          <el-form-item label="充装量(kg)">
            <el-input-number v-model="form.chargeKg" :min="0" :step="0.5" style="width: 100%" />
          </el-form-item>
          <el-form-item label="生产日期">
            <el-date-picker v-model="form.produceDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
          <el-form-item label="水压到期日">
            <el-date-picker v-model="form.hydroTestDue" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
          <el-form-item label="报废日期">
            <el-date-picker v-model="form.scrapDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
          <el-form-item label="存放楼层">
            <el-select v-model="form.floorId" clearable style="width: 100%">
              <el-option v-for="f in floors" :key="f.id" :label="f.floorCode + ' ' + f.floorName" :value="f.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="存放点位">
            <el-select v-model="form.pointId" filterable clearable style="width: 100%">
              <el-option v-for="p in storePoints" :key="p.id" :label="p.pointCode + ' · ' + p.locationDesc" :value="p.id" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="存放位置">
          <el-input v-model="form.storePlace" placeholder="如 地下一层配电间门口" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存登记</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { inject, onMounted, reactive, ref, watch } from 'vue'
import { buildingApi, extinguisherApi, facilityApi } from '../api'

const fp = inject('fp')

const rows = ref([])
const summary = ref({})
const floors = ref([])
const storePoints = ref([])
const createVisible = ref(false)

const form = reactive({
  steelNo: '',
  cylType: 'POWDER',
  chargeKg: 4,
  produceDate: null,
  hydroTestDue: null,
  scrapDate: null,
  floorId: null,
  pointId: null,
  storePlace: ''
})

async function load() {
  if (!fp.buildingId.value) {
    return
  }
  const [list, sum, floorList, pointList] = await Promise.all([
    extinguisherApi.list(fp.buildingId.value),
    extinguisherApi.summary(),
    buildingApi.floors(fp.buildingId.value),
    facilityApi.list({ buildingId: fp.buildingId.value, type: 'EXTINGUISHER' })
  ])
  rows.value = list
  summary.value = sum
  floors.value = floorList
  storePoints.value = pointList
}

function badge(days, threshold) {
  if (days == null || days >= 9999) {
    return { cls: 'muted', text: '未登记' }
  }
  if (days < 0) {
    return { cls: 'danger', text: `已逾期 ${Math.abs(days)} 天` }
  }
  if (days <= threshold) {
    return { cls: 'danger', text: `仅剩 ${days} 天` }
  }
  if (days <= threshold * 2) {
    return { cls: 'warn', text: `${days} 天` }
  }
  return { cls: 'ok', text: `${days} 天` }
}

function statusTag(status) {
  if (status === 'SCRAP') return 'danger'
  if (status === 'REFILL' || status === 'HYDRO') return 'warning'
  return 'success'
}

function rowClass({ row }) {
  return row.riskLevel === 'DANGER' ? 'fp-row-danger' : ''
}

async function refill(row) {
  await extinguisherApi.refill(row.id)
  await load()
}

async function hydro(row) {
  await extinguisherApi.hydro(row.id)
  await load()
}

async function scrap(row) {
  await extinguisherApi.scrap(row.id)
  await load()
  await fp.reloadOverview()
}

function openCreate() {
  Object.assign(form, {
    steelNo: '',
    cylType: 'POWDER',
    chargeKg: 4,
    produceDate: null,
    hydroTestDue: null,
    scrapDate: null,
    floorId: null,
    pointId: null,
    storePlace: ''
  })
  createVisible.value = true
}

async function submit() {
  await extinguisherApi.create({ ...form, buildingId: fp.buildingId.value })
  createVisible.value = false
  await load()
  await fp.reloadOverview()
}

watch(() => fp.buildingId.value, load)
onMounted(load)
</script>
