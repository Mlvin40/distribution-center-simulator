import { onMounted, onUnmounted, ref } from 'vue'
import { getOperations } from '@/features/simulation/services/simulation'

// EventSource se reconecta automáticamente; REST recupera el estado durante interrupciones del canal
export function useOperations() {
  const data = ref(null)
  const error = ref('')
  const loading = ref(true)
  const connection = ref('Conectando')
  const controller = new AbortController()
  let source
  let timer
  let pending
  let disposed = false
  function accept(snapshot) {
    if (disposed) return
    const captured = Date.parse(snapshot.capturedAt)
    const previous = data.value ? Date.parse(data.value.capturedAt) : -Infinity
    if (
      captured > previous ||
      (captured === previous && snapshot.simulatedSeconds >= data.value.simulatedSeconds)
    )
      data.value = snapshot
    error.value = ''
    loading.value = false
  }
  function refresh() {
    if (pending || disposed) return pending || Promise.resolve()
    pending = getOperations(controller.signal)
      .then(accept)
      .catch((cause) => {
        if (!disposed) error.value = cause.message
      })
      .finally(() => {
        loading.value = false
        pending = null
      })
    return pending
  }
  const disconnect = () => source?.close()
  function connect() {
    source?.close()
    connection.value = 'Conectando'
    source = new EventSource('/api/operations/events')
    source.addEventListener('snapshot', (event) => {
      try {
        accept(JSON.parse(event.data))
        connection.value = 'SSE en vivo'
      } catch {
        error.value = 'No se pudo interpretar la actualización del servidor'
      }
    })
    source.onerror = () => {
      connection.value = 'Reconectando · respaldo REST'
      refresh()
    }
  }
  function restore(event) {
    if (event.persisted) {
      refresh()
      connect()
    }
  }
  onMounted(() => {
    window.addEventListener('pagehide', disconnect)
    window.addEventListener('pageshow', restore)
    refresh()
    connect()
    timer = setInterval(() => {
      if (source.readyState !== EventSource.OPEN) refresh()
    }, 2000)
  })
  onUnmounted(() => {
    disposed = true
    window.removeEventListener('pagehide', disconnect)
    window.removeEventListener('pageshow', restore)
    source?.close()
    clearInterval(timer)
    controller.abort()
  })
  return { data, error, loading, connection, refresh }
}
