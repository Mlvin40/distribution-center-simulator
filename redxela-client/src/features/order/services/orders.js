import { getJson, postJson } from '@/services/http'

export async function getOrderDesk(signal) {
  const [orders, customers, products, levels, generator, inventory] = await Promise.all([
    getJson('/api/orders?limit=100', { signal }),
    getJson('/api/customers?limit=100', { signal }),
    getJson('/api/products', { signal }),
    getJson('/api/service-levels', { signal }),
    getJson('/api/generator', { signal }),
    getJson('/api/inventory', { signal }),
  ])
  return { orders, customers, products, levels, generator, inventory }
}
export const createCustomer = (body) => postJson('/api/customers', body)
export const createOrder = (body) => postJson('/api/orders', body)
export const controlGenerator = (command, body) => postJson(`/api/generator/${command}`, body)

export const getCustomer = (id) => getJson(`/api/customers/${encodeURIComponent(id)}`)
