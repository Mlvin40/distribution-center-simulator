import { getJson, postJson } from '@/services/http'

export async function getScheduling(signal) {
  const [queue, policies, levels] = await Promise.all([
    getJson('/api/scheduling', { signal }),
    getJson('/api/scheduling/policies', { signal }),
    getJson('/api/service-levels', { signal }),
  ])
  return { queue, policies, levels }
}
export const changePolicy = (policy) => postJson('/api/scheduling/policy', { policy })
export const comparePolicies = (body) => postJson('/api/scheduling/comparisons', body)
