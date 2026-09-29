<template>
  <div>
    <div class="fp-panel">
      <div class="fp-panel-head">
        <span class="fp-panel-title">值班许可台</span>
        <span class="fp-panel-tip">
          {{ board.today }} · 系统只突出风险，绝不自动关闭超期许可；关闭必须经复位核验由消防主管执行
        </span>
        <div class="fp-space">
          <el-button size="small" plain @click="load">刷新</el-button>
        </div>
      </div>
      <div class="fp-panel-body">
        <div class="duty-risk-banner" :class="{ 'is-alert': board.riskTotal > 0 }">
          <template v-if="board.riskTotal > 0">
            ⚠️ 当前有 <b>{{ (board.expired || []).length }}</b> 张许可已超期未复位、
            <b>{{ (board.pendingReset || []).length }}</b> 张待复位核验，请值班人员持续盯办
          </template>
          <template v-else>✅ 当前没有超期或待复位的停用许可</template>
        </div>

        <div class="duty-grid">
          <div class="duty-col">
            <div class="duty-col-head is-ok">
              <span>🟢 当前生效</span><span>{{ (board.effective || []).length }} 张</span>
            </div>
            <PermitCard v-for="p in board.effective" :key="p.id" :permit="p" @open="openDetail" />
            <el-empty v-if="!(board.effective || []).length" description="暂无生效许可" :image-size="48" />
          </div>
          <div class="duty-col">
            <div class="duty-col-head is-warn">
              <span>🟡 即将到期（3 天内）</span><span>{{ (board.expiringSoon || []).length }} 张</span>
            </div>
            <PermitCard v-for="p in board.expiringSoon" :key="p.id" :permit="p" @open="openDetail" />
            <el-empty v-if="!(board.expiringSoon || []).length" description="暂无临期许可" :image-size="48" />
          </div>
          <div class="duty-col">
            <div class="duty-col-head is-danger">
              <span>🔴 已经超期未复位</span><span>{{ (board.expired || []).length }} 张</span>
            </div>
            <PermitCard v-for="p in board.expired" :key="p.id" :permit="p" @open="openDetail" />
            <el-empty v-if="!(board.expired || []).length" description="暂无超期许可" :image-size="48" />
          </div>
        </div>

        <div class="duty-col" style="margin-top: 14px">
          <div class="duty-col-head is-warn">
            <span>🟠 待复位核验（措施 / 维保 / 逐点位核验）</span><span>{{ (board.pendingReset || []).length }} 张</span>
          </div>
          <div style="display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px">
            <PermitCard v-for="p in board.pendingReset" :key="p.id" :permit="p" @open="openDetail" />
          </div>
        </div>
      </div>
    </div>

    <PermitDetailDrawer v-model="drawerVisible" :permit-id="activeId" @changed="load" />
  </div>
</template>

<script setup>
import { h, onMounted, ref } from 'vue'
import { permitApi } from '../api'
import PermitDetailDrawer from './PermitDetailDrawer.vue'

const board = ref({ today: '', effective: [], expiringSoon: [], expired: [], pendingReset: [], riskTotal: 0 })
const drawerVisible = ref(false)
const activeId = ref(null)

const PermitCard = {
  props: { permit: Object },
  emits: ['open'],
  setup(props, { emit }) {
    return () =>
      h('div', {
        class: 'fp-card duty-card' + (props.permit.expired ? ' is-expired' : ''),
        onClick: () => emit('open', props.permit)
      }, [
        h('div', { style: 'display:flex;gap:6px;align-items:center' }, [
          h('span', { class: 'fp-card-no' }, props.permit.permitNo),
          h('span', { class: 'fp-badge ' + (props.permit.expired ? 'danger' : props.permit.expiringSoon ? 'warn' : 'ok') },
            props.permit.expired ? '已超期' : props.permit.expiringSoon ? '即将到期' : '生效中')
        ]),
        h('div', { class: 'fp-card-row' }, props.permit.buildingName + ' · ' + props.permit.reason),
        h('div', { class: 'fp-card-row' }, '计划 ' + props.permit.planStart + ' ~ ' + props.permit.planEnd),
        h('div', { class: 'fp-card-row' },
          '现场负责人 ' + props.permit.siteOwnerName
          + ' · ' + props.permit.pointCount + ' 点位 · 措施 '
          + props.permit.measureConfirmedCount + '/' + props.permit.measureCount
          + (props.permit.unfinishedOrderCount > 0 ? ' · 未结维保 ' + props.permit.unfinishedOrderCount : '')),
        props.permit.allResetPassed
          ? h('div', { class: 'fp-card-row' }, [h('span', { class: 'fp-badge ok' }, '复位核验全部合格·待主管关闭')])
          : null
      ])
  }
}

async function load() {
  board.value = await permitApi.duty()
}

function openDetail(row) {
  activeId.value = row.id
  drawerVisible.value = true
}

onMounted(load)
</script>

<style scoped>
.duty-risk-banner {
  padding: 10px 14px;
  border-radius: 10px;
  background: #eef7ee;
  color: #2e7d32;
  border: 1px solid #c8e6c9;
  margin-bottom: 14px;
}
.duty-risk-banner.is-alert {
  background: #fdeaea;
  color: #c62828;
  border-color: #f5c6c6;
  font-weight: 600;
}
.duty-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}
.duty-col-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 700;
  padding: 6px 8px 10px;
  border-bottom: 2px solid var(--fp-line);
  margin-bottom: 8px;
}
.duty-col-head.is-ok { color: #2e7d32; }
.duty-col-head.is-warn { color: #b26a00; }
.duty-col-head.is-danger { color: #c62828; }
.duty-card {
  cursor: pointer;
}
.duty-card.is-expired {
  border-left-color: var(--fp-danger);
  background: #fff8f7;
}
</style>
