<script setup>
import { computed, ref } from 'vue'
import { useOperations } from '@/composables/useOperations'
import PageHeading from '@/components/ui/PageHeading.vue'
import MetricCard from '@/components/ui/MetricCard.vue'
import UiPanel from '@/components/ui/UiPanel.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import ViewState from '@/components/ui/ViewState.vue'
const { data, error, loading, connection } = useOperations()
const product = ref('')
const locations = computed(
  () =>
    data.value?.storage.locations.filter(
      (location) => !product.value || location.lots.some((lot) => lot.productId === product.value),
    ) || [],
)
const name = (id) =>
  data.value.inventory.products.find((stock) => stock.product.id === id)?.product.name || id
</script>
<template>
  <PageHeading
    eyebrow="CAPACIDAD FÍSICA"
    title="Almacén y ubicaciones"
    description="Pasillos, niveles y posiciones reales. Un mismo lote puede ocupar ubicaciones no contiguas."
    ><StatusBadge :tone="error ? 'danger' : 'success'">{{
      error ? 'Sin conexión' : connection
    }}</StatusBadge></PageHeading
  >
  <ViewState
    v-if="loading || error"
    :loading="loading"
    :error="error"
    title="Consultando ubicaciones…"
  />
  <template v-if="data && !error">
    <div class="metric-grid">
      <MetricCard
        label="CAPACIDAD TOTAL"
        :value="data.storage.capacity"
        :detail="`${data.storage.locations.length} ubicaciones físicas`"
        icon="resources"
      /><MetricCard
        label="OCUPACIÓN"
        :value="`${((data.storage.occupied / data.storage.capacity) * 100).toFixed(1)} %`"
        :detail="`${data.storage.occupied} unidades almacenadas`"
        icon="box"
      /><MetricCard
        label="RESERVADAS"
        :value="data.storage.reserved"
        detail="Siguen ocupando espacio hasta el despacho"
        icon="shield"
      /><MetricCard
        label="CAPACIDAD LIBRE"
        :value="data.storage.availableCapacity"
        detail="Se actualiza al retirar mercancía"
        icon="check"
      />
    </div>
    <UiPanel
      title="Mapa de almacenamiento"
      description="Cada tarjeta representa una ubicación identificable, con sus lotes y cantidades."
      ><div class="flex flex-wrap items-end justify-between gap-4 p-5">
        <label class="field w-full sm:max-w-sm"
          >Filtrar por producto<select v-model="product" class="input">
            <option value="">Todos los productos y ubicaciones</option>
            <option
              v-for="stock in data.inventory.products"
              :key="stock.product.id"
              :value="stock.product.id"
            >
              {{ stock.product.name }}
            </option>
          </select></label
        >
        <p class="text-xs text-secondary">
          {{ locations.length }} ubicaciones visibles · Consulta los lotes expandiendo cada
          ubicación
        </p>
      </div></UiPanel
    >
    <ViewState
      v-if="!locations.length"
      title="Este producto no tiene ubicaciones ocupadas"
      description="Registra una llegada en Inventario y recepción para asignar espacio automáticamente."
    />
    <div
      class="grid items-start gap-4 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-5"
      data-testid="storage-map"
    >
      <article v-for="location in locations" :key="location.location.id" class="panel min-w-0 p-4">
        <div class="flex flex-wrap items-center justify-between gap-2">
          <h2 class="font-mono text-sm">{{ location.location.id }}</h2>
          <StatusBadge :tone="location.occupied ? 'info' : 'success'">{{
            location.occupied ? 'Ocupada' : 'Libre'
          }}</StatusBadge>
        </div>
        <p class="mt-3 text-xs text-muted">
          Pasillo {{ location.location.aisle }} · Nivel {{ location.location.level }} · Posición
          {{ location.location.position }}
        </p>
        <p class="mt-4 text-2xl font-semibold tabular-nums">
          {{ location.occupied
          }}<span class="ml-1 text-sm font-normal text-muted"
            >/ {{ location.location.capacity }}</span
          >
        </p>
        <div class="progress-track mt-2">
          <div
            class="progress-fill"
            :style="{ width: `${(location.occupied / location.location.capacity) * 100}%` }"
          />
        </div>
        <p class="mt-3 text-xs text-secondary">
          Libres {{ location.availableCapacity }} · Reservadas {{ location.reserved }}
        </p>
        <details v-if="location.lots.length" class="mt-4 border-t border-border pt-3">
          <summary class="cursor-pointer rounded text-xs font-medium text-primary-ink">
            Ver {{ location.lots.length }} fragmentos de lote
          </summary>
          <ul class="mt-3 space-y-3">
            <li
              v-for="(lot, index) in location.lots"
              :key="`${lot.lotId}-${index}`"
              class="rounded-lg bg-surface-muted p-3"
            >
              <p class="text-sm font-medium">{{ name(lot.productId) }}</p>
              <p class="mt-1 text-xs text-secondary">
                {{ lot.quantity }} unidades · {{ lot.reserved }} reservadas
              </p>
              <p class="id-text mt-2" :title="lot.lotId">Lote {{ lot.lotId }}</p>
            </li>
          </ul>
        </details>
      </article>
    </div>
  </template>
</template>
