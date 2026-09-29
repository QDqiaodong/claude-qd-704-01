<template>
  <div class="fp-panel">
    <div class="fp-panel-head">
      <span class="fp-panel-title">建筑与楼层</span>
      <span class="fp-panel-tip">建档时按地上 / 地下层数自动生成楼层，楼层是平面图的来源</span>
      <div class="fp-space">
        <el-button size="small" type="primary" plain @click="openCreate">+ 新增建筑</el-button>
      </div>
    </div>

    <div class="fp-panel-body">
      <el-table :data="buildings" row-key="id" border stripe>
        <el-table-column type="expand">
          <template #default="{ row }">
            <div style="padding: 8px 16px 12px">
              <div style="font-size: 13px; color: #7a6b66; margin-bottom: 6px">
                楼层清单（{{ row.floors.length }} 层）· 单层面积 {{ row.areaSqm ? (row.areaSqm / (row.floors.length || 1)).toFixed(2) : '—' }} ㎡
              </div>
              <el-table :data="row.floors" size="small" border>
                <el-table-column prop="floorCode" label="楼层代号" width="100" />
                <el-table-column prop="floorName" label="楼层名称" width="120" />
                <el-table-column prop="sortNo" label="排序" width="80" />
                <el-table-column prop="areaSqm" label="面积(㎡)" width="110" />
                <el-table-column prop="roomCount" label="分区数" width="90" />
                <el-table-column prop="exitCount" label="安全出口" width="90" />
                <el-table-column prop="pointCount" label="点位" width="80" />
                <el-table-column label="异常点位" min-width="120">
                  <template #default="{ row: f }">
                    <el-tag size="small" :type="f.abnormalCount > 0 ? 'danger' : 'info'">
                      {{ f.abnormalCount }}
                    </el-tag>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="code" label="建筑编号" width="100" />
        <el-table-column prop="name" label="建筑名称" min-width="180" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag size="small" type="warning" effect="plain">{{ row.buildingTypeName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="地上/地下" width="110">
          <template #default="{ row }">{{ row.floorsAbove }} / {{ row.floorsBelow }}</template>
        </el-table-column>
        <el-table-column prop="areaSqm" label="建筑面积(㎡)" width="130" />
        <el-table-column prop="fireManager" label="消防负责人" width="110" />
        <el-table-column prop="contactPhone" label="联系电话" width="130" />
        <el-table-column prop="pointCount" label="点位总数" width="100" />
        <el-table-column prop="address" label="地址" min-width="200" />
        <el-table-column label="操作" width="130">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="pick(row.id)">看平面图</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="createVisible" title="新增建筑（楼层自动生成）" width="640px">
      <el-form :model="form" label-width="100px">
        <div class="fp-form-grid">
          <el-form-item label="建筑编号">
            <el-input v-model="form.code" placeholder="如 B06，只用字母数字与短横线" />
          </el-form-item>
          <el-form-item label="建筑名称">
            <el-input v-model="form.name" />
          </el-form-item>
          <el-form-item label="建筑类型">
            <el-select v-model="form.buildingType" style="width: 100%">
              <el-option label="住宅" value="RESIDENTIAL" />
              <el-option label="商业" value="COMMERCIAL" />
              <el-option label="厂房" value="FACTORY" />
              <el-option label="办公" value="OFFICE" />
              <el-option label="仓储" value="WAREHOUSE" />
            </el-select>
          </el-form-item>
          <el-form-item label="建筑面积(㎡)">
            <el-input-number v-model="form.areaSqm" :min="0" :step="100" style="width: 100%" />
          </el-form-item>
          <el-form-item label="地上层数">
            <el-input-number v-model="form.floorsAbove" :min="1" :step="1" style="width: 100%" />
          </el-form-item>
          <el-form-item label="地下层数">
            <el-input-number v-model="form.floorsBelow" :min="0" :step="1" style="width: 100%" />
          </el-form-item>
          <el-form-item label="消防负责人">
            <el-input v-model="form.fireManager" />
          </el-form-item>
          <el-form-item label="联系电话">
            <el-input v-model="form.contactPhone" />
          </el-form-item>
        </div>
        <el-form-item label="详细地址">
          <el-input v-model="form.address" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存并生成楼层</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { buildingApi } from '../api'

const fp = inject('fp')
const router = useRouter()

const buildings = ref([])
const createVisible = ref(false)

const form = reactive({
  code: '',
  name: '',
  address: '',
  buildingType: 'COMMERCIAL',
  floorsAbove: 6,
  floorsBelow: 1,
  areaSqm: 5000,
  fireManager: '',
  contactPhone: ''
})

/** 建筑台账直接复用 App 顶栏那份列表数据，保证建筑上下文一致 */
const computedBuildings = computed(() => fp.buildings.value)

async function load() {
  buildings.value = await buildingApi.list()
  fp.buildings.value = buildings.value
}

function pick(id) {
  fp.buildingId.value = id
  router.push('/plan')
}

function openCreate() {
  Object.assign(form, {
    code: '',
    name: '',
    address: '',
    buildingType: 'COMMERCIAL',
    floorsAbove: 6,
    floorsBelow: 1,
    areaSqm: 5000,
    fireManager: '',
    contactPhone: ''
  })
  createVisible.value = true
}

async function submit() {
  await buildingApi.create({ ...form })
  createVisible.value = false
  await load()
  await fp.reloadOverview()
}

watch(computedBuildings, (val) => {
  if (val.length > 0) {
    buildings.value = val
  }
})

onMounted(load)
</script>
