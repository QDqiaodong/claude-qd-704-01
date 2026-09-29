import { createRouter, createWebHistory } from 'vue-router'

/**
 * 导航形态：顶部左侧一条「楼层/设施」纵向工具区 + 顶部横向模块条。
 * 被维保的建筑由 App.vue 顶栏的下拉统一切换，各模块共享同一栋楼。
 */
export const navItems = [
  { key: 'plan', path: '/plan', icon: '🚒', label: '楼层点位平面图', hint: 'SVG 手绘平面 + 点位气泡（含许可停用标记 / 设施台账）' },
  { key: 'permits', path: '/permits', icon: '📝', label: '停用许可', hint: '设施停用许可：选点 / 补偿措施 / 批准 / 复位核验 / 关闭闭环' },
  { key: 'duty', path: '/duty', icon: '🚨', label: '值班许可台', hint: '当前生效 / 即将到期 / 已超期未复位 / 待复位核验' },
  { key: 'buildings', path: '/buildings', icon: '🏢', label: '建筑与楼层', hint: '建筑类型·层数·面积·消防负责人' },
  { key: 'inspections', path: '/inspections', icon: '🧯', label: '巡检记录', hint: '日巡/月检/季检/年检 + 异常明细行' },
  { key: 'maintenance', path: '/maintenance', icon: '🔧', label: '维保看板', hint: '待派工 → 处理中 → 已完成 三列卡片' },
  { key: 'extinguishers', path: '/extinguishers', icon: '⏳', label: '灭火器气瓶台账', hint: '水压/报废倒计时 + 报废操作' },
  { key: 'drills', path: '/drills', icon: '🚩', label: '消防演练打点', hint: '演练场次 / 指令链编排 / 执行打点 / 暂停恢复 / 超时迟到 / 事件时间线' }
]

const routes = [
  { path: '/', redirect: '/plan' },
  { path: '/plan', name: 'plan', component: () => import('../views/PlanView.vue') },
  { path: '/permits', name: 'permits', component: () => import('../views/PermitListView.vue') },
  { path: '/duty', name: 'duty', component: () => import('../views/DutyView.vue') },
  { path: '/buildings', name: 'buildings', component: () => import('../views/BuildingView.vue') },
  { path: '/inspections', name: 'inspections', component: () => import('../views/InspectionView.vue') },
  { path: '/maintenance', name: 'maintenance', component: () => import('../views/MaintenanceView.vue') },
  { path: '/extinguishers', name: 'extinguishers', component: () => import('../views/ExtinguisherView.vue') },
  { path: '/drills', name: 'drills', component: () => import('../views/drill/DrillListView.vue') },
  { path: '/drills/:id/edit', name: 'drill-edit', component: () => import('../views/drill/DrillEditView.vue') },
  { path: '/drills/:id/run', name: 'drill-run', component: () => import('../views/drill/DrillRunView.vue') },
  { path: '/:pathMatch(.*)*', redirect: '/plan' }
]

export default createRouter({
  history: createWebHistory(),
  routes
})
