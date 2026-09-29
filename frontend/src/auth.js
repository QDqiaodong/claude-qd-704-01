import { ref } from 'vue'
import { userApi, USER_STORAGE_KEY } from './api'

/**
 * 全局当前操作人（三类角色）。演示系统没有登录页：
 * 顶栏切换身份 → 写入 localStorage → axios 每个请求带 X-User-Id。
 */
export const users = ref([])
export const currentUser = ref(null)

export async function loadUsers() {
  users.value = await userApi.list()
  const savedId = Number(localStorage.getItem(USER_STORAGE_KEY))
  const preferred = users.value.find((u) => u.id === savedId)
    || users.value.find((u) => u.role === 'FIRE_MANAGER')
  if (preferred) {
    switchUser(preferred.id)
  }
}

export function switchUser(id) {
  const user = users.value.find((u) => u.id === Number(id))
  currentUser.value = user || null
  if (user) {
    localStorage.setItem(USER_STORAGE_KEY, String(user.id))
  } else {
    localStorage.removeItem(USER_STORAGE_KEY)
  }
}

/** 角色快捷判断，用于页面按钮的显示 / 禁用（真正的权限以后端为准） */
export function isRole(role) {
  return currentUser.value && currentUser.value.role === role
}
