<script setup>
import { computed } from 'vue'
import { useOperations } from '@/composables/useOperations'
import ResourceCard from '../components/ResourceCard.vue'
import PageHeading from '@/components/ui/PageHeading.vue'
import MetricCard from '@/components/ui/MetricCard.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import ViewState from '@/components/ui/ViewState.vue'
const totals = computed(() => ({
  capacity: snapshot.value?.resources.reduce((sum, resource) => sum + resource.capacity, 0) || 0,
  available: snapshot.value?.resources.reduce((sum, resource) => sum + resource.available, 0) || 0,
  inUse: snapshot.value?.resources.reduce((sum, resource) => sum + resource.inUse, 0) || 0,
  waiting: new Set(
    snapshot.value?.resources.flatMap((resource) =>
      resource.waiting.map((request) => request.owner.operationId),
    ) || [],
  ).size,
}))

const { data, error, loading } = useOperations()
const snapshot = computed(() => data.value?.resources)
</script>

<template>
  <PageHeading
    eyebrow="CAPACIDAD Y SINCRONIZACIÓN"
    title="Recursos compartidos"
    description="Disponibilidad y asignaciones del centro de distribución, actualizadas desde el motor Java."
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
    title="Consultando recursos…"
  />
  <template v-if="snapshot && !error">
    <div class="metric-grid">
      <MetricCard
        label="CAPACIDAD TOTAL"
        :value="totals.capacity"
        detail="Unidades físicas del centro"
        icon="resources"
      /><MetricCard
        label="DISPONIBLES"
        :value="totals.available"
        detail="Recursos libres para asignación"
        icon="check"
      /><MetricCard
        label="EN USO"
        :value="totals.inUse"
        detail="Unidades con titular asignado"
        icon="activity"
      /><MetricCard
        label="OPERACIONES EN ESPERA"
        :value="totals.waiting"
        detail="Solicitudes únicas, sin duplicar por recurso"
        icon="clock"
      />
    </div>
    <div class="flex flex-wrap items-center justify-between gap-3">
      <h2>Distribución de recursos</h2>
      <p class="text-xs text-muted">
        Instantánea UTC: <time :datetime="snapshot.capturedAt">{{ snapshot.capturedAt }}</time>
      </p>
    </div>
    <div class="grid items-start gap-5 md:grid-cols-2 xl:grid-cols-3" data-testid="resources">
      <ResourceCard
        v-for="resource in snapshot.resources"
        :key="resource.type"
        :resource="resource"
      />
    </div>
    <p class="text-xs leading-relaxed text-secondary">
      El procesamiento normal obtiene cada conjunto sin retener recursos parciales. El experimento
      de conflicto permite una espera circular deliberada. El escáner es compartido entre Recepción
      y Despacho.
    </p>
  </template>
</template>
