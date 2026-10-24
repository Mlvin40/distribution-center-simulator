<script setup>
import { computed } from 'vue'
import { useOperations } from '@/composables/useOperations'
import ActiveOrders from '@/features/simulation/components/ActiveOrders.vue'
import SimulationControls from '@/features/simulation/components/SimulationControls.vue'
import { usePolling } from '@/composables/usePolling'
import PageHeading from '@/components/ui/PageHeading.vue'
import MetricCard from '@/components/ui/MetricCard.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import UiPanel from '@/components/ui/UiPanel.vue'
import ViewState from '@/components/ui/ViewState.vue'
import { serviceNames, orderStates, merchandiseNames } from '@/utils/display'
import { getOrderDesk } from '../services/orders'
import GeneratorControls from '../components/GeneratorControls.vue'
import ManualOrderForm from '../components/ManualOrderForm.vue'
import ReceptionPanel from '@/features/inventory/components/ReceptionPanel.vue'
import InventorySummary from '../components/InventorySummary.vue'
const {
  data: desk,
  error: deskError,
  loading: deskLoading,
  refresh: refreshDesk,
} = usePolling(getOrderDesk)
const { data: live, error: liveError, loading: liveLoading, refresh: refreshLive } = useOperations()
const data = computed(() =>
  desk.value && live.value
    ? { ...desk.value, orders: live.value.recentOrders, inventory: live.value.inventory }
    : null,
)
const error = computed(() => deskError.value || liveError.value)
const loading = computed(() => deskLoading.value || liveLoading.value)
async function refresh() {
  await Promise.all([refreshDesk(), refreshLive()])
}
</script>
<template>
  <PageHeading
    eyebrow="CENTRO DE OPERACIONES"
    title="Pedidos y generador"
    description="Gestiona las llegadas, registra solicitudes y consulta el inventario de esta ejecución."
    ><StatusBadge
      :tone="error ? 'danger' : loading ? 'neutral' : 'success'"
      :icon="error ? 'alert' : 'refresh'"
      >{{
        error ? 'Sin conexión' : loading ? 'Consultando' : 'Actualización cada segundo'
      }}</StatusBadge
    ></PageHeading
  >
  <ViewState
    v-if="loading || error"
    :loading="loading"
    :error="error"
    title="Consultando pedidos…"
  />
  <template v-if="data && !error">
    <SimulationControls :status="live.simulation" @changed="refresh" />
    <ActiveOrders :active="live.active" />
    <div class="metric-grid">
      <MetricCard
        label="PEDIDOS REGISTRADOS"
        :value="data.generator.totalOrders"
        detail="Total de esta ejecución"
        icon="orders"
      /><MetricCard
        label="GENERADOS AUTOMÁTICAMENTE"
        :value="data.generator.generatedOrders"
        detail="Llegadas del generador"
        icon="refresh"
      /><MetricCard
        label="UNIDADES RESERVADAS"
        :value="data.inventory.reserved"
        detail="Asignadas a pedidos, aún en almacén"
        icon="shield"
      /><MetricCard
        label="UNIDADES DISPONIBLES"
        :value="data.inventory.available"
        detail="Existencias libres del catálogo"
        icon="box"
      />
    </div>
    <div class="grid items-start gap-5 xl:grid-cols-[minmax(0,1.8fr)_minmax(0,1fr)]">
      <ManualOrderForm
        :customers="data.customers"
        :products="data.products"
        :levels="data.levels"
        @changed="refresh"
      /><GeneratorControls
        class="order-first xl:order-last"
        :status="data.generator"
        @changed="refresh"
      />
    </div>
    <InventorySummary :inventory="data.inventory" />
    <ReceptionPanel :products="data.products" />
    <UiPanel
      title="Pedidos recientes"
      description="Últimos 100 registros, del más reciente al más antiguo."
      ><template #actions
        ><StatusBadge icon="orders"
          >{{ data.orders.length }} registros visibles</StatusBadge
        ></template
      ><ViewState
        v-if="!data.orders.length"
        title="Todavía no hay pedidos"
        description="Registra un pedido manual o inicia el generador para observar las llegadas."
        icon="orders"
      />
      <div v-else class="table-scroll" tabindex="0" aria-label="Desplazar tabla de pedidos">
        <table class="data-table" data-testid="orders-table">
          <caption class="sr-only">
            Pedidos registrados, ordenados del más reciente al más antiguo
          </caption>
          <thead>
            <tr>
              <th scope="col">Pedido / cliente</th>
              <th scope="col">Nivel de servicio</th>
              <th scope="col">Productos solicitados</th>
              <th scope="col" class="text-right">Unidades</th>
              <th scope="col">Estado</th>
              <th scope="col">Origen</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="order in data.orders" :key="order.id">
              <td class="min-w-52 max-w-64">
                <p class="id-text font-medium text-main">{{ order.id }}</p>
                <p class="mt-1 text-[10px] text-muted">CLIENTE</p>
                <p class="id-text">{{ order.customerId }}</p>
              </td>
              <td>
                <StatusBadge
                  :tone="order.serviceLevel === 'EXPRESS' ? 'success' : 'neutral'"
                  :title="order.serviceLevel"
                  >{{ serviceNames[order.serviceLevel] || order.serviceLevel }}</StatusBadge
                >
              </td>
              <td class="min-w-40">
                <p
                  v-for="line in order.lines"
                  :key="line.product.id"
                  class="flex items-start justify-between gap-3"
                >
                  <span
                    >{{ line.product.name
                    }}<small class="mt-1 block text-muted">{{
                      merchandiseNames[line.product.merchandiseType]
                    }}</small></span
                  ><span class="whitespace-nowrap text-secondary">× {{ line.quantity }}</span>
                </p>
              </td>
              <td class="text-right font-semibold tabular-nums">{{ order.totalUnits }}</td>
              <td>
                <StatusBadge
                  :tone="
                    ['READY', 'COMPLETED'].includes(order.status)
                      ? 'success'
                      : ['FAILED', 'CANCELLED'].includes(order.status)
                        ? 'danger'
                        : 'warning'
                  "
                  :title="order.status"
                  >{{ orderStates[order.status] || order.status
                  }}<span class="sr-only"> ({{ order.status }})</span></StatusBadge
                >
              </td>
              <td>
                <span class="text-xs text-secondary" :title="order.source"
                  >{{ order.source === 'MANUAL' ? 'Manual' : 'Automático'
                  }}<span class="sr-only"> ({{ order.source }})</span></span
                >
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p
        class="border-t border-border bg-surface-muted/40 px-5 py-3 text-[11px] leading-relaxed text-secondary"
      >
        El estado proviene de Java. Esperar inventario no implica disponer de existencias ni estar
        listo para procesamiento.
      </p></UiPanel
    >
  </template>
</template>
