import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * 统一走 nginx 反代到后端 /api：
 *   成功 → 后端包成 { ok:true, message, data }，这里直接把 data 交给页面
 *   失败 → 后端包成 { ok:false, message:"中文业务提示" }，这里统一弹提示并 reject
 */
const http = axios.create({ baseURL: '/api', timeout: 15000 })

/** 顶栏切换的当前操作人身份（演示系统用 X-User-Id 头代替登录态） */
export const USER_STORAGE_KEY = 'fp_current_user_id'

http.interceptors.request.use((config) => {
  const userId = localStorage.getItem(USER_STORAGE_KEY)
  if (userId) {
    config.headers['X-User-Id'] = userId
  }
  return config
})

http.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && typeof body === 'object' && Object.prototype.hasOwnProperty.call(body, 'ok')) {
      if (body.ok) return body.data
      ElMessage.error(body.message || '操作失败')
      return Promise.reject(new Error(body.message || '操作失败'))
    }
    return body
  },
  (err) => {
    const body = err.response && err.response.data
    const message = (body && body.message) || err.message || '网络异常，请稍后再试'
    ElMessage.error(message)
    return Promise.reject(new Error(message))
  }
)

/** 全局盘面 */
export const overviewApi = {
  get: () => http.get('/overview')
}

/** 当前操作人（角色身份） */
export const userApi = {
  list: () => http.get('/permits/users')
}

/** 模块六：设施停用许可与复位闭环 */
export const permitApi = {
  list: (params) => http.get('/permits', { params }),
  duty: () => http.get('/permits/duty'),
  detail: (id) => http.get(`/permits/${id}`),
  create: (data) => http.post('/permits', data),
  update: (id, data) => http.post(`/permits/${id}`, data),
  submit: (id) => http.post(`/permits/${id}/submit`),
  approve: (id) => http.post(`/permits/${id}/approve`),
  reject: (id, data) => http.post(`/permits/${id}/reject`, data),
  revoke: (id, data) => http.post(`/permits/${id}/revoke`, data),
  confirmMeasure: (id, measureId, data) => http.post(`/permits/${id}/measures/${measureId}/confirm`, data || {}),
  waiveOrder: (id, orderId, data) => http.post(`/permits/${id}/orders/${orderId}/waive`, data),
  resetVerify: (id, data) => http.post(`/permits/${id}/reset-verify`, data),
  close: (id, data) => http.post(`/permits/${id}/close`, data)
}

/** 模块一：建筑与楼层 */
export const buildingApi = {
  list: () => http.get('/buildings'),
  floors: (id) => http.get(`/buildings/${id}/floors`),
  create: (data) => http.post('/buildings', data)
}

/** 模块二：消防设施点位 */
export const facilityApi = {
  list: (params) => http.get('/facilities', { params }),
  plan: (buildingId) => http.get('/facilities/plan', { params: { buildingId } }),
  create: (data) => http.post('/facilities', data),
  check: (id) => http.post(`/facilities/${id}/check`),
  report: (id, data) => http.post(`/facilities/${id}/report`, data)
}

/** 模块三：巡检记录 */
export const inspectionApi = {
  list: (buildingId) => http.get('/inspections', { params: { buildingId } }),
  items: (id) => http.get(`/inspections/${id}/items`),
  create: (data) => http.post('/inspections', data),
  saveItem: (id, itemId, data) => http.post(`/inspections/${id}/items/${itemId}`, data),
  complete: (id) => http.post(`/inspections/${id}/complete`)
}

/** 模块四：维保单 */
export const maintenanceApi = {
  list: () => http.get('/maintenance'),
  board: () => http.get('/maintenance/board'),
  create: (data) => http.post('/maintenance', data),
  assign: (id, data) => http.post(`/maintenance/${id}/assign`, data),
  advance: (id, data) => http.post(`/maintenance/${id}/advance`, data)
}

/** 模块五：灭火器与气瓶台账 */
export const extinguisherApi = {
  list: (buildingId) => http.get('/extinguishers', { params: { buildingId } }),
  summary: () => http.get('/extinguishers/summary'),
  create: (data) => http.post('/extinguishers', data),
  refill: (id) => http.post(`/extinguishers/${id}/refill`),
  hydro: (id) => http.post(`/extinguishers/${id}/hydro`),
  scrap: (id) => http.post(`/extinguishers/${id}/scrap`)
}
