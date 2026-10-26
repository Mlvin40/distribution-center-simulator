import { getJson, postJson } from '@/services/http'
export const getDeadlock = (signal) => getJson('/api/deadlock', { signal })
export const startDeadlock = () => postJson('/api/deadlock/start')
export const resolveDeadlock = (operationId) => postJson('/api/deadlock/resolve', { operationId })
