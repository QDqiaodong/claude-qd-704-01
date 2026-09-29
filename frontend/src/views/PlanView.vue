<template>
  <div class="fp-panel">
    <div class="fp-panel-head">
      <span class="fp-panel-title">楼层点位平面图</span>
      <span class="fp-panel-tip">
        {{ plan.buildingName || '—' }} · {{ plan.buildingTypeName || '' }} · 图上每个气泡是一个消防设施点位，点击查看明细
      </span>
      <div class="fp-space">
        <el-radio-group v-model="viewMode" size="small">
          <el-radio-button label="plan">平面图</el-radio-button>
          <el-radio-button label="ledger">设施台账</el-radio-button>
        </el-radio-group>
        <el-button size="small" @click="openCreate">+ 新增点位</el-button>
        <el-button size="small" type="primary" plain @click="load">刷新</el-button>
      </div>
    </div>

    <div class="fp-panel-body">
      <!-- ============ 平面图模式 ============ -->
      <template v-if="viewMode === 'plan'">
      <!-- 楼层切换标签条 -->
      <div class="fp-floor-tabs">
        <button
          v-for="f in plan.floors || []"
          :key="f.id"
          class="fp-floor-tab"
          :class="{ 'is-on': f.id === activeFloor }"
          type="button"
          @click="activeFloor = f.id"
        >
          <div class="fp-floor-code">{{ f.floorCode }}</div>
          <div class="fp-floor-meta">{{ f.floorName }} · {{ f.pointCount }} 个点位</div>
          <span v-if="f.abnormalCount > 0" class="fp-floor-dot" :title="f.abnormalCount + ' 个异常点位'"></span>
        </button>
      </div>

      <!-- 设施类型多选 chips -->
      <div class="fp-chips">
        <el-checkbox-group v-model="checkedTypes" class="fp-chip">
          <el-checkbox v-for="t in facilityTypes" :key="t.code" :label="t.code">
            {{ t.label }}（{{ countOf(t.code) }}）
          </el-checkbox>
        </el-checkbox-group>
      </div>

      <el-row :gutter="12" style="margin-top: 12px">
        <el-col :span="17">
          <div class="fp-plan-wrap">
            <!-- 纯 SVG 手绘一层楼的建筑平面：外墙粗线 + 房间细线 + 走廊 + 楼梯间 + 安全出口 -->
            <svg class="fp-plan-svg" viewBox="0 0 1000 660" preserveAspectRatio="xMidYMid meet">
              <rect class="wall-outer" x="40" y="40" width="920" height="580" rx="6" />
              <rect class="corridor" x="41" y="300" width="918" height="80" />

              <!-- 房间分隔细线 -->
              <g class="wall-inner">
                <line x1="280" y1="40" x2="280" y2="300" />
                <line x1="520" y1="40" x2="520" y2="300" />
                <line x1="760" y1="40" x2="760" y2="300" />
                <line x1="280" y1="380" x2="280" y2="620" />
                <line x1="520" y1="380" x2="520" y2="620" />
                <line x1="760" y1="380" x2="760" y2="620" />
                <line x1="40" y1="300" x2="960" y2="300" />
                <line x1="40" y1="380" x2="960" y2="380" />
              </g>

              <!-- 房间名 -->
              <text class="room-label" x="120" y="285">01 商铺区</text>
              <text class="room-label" x="360" y="285">02 后勤区</text>
              <text class="room-label" x="600" y="285">03 餐饮区</text>
              <text class="room-label" x="820" y="285">04 设备区</text>
              <text class="room-label" x="120" y="440">05 仓储区</text>
              <text class="room-label" x="360" y="440">06 办公区</text>
              <text class="room-label" x="600" y="440">07 备用区</text>

              <!-- 楼梯间 -->
              <g class="stair">
                <rect x="790" y="470" width="150" height="130" stroke-dasharray="6 4" />
                <line x1="800" y1="500" x2="930" y2="500" />
                <line x1="800" y1="525" x2="930" y2="525" />
                <line x1="800" y1="550" x2="930" y2="550" />
                <line x1="800" y1="575" x2="930" y2="575" />
                <text class="room-label" x="838" y="462">楼梯间</text>
              </g>

              <!-- 安全出口 -->
              <g>
                <rect class="exit-box" x="46" y="326" width="46" height="28" rx="4" />
                <text class="exit-text" x="54" y="346">EXIT</text>
                <rect class="exit-box" x="46" y="556" width="46" height="28" rx="4" />
                <text class="exit-text" x="54" y="576">EXIT</text>
              </g>

              <!-- 点位气泡：不同设施类型用不同形状，按状态着色 -->
              <g
                v-for="p in visiblePoints"
                :key="p.id"
                class="fp-point"
                @click="openPoint(p, $event)"
              >
                <title>{{ p.pointCode }} · {{ p.facilityTypeName }} · {{ p.statusName }}</title>
                <circle
                  v-if="p.facilityType === 'EXTINGUISHER'"
                  class="pt-shape"
                  :cx="px(p)"
                  :cy="py(p)"
                  r="15"
                  :fill="color(p)"
                />
                <rect
                  v-else-if="p.facilityType === 'HYDRANT'"
                  class="pt-shape"
                  :x="px(p) - 13"
                  :y="py(p) - 13"
                  width="26"
                  height="26"
                  rx="5"
                  :fill="color(p)"
                />
                <ellipse
                  v-else-if="p.facilityType === 'LIGHT'"
                  class="pt-shape"
                  :cx="px(p)"
                  :cy="py(p)"
                  rx="16"
                  ry="10"
                  :fill="color(p)"
                />
                <polygon
                  v-else
                  class="pt-shape"
                  :points="poly(p.facilityType, px(p), py(p))"
                  :fill="color(p)"
                />
                <!-- 许可停用叠加标记：红色虚线圈 + 「停」角标，不改变气泡原色（原状态保留） -->
                <circle
                  v-if="p.permitDisabled"
                  class="pt-permit-ring"
                  :cx="px(p)"
                  :cy="py(p)"
                  r="20"
                />
                <g v-if="p.permitDisabled">
                  <circle class="pt-permit-badge-bg" :cx="px(p) + 17" :cy="py(p) - 17" r="8" />
                  <text class="pt-permit-badge-text" :x="px(p) + 17" :y="py(p) - 13.5">停</text>
                </g>
                <text class="pt-label" :x="px(p)" :y="py(p) + 4">{{ tail(p.pointCode) }}</text>
              </g>
            </svg>

            <div class="fp-legend">
              <span class="fp-legend-item" v-for="t in facilityTypes" :key="t.code">
                <svg width="20" height="20" viewBox="0 0 40 40">
                  <circle v-if="t.code === 'EXTINGUISHER'" cx="20" cy="20" r="14" :fill="brand" />
                  <rect
                    v-else-if="t.code === 'HYDRANT'"
                    x="7"
                    y="7"
                    width="26"
                    height="26"
                    rx="5"
                    :fill="brand"
                  />
                  <ellipse v-else-if="t.code === 'LIGHT'" cx="20" cy="20" rx="16" ry="10" :fill="brand" />
                  <polygon v-else :points="poly(t.code, 20, 20)" :fill="brand" />
                </svg>
                <span>{{ t.label }}</span>
              </span>
              <span class="fp-legend-item" v-for="s in statusList" :key="s.code">
                <span class="fp-legend-swatch" :style="{ background: s.color }"></span>
                <span>{{ s.label }}</span>
              </span>
              <span class="fp-legend-item">
                <span class="fp-legend-ring">停</span>
                <span>许可停用（叠加标记，原状态保留，{{ plan.summary ? plan.summary.permitDisabled : 0 }} 个）</span>
              </span>
            </div>
          </div>
        </el-col>

        <el-col :span="7">
          <div class="fp-panel" style="box-shadow: none">
            <div class="fp-panel-head">
              <span class="fp-panel-title">本层状态</span>
            </div>
            <div class="fp-panel-body">
              <el-descriptions :column="1" size="small" border>
                <el-descriptions-item label="楼层">
                  {{ currentFloor ? currentFloor.floorCode + ' · ' + currentFloor.floorName : '—' }}
                </el-descriptions-item>
                <el-descriptions-item label="点位总数">{{ floorPoints.length }}</el-descriptions-item>
                <el-descriptions-item label="显示中">{{ visiblePoints.length }}</el-descriptions-item>
                <el-descriptions-item label="超期末检">{{ floorPoints.filter((p) => p.overdue).length }}</el-descriptions-item>
                <el-descriptions-item label="建筑面积">{{ currentFloor ? currentFloor.areaSqm : '—' }} ㎡</el-descriptions-item>
                <el-descriptions-item label="安全出口">{{ currentFloor ? currentFloor.exitCount : '—' }} 处</el-descriptions-item>
              </el-descriptions>

              <template v-if="activePoint.id">
                <div style="margin: 12px 0 6px; font-weight: 700; color: #a3310f">选中点位</div>
                <el-descriptions :column="1" size="small" border>
                  <el-descriptions-item label="点位编号">{{ activePoint.pointCode }}</el-descriptions-item>
                  <el-descriptions-item label="设施">{{ activePoint.facilityTypeName }}</el-descriptions-item>
                  <el-descriptions-item label="规格">{{ activePoint.spec || '—' }}</el-descriptions-item>
                  <el-descriptions-item label="位置">{{ activePoint.locationDesc || '—' }}</el-descriptions-item>
                  <el-descriptions-item label="上次检查">{{ activePoint.lastCheckDate || '—' }}</el-descriptions-item>
                  <el-descriptions-item label="下次检查">
                    {{ activePoint.nextCheckDate || '—' }}
                    <el-tag size="small" :type="activePoint.overdue ? 'danger' : 'info'">
                      {{ dueText(activePoint) }}
                    </el-tag>
                  </el-descriptions-item>
                  <el-descriptions-item label="周期">{{ activePoint.checkCycleDays }} 天</el-descriptions-item>
                  <el-descriptions-item label="状态">
                    <el-tag size="small" :type="statusTag(activePoint.status)">{{ activePoint.statusName }}</el-tag>
                  </el-descriptions-item>
                  <el-descriptions-item v-if="activePoint.permitDisabled" label="许可停用">
                    <el-tag size="small" type="danger" effect="dark">
                      许可停用 {{ activePoint.activeDisablePermitNo }}
                    </el-tag>
                    <div style="font-size: 11px; color: #a3310f">叠加标记，原状态保留；关闭许可后自动解除</div>
                  </el-descriptions-item>
                </el-descriptions>
                <div style="margin-top: 10px; display: flex; gap: 8px">
                  <el-button size="small" type="success" :disabled="activePoint.permitDisabled" @click="markChecked(activePoint)">标记已检</el-button>
                  <el-button size="small" type="warning" @click="openReport(activePoint)">报修</el-button>
                </div>
              </template>
              <el-empty v-else description="在平面图上点一个点位" :image-size="60" />
            </div>
          </div>
        </el-col>
      </el-row>
      </template>

      <!-- ============ 设施台账模式 ============ -->
      <template v-else>
        <div class="fp-chips" style="margin-bottom: 10px">
          <el-checkbox-group v-model="ledgerFilters.statuses" class="fp-chip">
            <el-checkbox label="NORMAL">正常</el-checkbox>
            <el-checkbox label="REPLACE">待更换</el-checkbox>
            <el-checkbox label="FAULT">故障</el-checkbox>
            <el-checkbox label="DISABLED">停用</el-checkbox>
          </el-checkbox-group>
          <el-checkbox v-model="ledgerFilters.onlyPermit" style="margin-left: 16px">只看许可停用</el-checkbox>
          <el-checkbox v-model="ledgerFilters.onlyOverdue">只看超期末检</el-checkbox>
        </div>
        <el-table :data="ledgerRows" border stripe size="small" :row-class-name="ledgerRowClass">
          <el-table-column prop="pointCode" label="点位编号" width="130" />
          <el-table-column prop="floorCode" label="楼层" width="70" />
          <el-table-column prop="facilityTypeName" label="设施类型" width="110" />
          <el-table-column prop="spec" label="规格" width="120" />
          <el-table-column prop="locationDesc" label="位置" min-width="170" />
          <el-table-column label="原状态（保留）" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="statusTag(row.status)">{{ row.statusName }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="许可停用标记" width="210">
            <template #default="{ row }">
              <el-tag v-if="row.permitDisabled" size="small" type="danger" effect="dark">
                许可停用 {{ row.activeDisablePermitNo }}
              </el-tag>
              <span v-else style="color: #9e9e9e; font-size: 12px">—</span>
            </template>
          </el-table-column>
          <el-table-column prop="lastCheckDate" label="上次检查" width="110" />
          <el-table-column label="下次检查" width="150">
            <template #default="{ row }">
              {{ row.nextCheckDate || '—' }}
              <el-tag size="small" :type="row.overdue ? 'danger' : 'info'">{{ dueText(row) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="130">
            <template #default="{ row }">
              <el-button size="small" link type="success" :disabled="row.permitDisabled" @click="markChecked(row)">标记已检</el-button>
              <el-button size="small" link type="warning" @click="openReport(row)">报修</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </div>

    <!-- 点位气泡上的 el-popover -->
    <el-popover
      :visible="popVisible"
      :virtual-ref="popTarget"
      virtual-triggering
      trigger="manual"
      placement="top"
      :width="300"
    >
      <template #default>
        <div v-if="activePoint.id">
          <div style="font-weight: 700; color: #a3310f; margin-bottom: 6px">
            {{ activePoint.pointCode }} · {{ activePoint.facilityTypeName }}
          </div>
          <div style="font-size: 12px; color: #7a6b66; line-height: 20px">
            <div>规格：{{ activePoint.spec || '—' }}</div>
            <div>位置：{{ activePoint.locationDesc || '—' }}</div>
            <div>安装日期：{{ activePoint.installDate || '—' }}</div>
            <div>上次检查：{{ activePoint.lastCheckDate || '—' }}</div>
            <div>下次检查：{{ activePoint.nextCheckDate || '—' }}（{{ dueText(activePoint) }}）</div>
            <div>
              状态：
              <el-tag size="small" :type="statusTag(activePoint.status)">{{ activePoint.statusName }}</el-tag>
              <el-tag v-if="activePoint.permitDisabled" size="small" type="danger" effect="dark" style="margin-left: 4px">
                许可停用 {{ activePoint.activeDisablePermitNo }}
              </el-tag>
            </div>
            <div v-if="activePoint.permitDisabled" style="font-size: 11px; color: #a3310f">
              许可停用为叠加标记，原状态保留；维保完成也不会自动恢复，关闭许可后才解除
            </div>
          </div>
          <div style="margin-top: 10px; display: flex; gap: 8px">
            <el-button size="small" type="success" :disabled="activePoint.permitDisabled" @click="markChecked(activePoint)">标记已检</el-button>
            <el-button size="small" type="warning" @click="openReport(activePoint)">报修</el-button>
            <el-button size="small" @click="popVisible = false">关闭</el-button>
          </div>
        </div>
      </template>
    </el-popover>

    <!-- 报修 -->
    <el-dialog v-model="reportVisible" title="点位报修" width="460px">
      <el-form :model="reportForm" label-width="90px">
        <el-form-item label="维保类型">
          <el-select v-model="reportForm.orderType" style="width: 100%">
            <el-option label="维修" value="REPAIR" />
            <el-option label="更换" value="REPLACE" />
            <el-option label="充装" value="REFILL" />
            <el-option label="检测" value="TEST" />
          </el-select>
        </el-form-item>
        <el-form-item label="故障描述">
          <el-input v-model="reportForm.faultDesc" type="textarea" :rows="3" placeholder="描述现场看到的故障现象" />
        </el-form-item>
        <el-form-item label="服务商">
          <el-input v-model="reportForm.vendor" placeholder="选填" />
        </el-form-item>
        <el-form-item label="责任人">
          <el-input v-model="reportForm.ownerPerson" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reportVisible = false">取消</el-button>
        <el-button type="primary" @click="submitReport">提交报修</el-button>
      </template>
    </el-dialog>

    <!-- 新增点位：编码与到期日都由后端生命周期回调生成 -->
    <el-dialog v-model="createVisible" title="新增消防设施点位" width="560px">
      <el-form :model="createForm" label-width="96px">
        <div class="fp-form-grid">
          <el-form-item label="所属楼层">
            <el-select v-model="createForm.floorId" style="width: 100%">
              <el-option v-for="f in plan.floors || []" :key="f.id" :label="f.floorCode + ' ' + f.floorName" :value="f.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="设施类型">
            <el-select v-model="createForm.facilityType" style="width: 100%">
              <el-option v-for="t in facilityTypes" :key="t.code" :label="t.label" :value="t.code" />
            </el-select>
          </el-form-item>
          <el-form-item label="规格型号">
            <el-input v-model="createForm.spec" placeholder="如 MFZ/ABC4" />
          </el-form-item>
          <el-form-item label="安装日期">
            <el-date-picker v-model="createForm.installDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
          <el-form-item label="图纸 X">
            <el-input-number v-model="createForm.planX" :min="60" :max="940" :step="10" />
          </el-form-item>
          <el-form-item label="图纸 Y">
            <el-input-number v-model="createForm.planY" :min="60" :max="600" :step="10" />
          </el-form-item>
        </div>
        <el-form-item label="位置描述">
          <el-input v-model="createForm.locationDesc" placeholder="如 三层东侧安全出口旁" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">保存（自动生成编号）</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, reactive, ref, watch } from 'vue'
import { facilityApi } from '../api'

const fp = inject('fp')

const brand = '#d84315'
const plan = ref({})
const activeFloor = ref(null)
const activePoint = ref({})
const popVisible = ref(false)
const popTarget = ref(null)
const reportVisible = ref(false)
const createVisible = ref(false)
const viewMode = ref('plan')
const ledgerFilters = ref({
  statuses: ['NORMAL', 'REPLACE', 'FAULT', 'DISABLED'],
  onlyPermit: false,
  onlyOverdue: false
})

const facilityTypes = [
  { code: 'EXTINGUISHER', label: '灭火器' },
  { code: 'HYDRANT', label: '消火栓' },
  { code: 'SPRINKLER', label: '喷淋头' },
  { code: 'SMOKE', label: '烟感探测器' },
  { code: 'ALARM', label: '手动报警按钮' },
  { code: 'LIGHT', label: '应急照明' },
  { code: 'DOOR', label: '防火门' },
  { code: 'PUMP', label: '消防泵' }
]

const statusList = [
  { code: 'NORMAL', label: '正常', color: brand },
  { code: 'REPLACE', label: '待更换', color: '#f9a825' },
  { code: 'FAULT', label: '故障', color: '#c62828' },
  { code: 'DISABLED', label: '停用', color: '#90a4ae' }
]

const checkedTypes = ref(facilityTypes.map((t) => t.code))

const reportForm = reactive({ pointId: null, orderType: 'REPAIR', faultDesc: '', vendor: '', ownerPerson: '' })
const createForm = reactive({
  floorId: null,
  facilityType: 'HYDRANT',
  spec: '',
  locationDesc: '',
  installDate: null,
  planX: 500,
  planY: 340
})

const allPoints = computed(() => plan.value.points || [])
const currentFloor = computed(() => (plan.value.floors || []).find((f) => f.id === activeFloor.value) || null)
const floorPoints = computed(() => allPoints.value.filter((p) => p.floorId === activeFloor.value))
const visiblePoints = computed(() => floorPoints.value.filter((p) => checkedTypes.value.includes(p.facilityType)))

const ledgerRows = computed(() =>
  allPoints.value.filter((p) => {
    if (!ledgerFilters.value.statuses.includes(p.status)) return false
    if (ledgerFilters.value.onlyPermit && !p.permitDisabled) return false
    if (ledgerFilters.value.onlyOverdue && !p.overdue) return false
    return true
  })
)

function ledgerRowClass({ row }) {
  return row.permitDisabled ? 'fp-row-danger' : ''
}

function countOf(code) {
  return floorPoints.value.filter((p) => p.facilityType === code).length
}

function px(p) {
  return p.planX == null ? 500 : p.planX
}

function py(p) {
  return p.planY == null ? 340 : p.planY
}

function tail(code) {
  return code ? code.slice(-3) : ''
}

function color(p) {
  const hit = statusList.find((s) => s.code === p.status)
  return hit ? hit.color : brand
}

function statusTag(status) {
  if (status === 'FAULT') return 'danger'
  if (status === 'REPLACE') return 'warning'
  if (status === 'DISABLED') return 'info'
  return 'success'
}

function dueText(p) {
  if (!p.nextCheckDate) return '—'
  if (p.overdue) return '已超期 ' + Math.abs(p.daysToNext) + ' 天'
  return '还有 ' + p.daysToNext + ' 天'
}

/** 不同设施类型 → 不同形状的多边形顶点 */
function poly(type, x, y) {
  const points = []
  if (type === 'SMOKE') {
    return `${x},${y - 16} ${x + 15},${y + 12} ${x - 15},${y + 12}`
  }
  if (type === 'SPRINKLER') {
    return `${x},${y - 16} ${x + 16},${y} ${x},${y + 16} ${x - 16},${y}`
  }
  if (type === 'ALARM') {
    for (let i = 0; i < 10; i++) {
      const r = i % 2 === 0 ? 17 : 7
      const a = -Math.PI / 2 + (i * Math.PI) / 5
      points.push(`${(x + r * Math.cos(a)).toFixed(1)},${(y + r * Math.sin(a)).toFixed(1)}`)
    }
    return points.join(' ')
  }
  // DOOR 六边形 / PUMP 八边形
  const sides = type === 'PUMP' ? 8 : 6
  const r = type === 'PUMP' ? 16 : 17
  for (let i = 0; i < sides; i++) {
    const a = -Math.PI / 2 + (i * 2 * Math.PI) / sides
    points.push(`${(x + r * Math.cos(a)).toFixed(1)},${(y + r * Math.sin(a)).toFixed(1)}`)
  }
  return points.join(' ')
}

function openPoint(point, event) {
  activePoint.value = point
  popTarget.value = event.currentTarget
  popVisible.value = true
}

async function load() {
  if (!fp.buildingId.value) {
    return
  }
  plan.value = await facilityApi.plan(fp.buildingId.value)
  const floors = plan.value.floors || []
  if (floors.length === 0) {
    activeFloor.value = null
    return
  }
  if (!floors.some((f) => f.id === activeFloor.value)) {
    const preferred = floors.find((f) => f.floorCode === '1F') || floors[0]
    activeFloor.value = preferred.id
  }
}

async function markChecked(point) {
  await facilityApi.check(point.id)
  popVisible.value = false
  const id = point.id
  await load()
  const hit = allPoints.value.find((p) => p.id === id)
  if (hit) {
    activePoint.value = hit
  }
}

function openReport(point) {
  activePoint.value = point
  reportForm.pointId = point.id
  reportForm.orderType = 'REPAIR'
  reportForm.faultDesc = ''
  reportForm.vendor = ''
  reportForm.ownerPerson = ''
  reportVisible.value = true
  popVisible.value = false
}

async function submitReport() {
  await facilityApi.report(reportForm.pointId, {
    orderType: reportForm.orderType,
    faultDesc: reportForm.faultDesc,
    vendor: reportForm.vendor || null,
    ownerPerson: reportForm.ownerPerson || null
  })
  reportVisible.value = false
  await load()
  await fp.reloadOverview()
}

function openCreate() {
  createForm.floorId = activeFloor.value
  createForm.facilityType = 'HYDRANT'
  createForm.spec = ''
  createForm.locationDesc = ''
  createForm.installDate = null
  createForm.planX = 500
  createForm.planY = 340
  createVisible.value = true
}

async function submitCreate() {
  await facilityApi.create({
    buildingId: fp.buildingId.value,
    floorId: createForm.floorId,
    facilityType: createForm.facilityType,
    spec: createForm.spec,
    locationDesc: createForm.locationDesc,
    installDate: createForm.installDate || null,
    planX: createForm.planX,
    planY: createForm.planY
  })
  createVisible.value = false
  await load()
  await fp.reloadOverview()
}

watch(() => fp.buildingId.value, load)
onMounted(load)
</script>
