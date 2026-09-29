<template>
  <div class="fp-panel">
    <div class="fp-panel-head">
      <span class="fp-panel-title">巡检记录</span>
      <span class="fp-panel-tip">
        展开行看每个点位的巡检结论；收单时会按结论刷新点位的上次检查日（下一次到期日自动重算）
      </span>
      <div class="fp-space">
        <el-checkbox v-model="onlyCurrent" style="margin-right: 8px">只看当前建筑</el-checkbox>
        <el-button size="small" type="primary" plain @click="openCreate">+ 发起巡检</el-button>
      </div>
    </div>

    <div class="fp-panel-body">
      <el-table :data="rows" border stripe>
        <el-table-column type="expand">
          <template #default="{ row }">
            <div style="padding: 8px 16px 12px">
              <div style="font-size: 13px; color: #7a6b66; margin-bottom: 6px">
                巡检明细：应检 {{ row.expectedCount }} / 实检 {{ row.actualCount }} / 异常 {{ row.abnormalCount }}
              </div>
              <el-table :data="row.items" size="small" border :row-class-name="rowClass">
                <el-table-column prop="pointCode" label="点位编号" width="140" />
                <el-table-column prop="facilityTypeName" label="设施" width="110" />
                <el-table-column prop="floorCode" label="楼层" width="70" />
                <el-table-column prop="locationDesc" label="位置" min-width="160" />
                <el-table-column label="许可停用" width="170">
                  <template #default="{ row: item }">
                    <el-tag v-if="item.disablePermitNo" size="small" type="danger" effect="plain">
                      {{ item.disablePermitNo }}
                    </el-tag>
                    <span v-else style="color: #bdbdbd; font-size: 12px">—</span>
                  </template>
                </el-table-column>
                <el-table-column label="巡检结论" width="200">
                  <template #default="{ row: item }">
                    <el-select v-model="item.result" size="small" style="width: 120px" :disabled="row.status === 'COMPLETED'">
                      <el-option label="未检" value="PENDING" />
                      <el-option label="正常" value="NORMAL" />
                      <el-option label="异常" value="ABNORMAL" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="异常描述" min-width="220">
                  <template #default="{ row: item }">
                    <el-input v-model="item.problemDesc" size="small" placeholder="异常时必填" :disabled="row.status === 'COMPLETED'" />
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="90">
                  <template #default="{ row: item }">
                    <el-button size="small" link type="primary" :disabled="row.status === 'COMPLETED'" @click="saveItem(row, item)">
                      保存
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="inspectNo" label="巡检单号" width="150" />
        <el-table-column prop="buildingName" label="建筑" min-width="170" />
        <el-table-column prop="inspectTypeName" label="类型" width="80">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.inspectTypeName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="inspector" label="巡检人" width="100" />
        <el-table-column prop="inspectDate" label="巡检日期" width="120" />
        <el-table-column label="应检/实检" width="110">
          <template #default="{ row }">{{ row.expectedCount }} / {{ row.actualCount }}</template>
        </el-table-column>
        <el-table-column label="异常数" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.abnormalCount > 0 ? 'danger' : 'info'">{{ row.abnormalCount }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'COMPLETED' ? 'success' : 'warning'">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button
              size="small"
              type="success"
              plain
              :disabled="row.status === 'COMPLETED'"
              @click="complete(row)"
            >
              完成巡检
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="createVisible" title="发起巡检（明细行自动生成）" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="被检建筑">
          <el-select v-model="form.buildingId" style="width: 100%">
            <el-option v-for="b in fp.buildings.value" :key="b.id" :label="b.name" :value="b.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="巡检范围">
          <el-select v-model="form.floorId" clearable placeholder="留空=整栋楼" style="width: 100%">
            <el-option v-for="f in floors" :key="f.id" :label="f.floorCode + ' ' + f.floorName" :value="f.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="巡检类型">
          <el-select v-model="form.inspectType" style="width: 100%">
            <el-option label="日巡" value="DAILY" />
            <el-option label="月检" value="MONTHLY" />
            <el-option label="季检" value="QUARTERLY" />
            <el-option label="年检" value="YEARLY" />
          </el-select>
        </el-form-item>
        <el-form-item label="巡检人">
          <el-input v-model="form.inspector" placeholder="如 王海涛" />
        </el-form-item>
        <el-form-item label="巡检日期">
          <el-date-picker v-model="form.inspectDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">生成巡检单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { inject, onMounted, reactive, ref, watch } from 'vue'
import { buildingApi, inspectionApi } from '../api'

const fp = inject('fp')

const rows = ref([])
const floors = ref([])
const onlyCurrent = ref(true)
const createVisible = ref(false)

const form = reactive({
  buildingId: null,
  floorId: null,
  inspectType: 'MONTHLY',
  inspector: '',
  inspectDate: null,
  remark: ''
})

async function load() {
  rows.value = await inspectionApi.list(onlyCurrent.value ? fp.buildingId.value : null)
  if (fp.buildingId.value) {
    floors.value = await buildingApi.floors(fp.buildingId.value)
  }
}

function rowClass({ row }) {
  return row.result === 'ABNORMAL' ? 'fp-row-danger' : ''
}

async function saveItem(inspection, item) {
  await inspectionApi.saveItem(inspection.id, item.id, {
    result: item.result,
    problemDesc: item.problemDesc
  })
  if (item.result !== 'ABNORMAL') {
    item.problemDesc = ''
  }
  await load()
  await fp.reloadOverview()
}

async function complete(inspection) {
  await inspectionApi.complete(inspection.id)
  await load()
  await fp.reloadOverview()
}

function openCreate() {
  form.buildingId = fp.buildingId.value
  form.floorId = null
  form.inspectType = 'MONTHLY'
  form.inspector = ''
  form.inspectDate = null
  form.remark = ''
  createVisible.value = true
}

async function submit() {
  await inspectionApi.create({ ...form, floorId: form.floorId || null })
  createVisible.value = false
  await load()
}

watch([() => fp.buildingId.value, onlyCurrent], load)
onMounted(load)
</script>
