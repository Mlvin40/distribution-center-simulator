import { getJson, postJson } from '@/services/http'
export const getReception = (signal) => getJson('/api/reception', { signal })
export const registerArrival = (body) => postJson('/api/reception/arrivals', body)
export const getStorage = (signal) => getJson('/api/storage', { signal })
