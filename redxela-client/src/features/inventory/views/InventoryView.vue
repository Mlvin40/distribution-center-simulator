<script setup>
import { useOperations } from '@/composables/useOperations'
import PageHeading from '@/components/ui/PageHeading.vue'
import MetricCard from '@/components/ui/MetricCard.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import ViewState from '@/components/ui/ViewState.vue'
import InventorySummary from '@/features/order/components/InventorySummary.vue'
import ReceptionPanel from '../components/ReceptionPanel.vue'
const { data, error, loading, connection } = useOperations()
</script>
<template>
  <PageHeading
    eyebrow="ABASTECIMIENTO"
    title="Inventario y recepción"
    description="Existencias reservadas, llegadas por mensajes y reactivación automática de pedidos."
    ><StatusBadge :tone="error ? 'danger' : 'success'">{{
      error ? 'Sin conexión' : connection
    }}</StatusBadge></PageHeading
  >
  <ViewState
    v-if="loading || error"
    :loading="loading"
    :error="error"
    title="Consultando inventario…"
  />
  <template v-if="data && !error">
    <div class="metric-grid">
      <MetricCard
        label="UNIDADES FÍSICAS"
        :value="data.inventory.onHand"
        detail="Ubicadas dentro del almacén"
        icon="box"
      /><MetricCard
        label="RESERVADAS"
        :value="data.inventory.reserved"
        detail="Asignadas a pedidos"
        icon="shield"
      /><MetricCard
        label="DISPONIBLES"
        :value="data.inventory.available"
        detail="Sin reserva"
        icon="check"
      /><MetricCard
        label="ESPACIO LIBRE"
        :value="data.storage.availableCapacity"
        detail="Unidades adicionales que caben"
        icon="resources"
      />
    </div>
    <ReceptionPanel :products="data.inventory.products.map((stock) => stock.product)" />
    <InventorySummary :inventory="data.inventory" />
  </template>
</template>
