import { onMounted, onUnmounted, ref } from 'vue'

export function usePolling(load, interval = 1000) {
  const data = ref(null)
  const error = ref('')
  const loading = ref(true)
  const controller = new AbortController()
  let disposed = false
  let timer
  let pending
  function refresh() {
    if (disposed) return Promise.resolve()
    if (pending) return pending
    clearTimeout(timer)
    pending = (async () => {
      try {
        const result = await load(controller.signal)
        if (!disposed) {
          data.value = result
          error.value = ''
        }
      } catch (cause) {
        if (!disposed) error.value = cause.message
      } finally {
        loading.value = false
        pending = null
        if (!disposed) timer = setTimeout(refresh, interval)
      }
    })()
    return pending
  }
  onMounted(refresh)
  onUnmounted(() => {
    disposed = true
    clearTimeout(timer)
    controller.abort()
  })
  return { data, error, loading, refresh }
}
