<script setup>
import AppIcon from '@/components/ui/AppIcon.vue'
import UiPanel from '@/components/ui/UiPanel.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import ViewState from '@/components/ui/ViewState.vue'
import { merchandiseNames } from '@/utils/display'
defineProps({ inventory: { type: Object, required: true } })
</script>
<template>
  <UiPanel
    title="Existencias y reservas"
    description="Disponibilidad real del catálogo para atender nuevos pedidos."
  >
    <template #actions
      ><StatusBadge tone="success" icon="box"
        >{{ inventory.onHand }} / {{ inventory.capacity }} unidades físicas</StatusBadge
      ></template
    >
    <div class="grid xl:grid-cols-[minmax(0,1.7fr)_minmax(0,1fr)]">
      <div class="min-w-0">
        <div class="table-scroll" tabindex="0" aria-label="Desplazar tabla de inventario">
          <table class="data-table" data-testid="inventory-table">
            <caption class="sr-only">
              Existencias del catálogo, incluyendo unidades reservadas y disponibles
            </caption>
            <thead>
              <tr>
                <th scope="col">Producto</th>
                <th scope="col">Mercancía</th>
                <th scope="col" class="text-right">Físicas</th>
                <th scope="col" class="text-right">Reservadas</th>
                <th scope="col" class="text-right">Disponibles</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="stock in inventory.products" :key="stock.product.id">
                <td>
                  <p class="font-medium">{{ stock.product.name }}</p>
                  <p class="mt-1 font-mono text-[10px] text-muted">{{ stock.product.id }}</p>
                </td>
                <td>
                  <StatusBadge>{{
                    merchandiseNames[stock.product.merchandiseType] || stock.product.merchandiseType
                  }}</StatusBadge>
                </td>
                <td class="text-right tabular-nums">{{ stock.onHand }}</td>
                <td class="text-right tabular-nums text-secondary">{{ stock.reserved }}</td>
                <td class="text-right">
                  <span
                    class="font-semibold tabular-nums"
                    :class="stock.available ? 'text-primary-ink' : 'text-muted'"
                    >{{ stock.available }}</span
                  >
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="border-t border-border px-5 py-4">
          <div class="mb-2 flex justify-between gap-2 text-xs text-secondary">
            <span>Ocupación del almacén</span
            ><span class="font-medium text-main"
              >{{ ((inventory.onHand / inventory.capacity) * 100).toFixed(1) }} %</span
            >
          </div>
          <div
            class="progress-track"
            role="progressbar"
            aria-label="Capacidad ocupada del almacén"
            :aria-valuenow="inventory.onHand"
            :aria-valuemax="inventory.capacity"
            :aria-valuemin="0"
          >
            <div
              class="progress-fill"
              :style="{ width: `${(inventory.onHand / inventory.capacity) * 100}%` }"
            />
          </div>
          <p class="mt-3 text-[11px] leading-relaxed text-muted">
            Las reservas siguen ocupando sus ubicaciones físicas hasta el despacho. Las llegadas se
            procesan mediante Recepción.
          </p>
        </div>
      </div>
      <div class="min-w-0 border-t border-border bg-surface-muted/40 xl:border-t-0 xl:border-l">
        <div class="flex items-center justify-between gap-2 px-5 pt-5">
          <h3 class="flex items-center gap-2 text-sm font-medium">
            <AppIcon name="clock" :size="16" />Espera de existencias
          </h3>
          <StatusBadge :tone="inventory.waitingOrders.length ? 'warning' : 'neutral'">{{
            inventory.waitingOrders.length
          }}</StatusBadge>
        </div>
        <ViewState
          v-if="!inventory.waitingOrders.length"
          title="Sin pedidos pendientes de stock"
          description="Las solicitudes que no puedan reservar todos sus productos aparecerán aquí."
          icon="check"
        />
        <ul v-else class="max-h-80 space-y-3 overflow-y-auto p-5">
          <li
            v-for="order in inventory.waitingOrders.slice(0, 20)"
            :key="order.orderId"
            class="rounded-lg border border-border bg-surface p-3"
          >
            <p class="id-text">{{ order.orderId }}</p>
            <p
              v-for="shortage in order.shortages"
              :key="shortage.productId"
              class="mt-2 text-xs leading-relaxed text-secondary"
            >
              <span class="font-medium text-main">{{ shortage.productId }}</span> necesita
              {{ shortage.requested }}, disponibles {{ shortage.available }},
              <span class="font-medium">faltan {{ shortage.missing }}</span>
            </p>
          </li>
        </ul>
        <p v-if="inventory.waitingOrders.length > 20" class="px-5 pb-4 text-xs text-muted">
          Se muestran los primeros 20 de {{ inventory.waitingOrders.length }} pedidos sin
          existencias.
        </p>
      </div>
    </div>
  </UiPanel>
</template>
