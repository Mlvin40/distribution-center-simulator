<script setup>
import { computed, ref, watch } from 'vue'
import { useOperations } from '@/composables/useOperations'
import { usePolling } from '@/composables/usePolling'
import { getScheduling, changePolicy } from '../services/scheduling'
import PolicyComparison from '../components/PolicyComparison.vue'
import QueueTable from '../components/QueueTable.vue'
import PageHeading from '@/components/ui/PageHeading.vue'
import MetricCard from '@/components/ui/MetricCard.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import UiPanel from '@/components/ui/UiPanel.vue'
import ViewState from '@/components/ui/ViewState.vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import { policyNames, resourceNames } from '@/utils/display'
const completed = computed(
  () =>
    data.value?.queue.metrics.reduce((total, metrics) => total + metrics.completedOrders, 0) || 0,
)
const {
  data: desk,
  error: deskError,
  loading: deskLoading,
  refresh: refreshDesk,
} = usePolling(getScheduling, 5000)
const { data: live, error: liveError, loading: liveLoading, refresh: refreshLive } = useOperations()
const data = computed(() =>
  desk.value && live.value ? { ...desk.value, queue: live.value.scheduling } : null,
)
const error = computed(() => deskError.value || liveError.value)
const loading = computed(() => deskLoading.value || liveLoading.value)
async function refresh() {
  await Promise.all([refreshDesk(), refreshLive()])
}
const selected = ref('STRICT_PRIORITY')
const pending = ref(false)
const actionError = ref('')
watch(
  () => data.value?.queue.policy,
  (value) => {
    if (value) selected.value = value
  },
)
async function apply() {
  pending.value = true
  actionError.value = ''
  try {
    await changePolicy(selected.value)
    await refresh()
  } catch (cause) {
    actionError.value = cause.message
  } finally {
    pending.value = false
  }
}
</script>
<template>
  <PageHeading
    eyebrow="POLÍTICAS DE ATENCIÓN"
    title="Planificación"
    description="Una cola con criterio. Evalúa la atención y distingue las estimaciones de la operación real."
    ><StatusBadge
      :tone="error ? 'danger' : loading ? 'neutral' : 'success'"
      :icon="error ? 'alert' : 'clock'"
      >{{
        data && !error
          ? data.queue.simulatedSeconds.toFixed(1) + ' s simulados'
          : error
            ? 'Sin conexión'
            : 'Consultando'
      }}</StatusBadge
    ></PageHeading
  >
  <ViewState
    v-if="loading || error"
    :loading="loading"
    :error="error"
    title="Consultando planificación…"
  />
  <template v-if="data && !error">
    <div class="metric-grid">
      <MetricCard
        label="PEDIDOS ELEGIBLES"
        :value="data.queue.eligible.length"
        detail="Listos para competir por recursos"
        icon="orders"
      /><MetricCard
        label="ESPERANDO INVENTARIO"
        :value="data.queue.waitingInventory.length"
        detail="Fuera de la cola elegible"
        icon="box"
      /><MetricCard
        label="SELECCIONES DE TRABAJADORES"
        :value="data.queue.claimed.length"
        detail="Pedidos con selección reservada"
        icon="users"
      /><MetricCard
        label="COMPLETADOS REALES"
        :value="completed"
        detail="Finalizaciones registradas por el motor"
        icon="check"
      />
    </div>
    <UiPanel
      title="Política de atención"
      description="Cambia el criterio sin perder los pedidos pendientes."
      ><template #actions
        ><StatusBadge tone="success" icon="chart">{{
          policyNames[data.queue.policy]
        }}</StatusBadge></template
      >
      <div class="grid gap-5 p-5 xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
        <div>
          <form class="flex flex-wrap items-end gap-3" @submit.prevent="apply">
            <label class="field min-w-0 flex-1"
              >Política activa<select v-model="selected" class="input">
                <option v-for="policy in data.policies" :key="policy.code" :value="policy.code">
                  {{ policy.name }}
                </option>
              </select></label
            ><button :disabled="pending" class="btn btn-primary" :aria-busy="pending">
              <AppIcon name="check" :size="15" />Aplicar política
            </button>
          </form>
          <p v-if="actionError" role="alert" class="notice notice-danger mt-3">{{ actionError }}</p>
          <p class="mt-4 text-xs leading-relaxed text-secondary" data-testid="policy-criterion">
            {{ data.queue.criterion }}. Empates: {{ data.queue.tieBreaker }}.
          </p>
        </div>
        <div class="rounded-lg border border-border bg-surface-muted/50 p-4">
          <h3 class="mb-3 text-xs font-semibold">Objetivos de espera por servicio</h3>
          <ul class="flex flex-wrap gap-x-5 gap-y-2">
            <li v-for="level in data.levels" :key="level.code" class="text-xs">
              <span class="text-secondary">{{ level.name }}</span
              ><span class="ml-1.5 font-medium">{{
                level.level === 1 ? 'Inmediato' : level.targetWaitSimulatedMinutes + ' min sim.'
              }}</span>
            </li>
          </ul>
          <p class="mt-3 text-[11px] leading-relaxed text-muted">
            Las metas de espera no son garantías bajo saturación. El reloj utiliza tiempo simulado.
          </p>
        </div>
      </div>
      <div
        class="flex items-start gap-2 border-t border-border px-5 py-3 text-xs leading-relaxed text-secondary"
      >
        <AppIcon name="info" :size="15" class="mt-0.5" />Los trabajadores Java aplican esta política
        al seleccionar el próximo pedido. Las selecciones pueden ejecutarse en paralelo; un inicio
        observado no sustituye el orden atómico de selección.
      </div></UiPanel
    >
    <PolicyComparison />
    <QueueTable :entries="data.queue.eligible" title="Cola elegible en orden de atención" />
    <div class="grid items-start gap-5 xl:grid-cols-2">
      <QueueTable
        :entries="data.queue.waitingInventory"
        title="Espera de inventario (fuera de la cola elegible)"
      /><QueueTable :entries="data.queue.claimed" title="Selecciones reservadas por trabajadores" />
    </div>
    <UiPanel
      title="Métricas del sistema real"
      description="Resultados registrados por el motor, separados del experimento comparativo."
      ><div class="grid divide-y divide-border xl:grid-cols-3 xl:divide-x xl:divide-y-0">
        <article v-for="metrics in data.queue.metrics" :key="metrics.policy" class="space-y-4 p-5">
          <div class="flex items-center justify-between gap-3">
            <h3 class="text-sm font-semibold">{{ policyNames[metrics.policy] }}</h3>
            <AppIcon name="chart" :size="17" class="text-primary" />
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div>
              <p class="eyebrow">SELECCIONES</p>
              <p class="mt-1 text-2xl font-semibold tabular-nums">{{ metrics.selections }}</p>
            </div>
            <div>
              <p class="eyebrow">COMPLETADOS</p>
              <p class="mt-1 text-2xl font-semibold tabular-nums">{{ metrics.completedOrders }}</p>
            </div>
          </div>
          <dl class="space-y-2 text-xs">
            <div class="flex justify-between gap-3">
              <dt class="text-secondary">Espera media</dt>
              <dd>
                {{
                  metrics.averageCompletedWaitSimulatedSeconds === null
                    ? 'Sin muestras'
                    : metrics.averageCompletedWaitSimulatedSeconds.toFixed(1) + ' s sim.'
                }}
              </dd>
            </div>
            <div class="flex justify-between gap-3">
              <dt class="text-secondary">Proceso medio</dt>
              <dd>
                {{
                  metrics.averageProcessingSimulatedSeconds === null
                    ? 'Sin muestras'
                    : metrics.averageProcessingSimulatedSeconds.toFixed(1) + ' s sim.'
                }}
              </dd>
            </div>
          </dl>
          <div class="space-y-2 border-t border-border pt-3">
            <p class="eyebrow">OCUPACIÓN ATRIBUIDA</p>
            <div
              v-for="(fraction, resource) in metrics.resourceUtilization"
              :key="resource"
              class="flex items-center gap-2 text-[11px] text-secondary"
            >
              <span class="w-24" :title="resource">{{ resourceNames[resource] || resource }}</span>
              <div class="progress-track flex-1">
                <div class="progress-fill" :style="{ width: `${fraction * 100}%` }" />
              </div>
              <span class="w-10 text-right tabular-nums">{{ (fraction * 100).toFixed(1) }} %</span>
            </div>
          </div>
        </article>
      </div>
      <p
        class="border-t border-border bg-surface-muted/40 px-5 py-3 text-[11px] leading-relaxed text-secondary"
      >
        Sin pedidos completados, las medias de espera y proceso muestran «Sin muestras». La
        ocupación atribuida mide tiempo de recursos retenidos por selecciones de cada política sobre
        la ventana común de observación.
      </p></UiPanel
    >
  </template>
</template>
