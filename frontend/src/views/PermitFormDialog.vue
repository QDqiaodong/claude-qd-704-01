<template>
  <el-dialog
    :model-value="modelValue"
    :title="form.id ? '编辑停用许可（草稿）' : '新增设施停用许可'"
    width="860px"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:modelValue', $event)"
    @closed="$emit('closed')"
  >
    <el-form :model="form" label-width="110px">
      <div class="fp-form-grid">
        <el-form-item label="停用原因">
          <el-input v-model="form.reason" placeholder="如 管网改造 / 设备检修 / 联动测试" />
        </el-form-item>
        <el-form-item label="现场负责人">
          <el-select v-model="form.siteOwnerId" style="width: 100%">
            <el-option
              v-for="u in siteOwners"
              :key="u.id"
              :label="u.displayName"
              :value="u.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="计划开始">
          <el-date-picker v-model="form.planStart" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="计划结束">
          <el-date-picker v-model="form.planEnd" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </div>

      <el-form-item label="受影响范围">
        <div style="width: 100%">
          <div style="margin-bottom: 6px; display: flex; gap: 8px; align-items: center">
            <el-select v-model="pickFloorId" size="small" style="width: 160px" placeholder="选择楼层加入点位">
              <el-option
                v-for="f in floors"
                :key="f.id"
                :label="f.floorCode + ' ' + f.floorName"
                :value="f.id"
              />
            </el-select>
            <el-button size="small" :disabled="scopeLocked" @click="toggleFloorPoints(false)">加入整层</el-button>
            <el-button size="small" :disabled="scopeLocked" @click="toggleFloorPoints(true)">移除整层</el-button>
            <el-tag size="small" type="danger" effect="plain" v-if="scopeLocked">
              范围已锁定：提交 / 被拒绝后不能增删点位
            </el-tag>
          </div>
          <el-table :data="selectableRows" size="small" border max-height="240"
                    :row-class-name="selectRowClass" @selection-change="onSelectionChange"
                    ref="selectTableRef">
            <el-table-column type="selection" width="46" :selectable="() => !scopeLocked" reserve-selection />
            <el-table-column prop="pointCode" label="点位编号" width="130" />
            <el-table-column label="楼层" width="70">
              <template #default="{ row }">{{ floorCodeOf(row.floorId) }}</template>
            </el-table-column>
            <el-table-column prop="facilityTypeName" label="设施" width="110" />
            <el-table-column prop="locationDesc" label="位置" min-width="160" />
            <el-table-column label="原状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="pointStatusType(row.status)">{{ row.statusName }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
          <div style="margin-top: 4px; font-size: 12px; color: #7a6b66">
            已选 {{ form.pointIds.length }} 个点位；许可停用只会叠加标记，不改变上面的原状态
          </div>
        </div>
      </el-form-item>

      <el-form-item label="补偿措施">
        <div style="width: 100%">
          <el-table :data="form.measures" size="small" border>
            <el-table-column label="类别" width="150">
              <template #default="{ row }">
                <el-select v-model="row.measureType" size="small">
                  <el-option label="替代灭火器" value="SPARE_EXT" />
                  <el-option label="加密巡查" value="EXTRA_PATROL" />
                  <el-option label="临时警戒" value="TEMP_BARRIER" />
                  <el-option label="其它措施" value="OTHER" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="措施内容" min-width="220">
              <template #default="{ row }">
                <el-input v-model="row.content" size="small" placeholder="如 东西疏散口各增配 4kg 干粉灭火器" />
              </template>
            </el-table-column>
            <el-table-column label="数量/频次" width="170">
              <template #default="{ row }">
                <el-input v-model="row.specDesc" size="small" placeholder="如 4 具 / 每 2 小时一次" />
              </template>
            </el-table-column>
            <el-table-column width="60">
              <template #default="{ $index }">
                <el-button size="small" link type="danger" @click="form.measures.splice($index, 1)">删</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-button size="small" style="margin-top: 6px" @click="addMeasure">+ 增加补偿措施</el-button>
        </div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button @click="submit(false)">保存草稿</el-button>
      <el-button type="primary" @click="submit(true)">保存并提交批准</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, inject, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { buildingApi, facilityApi, permitApi, userApi } from '../api'

const props = defineProps({
  modelValue: Boolean,
  // 传入已有详情=编辑草稿；null=新建
  permit: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'saved', 'closed'])

const fp = inject('fp')
const users = ref([])
const floors = ref([])
const points = ref([])
const pickFloorId = ref(null)
const selectTableRef = ref(null)

const form = reactive({
  id: null,
  reason: '',
  siteOwnerId: null,
  planStart: null,
  planEnd: null,
  pointIds: [],
  measures: []
})

const siteOwners = computed(() => users.value.filter((u) => u.role === 'SITE_OWNER'))
const scopeLocked = computed(() => !!form.id && props.permit && props.permit.scopeLocked)

const selectableRows = computed(() => {
  if (!pickFloorId.value) return points.value
  return points.value.filter((p) => p.floorId === pickFloorId.value)
})

function floorCodeOf(floorId) {
  const f = floors.value.find((x) => x.id === floorId)
  return f ? f.floorCode : ''
}

function pointStatusType(status) {
  if (status === 'FAULT') return 'danger'
  if (status === 'REPLACE') return 'warning'
  if (status === 'DISABLED') return 'info'
  return 'success'
}

function onSelectionChange(rows) {
  const visibleIds = new Set(selectableRows.value.map((r) => r.id))
  const others = form.pointIds.filter((id) => !visibleIds.has(id))
  form.pointIds = [...others, ...rows.map((r) => r.id)]
}

function toggleFloorPoints(remove) {
  if (scopeLocked.value) return
  const floorPoints = selectableRows.value
  if (!floorPoints.length) {
    ElMessage.warning('该楼层暂无可选点位')
    return
  }
  const ids = new Set(form.pointIds)
  floorPoints.forEach((p) => (remove ? ids.delete(p.id) : ids.add(p.id)))
  form.pointIds = [...ids]
  // 同步表格勾选态
  selectTableRef.value?.clearSelection?.()
  floorPoints.forEach((p) => {
    if (!remove) selectTableRef.value?.toggleRowSelection?.(p, true)
  })
}

function addMeasure() {
  form.measures.push({ measureType: 'SPARE_EXT', content: '', specDesc: '' })
}

async function loadBase() {
  if (!users.value.length) {
    users.value = await userApi.list()
  }
  floors.value = await buildingApi.floors(fp.buildingId.value)
  points.value = await facilityApi.list({ buildingId: fp.buildingId.value })
}

watch(
  () => props.modelValue,
  async (visible) => {
    if (!visible) return
    await loadBase()
    form.id = null
    form.reason = ''
    form.siteOwnerId = siteOwners.value[0] ? siteOwners.value[0].id : null
    form.planStart = new Date().toISOString().slice(0, 10)
    const end = new Date()
    end.setDate(end.getDate() + 3)
    form.planEnd = end.toISOString().slice(0, 10)
    form.pointIds = []
    form.measures = []
    if (props.permit) {
      const d = props.permit
      form.id = d.id
      form.reason = d.reason
      form.siteOwnerId = d.siteOwnerId
      form.planStart = d.planStart
      form.planEnd = d.planEnd
      form.pointIds = (d.points || []).map((p) => p.pointId)
      form.measures = (d.measures || []).map((m) => ({
        measureType: m.measureType,
        content: m.content,
        specDesc: m.specDesc
      }))
    }
    await nextTick()
    nextTickSelection()
  }
)

async function nextTickSelection() {
  // 等表格渲染后恢复勾选
  setTimeout(() => {
    selectTableRef.value?.clearSelection?.()
    points.value.forEach((p) => {
      if (form.pointIds.includes(p.id)) selectTableRef.value?.toggleRowSelection?.(p, true)
    })
  }, 60)
}

function payload() {
  return {
    buildingId: fp.buildingId.value,
    reason: form.reason,
    siteOwnerId: form.siteOwnerId,
    planStart: form.planStart,
    planEnd: form.planEnd,
    pointIds: form.pointIds,
    measures: form.measures.filter((m) => m.content && m.content.trim())
  }
}

async function submit(andSubmit) {
  const data = payload()
  if (!data.reason || !data.reason.trim()) return ElMessage.warning('请填写停用原因')
  if (!data.planStart || !data.planEnd) return ElMessage.warning('请选择计划起止时间')
  if (!data.pointIds.length) return ElMessage.warning('请至少选择一个停用点位')
  if (!data.measures.length) return ElMessage.warning('请逐项登记补偿措施')
  let detail
  if (form.id) {
    detail = await permitApi.update(form.id, data)
  } else {
    detail = await permitApi.create(data)
  }
  if (andSubmit) {
    detail = await permitApi.submit(detail.id)
    ElMessage.success('已提交安全负责人批准')
  } else {
    ElMessage.success('草稿已保存')
  }
  emit('update:modelValue', false)
  emit('saved', detail)
}
</script>
