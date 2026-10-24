<script setup>
import AppIcon from '@/components/ui/AppIcon.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
const icons = {
  PACKING_STATION: 'box',
  LOADING_AREA: 'truck',
  WAREHOUSE_WORKER: 'users',
  FORKLIFT: 'forklift',
  QUALITY_STATION: 'shield',
  SCANNER: 'scan',
}
defineProps({ resource: { type: Object, required: true } })
const names = {
  PACKING_STATION: 'Estaciones de empaque',
  LOADING_AREA: 'Áreas de carga',
  WAREHOUSE_WORKER: 'Encargados de bodega',
  FORKLIFT: 'Montacargas',
  QUALITY_STATION: 'Control de calidad',
  SCANNER: 'Escáner de Recepción y Despacho',
}
const areas = { ORDER_PROCESSING: 'Procesamiento', RECEPTION: 'Recepción', DISPATCH: 'Despacho' }
</script>

<template>
  <article class="panel" :aria-label="names[resource.type]">
    <header class="flex items-start gap-3 px-5 pt-5">
      <span class="metric-icon shrink-0 rounded-lg"><AppIcon :name="icons[resource.type]" /></span>
      <div class="min-w-0 flex-1">
        <h2 class="leading-snug">{{ names[resource.type] }}</h2>
        <p class="mt-1 text-xs text-muted">
          {{ resource.capacity }}
          {{ resource.capacity === 1 ? 'unidad compartida' : 'unidades disponibles en el centro' }}
        </p>
      </div>
      <StatusBadge :tone="resource.inUse ? 'info' : 'success'">{{
        resource.inUse ? 'En uso' : 'Disponible'
      }}</StatusBadge>
    </header>
    <div class="px-5 pt-5 pb-4">
      <div class="mb-2 flex items-end justify-between">
        <p class="text-[25px] font-semibold tabular-nums">
          {{ resource.available }}
          <span class="text-xs font-normal text-secondary">libres de {{ resource.capacity }}</span>
        </p>
        <span class="text-xs text-secondary"
          >{{ Math.round((resource.inUse / resource.capacity) * 100) }} % en uso</span
        >
      </div>
      <div
        class="progress-track"
        role="progressbar"
        :aria-label="`Ocupación de ${names[resource.type]}`"
        :aria-valuenow="resource.inUse"
        :aria-valuemax="resource.capacity"
        :aria-valuemin="0"
      >
        <div
          class="progress-fill bg-blue-500"
          :style="{ width: `${(resource.inUse / resource.capacity) * 100}%` }"
        />
      </div>
      <p class="sr-only" data-testid="resource-count">
        {{ resource.available }} disponibles · {{ resource.inUse }} en uso ·
        {{ resource.capacity }} en total
      </p>
    </div>
    <ul class="divide-y divide-border/70 border-t border-border">
      <li v-for="unit in resource.units" :key="unit.id" class="px-5 py-2.5">
        <div class="flex items-center justify-between gap-2">
          <span class="id-text">{{ unit.id }}</span
          ><span
            class="flex items-center gap-1.5 text-[11px]"
            :class="unit.status === 'AVAILABLE' ? 'text-primary-ink' : 'text-blue-600'"
            ><AppIcon :name="unit.status === 'AVAILABLE' ? 'check' : 'activity'" :size="13" />{{
              unit.status === 'AVAILABLE' ? 'Disponible' : 'En uso'
            }}</span
          >
        </div>
        <div
          v-if="unit.owner"
          class="mt-2 rounded-md bg-surface-muted p-3 text-xs leading-relaxed text-secondary"
        >
          <p class="font-medium text-main">
            {{ areas[unit.owner.area] }} · {{ unit.owner.description }}
          </p>
          <p class="mt-1 break-all">Operación: {{ unit.owner.operationId }}</p>
          <p v-if="unit.owner.orderId" class="break-all">Pedido: {{ unit.owner.orderId }}</p>
        </div>
      </li>
    </ul>
    <div class="border-t border-border bg-surface-muted/50 px-5 py-3">
      <p class="flex items-center justify-between text-xs text-secondary">
        <span class="flex items-center gap-1.5"
          ><AppIcon name="clock" :size="14" />Solicitudes en espera</span
        ><StatusBadge :tone="resource.waiting.length ? 'warning' : 'neutral'">{{
          resource.waiting.length
        }}</StatusBadge>
      </p>
      <ul
        v-if="resource.waiting.length"
        class="mt-3 space-y-3 text-xs leading-relaxed text-secondary"
      >
        <li v-for="request in resource.waiting" :key="request.owner.operationId" class="break-all">
          <p class="font-medium text-main">
            {{ areas[request.owner.area] }} · {{ request.owner.description }}
          </p>
          <p>Operación: {{ request.owner.operationId }}</p>
          <p v-if="request.owner.orderId">Pedido: {{ request.owner.orderId }}</p>
          <p v-if="request.unavailableResources.length" class="mt-1">
            Sin disponibilidad:
            {{ request.unavailableResources.map((type) => names[type]).join(', ') }}
          </p>
          <p v-else class="mt-1">Esperando turno para adquirir el conjunto solicitado</p>
        </li>
      </ul>
    </div>
  </article>
</template>
