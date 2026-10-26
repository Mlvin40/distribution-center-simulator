<script setup>
import UiPanel from '@/components/ui/UiPanel.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import ViewState from '@/components/ui/ViewState.vue'
import { orderStates } from '@/utils/display'
defineProps({ active: { type: Array, required: true } })
</script>
<template>
  <UiPanel
    title="Procesamiento en curso"
    description="Progreso del trabajo ejecutado en Java; las esperas no aumentan el avance."
  >
    <ViewState
      v-if="!active.length"
      title="Sin pedidos en ejecución"
      description="Inicia el motor con pedidos reservados para observar las etapas operativas."
      icon="activity"
    />
    <div v-else class="table-scroll" tabindex="0" aria-label="Progreso de pedidos activos">
      <table class="data-table">
        <thead>
          <tr>
            <th>Pedido</th>
            <th>Etapa real</th>
            <th>Trabajo realizado</th>
            <th>Trabajo restante estimado</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="order in active" :key="order.orderId">
            <td class="id-text">{{ order.orderId }}</td>
            <td>
              <StatusBadge :tone="order.waitingResources ? 'warning' : 'info'"
                >{{ orderStates[order.stage]
                }}{{ order.waitingResources ? ' · espera de recursos' : '' }}</StatusBadge
              >
            </td>
            <td class="min-w-44">
              <div class="mb-2 text-xs tabular-nums">
                {{ order.progressPercent.toFixed(1) }} % ·
                {{ order.workedSimulatedSeconds.toFixed(1) }} s sim.
              </div>
              <div
                class="progress-track"
                role="progressbar"
                :aria-label="`Progreso de ${order.orderId}`"
                :aria-valuenow="order.progressPercent"
                aria-valuemin="0"
                aria-valuemax="100"
              >
                <div class="progress-fill" :style="{ width: `${order.progressPercent}%` }" />
              </div>
            </td>
            <td>
              {{ order.remainingWorkSimulatedSeconds.toFixed(1) }} s simulados
              <p class="mt-1 text-xs text-muted">Más la espera por recursos</p>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </UiPanel>
</template>
