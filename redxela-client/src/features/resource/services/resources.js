import { getJson } from '@/services/http'

export function getResources(signal) {
  return getJson('/api/resources', { signal })
}
