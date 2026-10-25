import { getJson, postJson } from '@/services/http'
export const getOperations = (signal) => getJson('/api/operations', { signal })
export const controlSimulation = (command) => postJson(`/api/simulation/${command}`)
