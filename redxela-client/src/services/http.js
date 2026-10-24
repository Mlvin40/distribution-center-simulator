export async function getJson(path, options = {}) {
  const response = await fetch(path, options)
  if (!response.ok) {
    const error = await response.json().catch(() => null)
    throw new Error(error?.message || `Error HTTP ${response.status}`)
  }
  return response.json()
}

export function postJson(path, body, signal) {
  return getJson(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    signal,
  })
}
