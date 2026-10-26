<script setup>
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { useOperations } from '@/composables/useOperations'
import SimulationControls from '../components/SimulationControls.vue'
import ActiveOrders from '../components/ActiveOrders.vue'
import PageHeading from '@/components/ui/PageHeading.vue'
import MetricCard from '@/components/ui/MetricCard.vue'
import UiPanel from '@/components/ui/UiPanel.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import ViewState from '@/components/ui/ViewState.vue'
import AreaChart from '@/components/ui/AreaChart.vue'
import { resourceNames, orderStates } from '@/utils/display'
const { data, error, loading, connection, refresh } = useOperations()
const waits = computed(
  () =>
    data.value?.recentOrders
      .filter((o) => o.status === 'COMPLETED')
      .toReversed()
      .map((o) => {
        const start = o.history.find((e) => e.status === 'PICKING').simulatedSeconds
        return { label: o.id, value: start - o.history[0].simulatedSeconds }
      }) || [],
)
const mean = (value) => (value === null ? 'Sin muestras' : `${value.toFixed(1)} s sim.`)
</script>
<template>
  <PageHeading
    eyebrow="CENTRO DE OPERACIONES"
    title="Dashboard operativo"
    description="Pedidos, capacidad y actividad real del centro de distribución."
    ><StatusBadge :tone="error ? 'danger' : 'success'" icon="activity">{{
      error ? 'Sin conexión' : connection
    }}</StatusBadge></PageHeading
  >
  <ViewState
    v-if="loading || error"
    :loading="loading"
    :error="error"
    title="Sincronizando operaciones…"
  />
  <template v-if="data && !error">
    <div class="metric-grid">
      <MetricCard
        label="PEDIDOS RECIBIDOS"
        :value="data.metrics.received"
        detail="Total de esta ejecución"
        icon="orders"
      /><MetricCard
        label="EN PROCESAMIENTO"
        :value="data.metrics.processing"
        detail="Trabajadores con pedidos activos"
        icon="activity"
      /><MetricCard
        label="COMPLETADOS"
        :value="data.metrics.completed"
        detail="Despachos confirmados"
        icon="check"
      /><MetricCard
        label="EN ESPERA"
        :value="data.metrics.waiting"
        detail="Inventario o recursos pendientes"
        icon="clock"
      />
    </div>
    <SimulationControls :status="data.simulation" @changed="refresh" />
    <div v-if="data.deadlock.detected" class="notice notice-danger" role="alert">
      Hay una espera circular real en el centro.
      <RouterLink to="/deadlock" class="font-semibold underline"
        >Resolver conflicto de recursos</RouterLink
      >
    </div>
    <div class="grid items-start gap-5 xl:grid-cols-[minmax(0,1.7fr)_minmax(0,1fr)]">
      <UiPanel
        title="Espera de pedidos completados"
        description="Últimos pedidos completados entre los 100 registros recientes, en orden de llegada."
        ><AreaChart
          v-if="waits.length"
          title="Espera observada antes de iniciar trabajo"
          caption="Segundos simulados · orden de llegada"
          :points="waits" /><ViewState
          v-else
          title="Todavía no hay despachos"
          description="La gráfica aparecerá al completar pedidos reales; no contiene datos de demostración."
          icon="chart"
      /></UiPanel>
      <UiPanel
        title="Rendimiento de la ejecución"
        :description="`${data.simulatedSeconds.toFixed(1)} segundos simulados desde el arranque`"
        ><dl class="space-y-4 p-5 text-sm">
          <div class="flex justify-between gap-3">
            <dt class="text-secondary">Espera promedio</dt>
            <dd>{{ mean(data.metrics.averageWaitSimulatedSeconds) }}</dd>
          </div>
          <div class="flex justify-between gap-3">
            <dt class="text-secondary">Proceso promedio</dt>
            <dd>{{ mean(data.metrics.averageProcessingSimulatedSeconds) }}</dd>
          </div>
          <div class="flex justify-between gap-3">
            <dt class="text-secondary">Metas de espera incumplidas</dt>
            <dd>{{ data.metrics.missedTargets }}</dd>
          </div>
          <div class="flex justify-between gap-3">
            <dt class="text-secondary">Cancelados / fallidos</dt>
            <dd>{{ data.metrics.cancelled }} / {{ data.metrics.failed }}</dd>
          </div>
          <div class="border-t border-border pt-4">
            <p class="mb-2 text-secondary">
              Almacén · {{ data.storage.occupied }} / {{ data.storage.capacity }} unidades
            </p>
            <div class="progress-track">
              <div
                class="progress-fill"
                :style="{ width: `${(data.storage.occupied / data.storage.capacity) * 100}%` }"
              />
            </div>
          </div>
        </dl>
        <p class="border-t border-border p-5 text-xs text-secondary">
          Promedios de completados reales. Las metas de espera son objetivos, no garantías bajo
          saturación.
        </p></UiPanel
      >
    </div>
    <ActiveOrders :active="data.active" />
    <div class="grid items-start gap-5 xl:grid-cols-2">
      <UiPanel
        title="Capacidad compartida"
        description="Disponibilidad instantánea y utilización acumulada desde el arranque."
        ><div class="space-y-4 p-5">
          <div v-for="resource in data.resources.resources" :key="resource.type">
            <div class="mb-2 flex justify-between gap-3 text-xs">
              <span class="font-medium">{{ resourceNames[resource.type] }}</span
              ><span class="text-secondary"
                >{{ resource.available }} libres / {{ resource.capacity }} ·
                {{ (data.metrics.resourceUtilization[resource.type] * 100).toFixed(1) }} %
                acumulado</span
              >
            </div>
            <div class="progress-track">
              <div
                class="progress-fill"
                :style="{ width: `${(resource.inUse / resource.capacity) * 100}%` }"
              />
            </div>
          </div></div
      ></UiPanel>
      <UiPanel
        title="Actividad reciente"
        description="Eventos del motor; se muestran los últimos 12."
        ><ViewState
          v-if="!data.events.length"
          title="Esperando actividad"
          description="Los trabajadores registrarán sus etapas y asignaciones aquí."
        />
        <ol v-else class="divide-y divide-border">
          <li
            v-for="(event, index) in data.events.slice(-12).toReversed()"
            :key="index"
            class="flex items-start gap-3 px-5 py-3"
          >
            <StatusBadge>{{ orderStates[event.stage] }}</StatusBadge>
            <div class="min-w-0">
              <p class="text-xs text-secondary">{{ event.detail }}</p>
              <p class="id-text mt-1">{{ event.orderId }}</p>
            </div>
            <span class="ml-auto whitespace-nowrap text-xs text-muted"
              >{{ event.simulatedSeconds.toFixed(1) }} s sim.</span
            >
          </li>
        </ol></UiPanel
      >
    </div>
  </template>
</template>
