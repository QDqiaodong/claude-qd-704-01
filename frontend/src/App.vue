<template>
  <div class="fp-shell">
    <!-- 顶栏：品牌 + 全局建筑上下文 + 盘面计数器 -->
    <header class="fp-topbar">
      <div class="fp-brand">
        <span class="fp-brand-mark">🔥</span>
        <div>
          <div class="fp-brand-title">消防设施维保管理系统</div>
          <div class="fp-brand-sub">{{ todayText }} · 在管建筑 {{ buildings.length }} 栋 · 最近巡检 {{ overview.lastInspectDate || '—' }}</div>
        </div>
      </div>

      <div class="fp-top-picker">
        <span style="font-size: 13px; opacity: 0.9">当前建筑</span>
        <el-select v-model="buildingId" filterable placeholder="选择被维保的建筑">
          <el-option
            v-for="b in buildings"
            :key="b.id"
            :label="b.code + ' · ' + b.name"
            :value="b.id"
          >
            <span>{{ b.code }} · {{ b.name }}</span>
            <span style="float: right; margin-left: 12px; font-size: 12px; opacity: 0.7">
              {{ b.buildingTypeName }}
            </span>
          </el-option>
        </el-select>
      </div>

      <div class="fp-top-picker">
        <span style="font-size: 13px; opacity: 0.9">当前身份</span>
        <el-select v-model="currentUserId" style="width: 200px" @change="onSwitchUser">
          <el-option
            v-for="u in users"
            :key="u.id"
            :label="u.displayName + '（' + u.roleName + '）'"
            :value="u.id"
          >
            <span>{{ u.displayName }}</span>
            <span style="float: right; margin-left: 10px; font-size: 12px; opacity: 0.75">{{ u.roleName }}</span>
          </el-option>
        </el-select>
      </div>

      <div class="fp-top-stats">
        <div class="fp-stat"><b>{{ overview.pointTotal || 0 }}</b><span>设施点位</span></div>
        <div class="fp-stat" :class="{ 'is-alert': (overview.overdueCount || 0) > 0 }">
          <b>{{ overview.overdueCount || 0 }}</b><span>超期末检</span>
        </div>
        <div class="fp-stat" :class="{ 'is-alert': (overview.pointFault || 0) > 0 }">
          <b>{{ overview.pointFault || 0 }}</b><span>故障点位</span>
        </div>
        <div class="fp-stat" :class="{ 'is-warn': (overview.pointReplace || 0) > 0 }">
          <b>{{ overview.pointReplace || 0 }}</b><span>待更换</span>
        </div>
        <div class="fp-stat" :class="{ 'is-warn': (overview.permitEffective || 0) > 0 }">
          <b>{{ overview.permitEffective || 0 }}</b><span>生效许可</span>
        </div>
        <div class="fp-stat" :class="{ 'is-alert': (overview.permitExpired || 0) > 0 }">
          <b>{{ overview.permitExpired || 0 }}</b><span>超期未复位</span>
        </div>
        <div class="fp-stat" :class="{ 'is-warn': (overview.permitPendingReset || 0) > 0 }">
          <b>{{ overview.permitPendingReset || 0 }}</b><span>待复位</span>
        </div>
        <div class="fp-stat"><b>{{ overview.orderWorking || 0 }}</b><span>在办维保</span></div>
      </div>
    </header>

    <!-- 横向模块条 -->
    <nav class="fp-navbar">
      <button
        v-for="item in navItems"
        :key="item.key"
        class="fp-nav-btn"
        :class="{ 'is-on': activeKey === item.key }"
        type="button"
        :title="item.hint"
        @click="go(item.path)"
      >
        <span class="fp-nav-ico">{{ item.icon }}</span>{{ item.label }}
      </button>
    </nav>

    <main class="fp-stage">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { computed, getCurrentInstance, onMounted, provide, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import router, { navItems } from './router'
import { buildingApi, overviewApi } from './api'
import { users, currentUser, loadUsers, switchUser } from './auth'

/**
 * 模板自带的 main.js 只装了 Element Plus（那份文件不动它），
 * 所以在这里把 router 装到根组件所属的 app 实例上。
 */
getCurrentInstance().appContext.app.use(router)

const route = useRoute()
const nav = useRouter()

const buildings = ref([])
const buildingId = ref(null)
const overview = ref({})

const currentUserId = computed({
  get: () => currentUser.value && currentUser.value.id,
  set: () => {}
})

function onSwitchUser(id) {
  switchUser(id)
}

const todayText = computed(() => {
  const now = new Date()
  const week = ['日', '一', '二', '三', '四', '五', '六'][now.getDay()]
  return `${now.getFullYear()} 年 ${now.getMonth() + 1} 月 ${now.getDate()} 日 星期${week}`
})

const activeKey = computed(() => (route.meta.navKey ? route.meta.navKey : (route.path || '').replace('/', '') || 'plan'))

/** 全局上下文：五个页面共用同一栋被维保的建筑 */
provide('fp', { buildingId, buildings, overview, reloadOverview: loadOverview })

async function loadBuildings() {
  buildings.value = await buildingApi.list()
  if (!buildingId.value && buildings.value.length > 0) {
    const preferred = buildings.value.find((b) => b.code === 'B02')
    buildingId.value = (preferred || buildings.value[0]).id
  }
}

async function loadOverview() {
  overview.value = await overviewApi.get()
}

function go(path) {
  nav.push(path)
}

watch(buildingId, () => loadOverview())

onMounted(async () => {
  await loadUsers()
  await loadBuildings()
  await loadOverview()
})
</script>
