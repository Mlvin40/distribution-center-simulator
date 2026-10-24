<script setup>
import UiPanel from '@/components/ui/UiPanel.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import ViewState from '@/components/ui/ViewState.vue'
import { serviceNames, orderStates } from '@/utils/display'
defineProps({ entries: { type: Array, required: true }, title: { type: String, required: true } })
</script>
<template>
  <UiPanel :title="title"
    ><template #actions
      ><StatusBadge :tone="entries.length ? 'warning' : 'neutral'" icon="orders"
        >{{ entries.length }} pedidos</StatusBadge
      ></template
    ><ViewState
      v-if="!entries.length"
      title="Sin pedidos en este grupo"
      description="Los pedidos aparecerán automáticamente cuando cambie su estado en el motor."
      icon="orders"
    /><template v-else
      ><p v-if="entries.length > 100" class="px-5 py-3 text-xs text-muted">
        Se muestran los primeros 100 de {{ entries.length }} pedidos de este grupo
      </p>
      <div
        class="table-scroll max-h-[480px] overflow-y-auto"
        tabindex="0"
        :aria-label="`Desplazar ${title}`"
      >
        <table class="data-table">
          <caption class="sr-only">
            {{
              title
            }}; tiempos en segundos simulados
          </caption>
          <thead class="sticky top-0 z-10">
            <tr>
              <th scope="col">Pedido</th>
              <th scope="col">Servicio / prioridad efectiva</th>
              <th scope="col">Unidades / trabajo (s sim.)</th>
              <th scope="col">Espera (s sim.)</th>
              <th scope="col">Objetivo</th>
              <th scope="col">Motivo</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="entry in entries.slice(0, 100)" :key="entry.order.id">
              <td class="min-w-48 max-w-56">
                <span class="id-text">{{ entry.order.id }}</span>
              </td>
              <td>
                <span class="font-medium" :title="entry.order.serviceLevel">{{
                  serviceNames[entry.order.serviceLevel] || entry.order.serviceLevel
                }}</span>
                <p class="mt-1 text-xs text-secondary">
                  Prioridad efectiva: {{ entry.effectivePriority }}
                </p>
              </td>
              <td class="tabular-nums">
                {{ entry.order.totalUnits }} / {{ entry.order.baseWorkSimulatedSeconds }}
              </td>
              <td class="tabular-nums">{{ entry.waitingSimulatedSeconds.toFixed(1) }}</td>
              <td>
                <StatusBadge
                  :tone="entry.targetMissed ? 'danger' : 'success'"
                  :icon="entry.targetMissed ? 'alert' : 'check'"
                  >{{ entry.targetMissed ? 'Incumplido' : 'Dentro del objetivo' }}</StatusBadge
                >
              </td>
              <td class="text-xs text-secondary" :title="entry.reason">
                {{
                  orderStates[entry.reason] ||
                  (entry.reason === 'CLAIMED' ? 'Reservado por trabajador' : entry.reason)
                }}
              </td>
            </tr>
          </tbody>
        </table>
      </div></template
    ></UiPanel
  >
</template>
