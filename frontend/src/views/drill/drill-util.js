/** 演练模块共享：状态标签配色、时间格式化、剩余秒展示 */

export const SESSION_STATUS_TAG = {
  DRAFT: 'info',
  READY: 'warning',
  RUNNING: 'success',
  PAUSED: 'warning',
  FINISHED: 'success',
  CANCELED: 'info'
}

export const CARD_STATUS_TAG = {
  PENDING: 'info',
  WAITING: 'warning',
  CONFIRMED: 'success',
  TIMEOUT: 'danger',
  SKIPPED: 'danger'
}

/** 时间线圆点语义色 */
export function eventDotClass(action) {
  if (action === 'CONFIRM' || action === 'FINISH' || action === 'READY' || action === 'START') return 'is-ok'
  if (action === 'TIMEOUT' || action === 'LATE_CONFIRM' || action === 'CANCEL') return 'is-danger'
  if (action === 'PAUSE' || action === 'SKIP' || action === 'DISPOSE') return 'is-warn'
  return 'is-info'
}

export function fmtTime(t) {
  if (!t) return '—'
  return String(t).replace('T', ' ').slice(0, 19)
}

export function fmtClock(t) {
  if (!t) return '—'
  return String(t).replace('T', ' ').slice(11, 19)
}

/** 秒 → mm:ss（超过 1 小时带小时） */
export function fmtSeconds(sec) {
  if (sec === null || sec === undefined) return '—'
  const s = Math.max(0, Math.floor(sec))
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const r = s % 60
  const mm = String(m).padStart(2, '0')
  const ss = String(r).padStart(2, '0')
  return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`
}

/** 浏览器本地与服务端时间差（毫秒）：服务端时间 = 本地时间 + offset */
export function serverClockOffset(serverTimeIso) {
  const serverMs = new Date(serverTimeIso).getTime()
  return serverMs - Date.now()
}

export function nowServerMs(offset) {
  return Date.now() + offset
}

/**
 * 按服务端口径实时计算剩余秒数（前端只做展示）：
 * RUNNING + deadline：(deadline - 本地经校时的当前时间)，下限 0
 * PAUSED：冻结值
 */
export function liveRemaining(card, sessionStatus, offsetMs) {
  if (card.status !== 'WAITING') return null
  if (sessionStatus === 'PAUSED' || card.remainingFrozenSeconds != null) {
    return card.remainingFrozenSeconds ?? null
  }
  if (card.deadline) {
    const ms = new Date(card.deadline).getTime() - nowServerMs(offsetMs)
    return Math.max(0, Math.floor(ms / 1000))
  }
  return card.remainingSeconds ?? null
}
