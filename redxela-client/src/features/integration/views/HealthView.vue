<script setup>
import { onMounted, ref } from 'vue'
import { getJson } from '@/services/http'
import { RouterLink } from 'vue-router'
import AppIcon from '@/components/ui/AppIcon.vue'
import MetricCard from '@/components/ui/MetricCard.vue'
import PageHeading from '@/components/ui/PageHeading.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import UiPanel from '@/components/ui/UiPanel.vue'
const detailsOpen = ref(false)

const health = ref(null)
const error = ref('')
const loading = ref(false)
async function checkConnection() {
  loading.value = true
  error.value = ''
  health.value = null
  try {
    health.value = await getJson('/api/health')
  } catch (cause) {
    error.value = `No se pudo conectar con Java: ${cause.message}`
  } finally {
    loading.value = false
  }
}
onMounted(checkConnection)
</script>

<template>
  <PageHeading
    eyebrow="MONITOREO DEL SISTEMA"
    title="Conexión del sistema"
    description="Comprueba la comunicación entre el panel de operaciones y el backend de REDXela."
  >
    <button type="button" class="btn btn-primary" :disabled="loading" @click="checkConnection">
      <AppIcon name="refresh" :class="{ 'animate-spin': loading }" :size="16" />{{
        loading ? 'Comprobando…' : 'Comprobar conexión'
      }}
    </button>
  </PageHeading>
  <div class="metric-grid">
    <MetricCard
      label="ESTADO DEL SERVICIO"
      :value="loading ? 'Consultando' : health ? health.status : 'Sin conexión'"
      detail="Resultado de la última comprobación"
      icon="activity"
    />
    <MetricCard
      label="APLICACIÓN"
      :value="health?.application || '—'"
      detail="Identidad reportada por el backend"
      icon="box"
    />
    <MetricCard
      label="ÚLTIMA RESPUESTA · UTC"
      :value="health ? health.timestamp.slice(11, 19) : '—'"
      :detail="health ? health.timestamp.slice(0, 10) : 'Aún sin respuesta del servicio'"
      icon="clock"
    />
    <MetricCard
      label="CANAL DE COMUNICACIÓN"
      value="REST"
      detail="Fetch · respuesta JSON"
      icon="connection"
    />
  </div>
  <div v-if="error" class="notice notice-danger" role="alert">
    <AppIcon name="alert" />
    <div>
      <p class="font-semibold">No se pudo conectar con el servicio</p>
      <p>{{ error }}</p>
      <p class="mt-1 text-xs">Vuelve a comprobar la conexión cuando el backend esté disponible.</p>
    </div>
  </div>
  <div class="grid items-stretch gap-5 xl:grid-cols-[1.7fr_1fr]">
    <UiPanel
      title="Conexión de servicios"
      description="Un panel independiente. Una fuente de verdad en Java."
    >
      <template #actions
        ><StatusBadge
          :tone="health ? 'success' : error ? 'danger' : 'neutral'"
          :icon="health ? 'check' : 'connection'"
          >{{
            loading ? 'Comprobando' : health ? 'Comunicación disponible' : 'Sin respuesta'
          }}</StatusBadge
        ></template
      >
      <div class="connection-canvas px-5 py-10 sm:py-14">
        <div class="flex items-center justify-center gap-3 sm:gap-6">
          <div class="service-node">
            <span class="service-icon"><AppIcon name="grid" :size="28" /></span>
            <h3>Panel REDXela</h3>
            <p>Vue · Navegador</p>
          </div>
          <div class="flex min-w-8 flex-1 flex-col items-center gap-2 sm:max-w-32">
            <span class="text-[10px] font-medium text-muted">GET /api/health</span>
            <div class="flex w-full items-center text-primary">
              <span class="h-px flex-1 bg-primary/40" /><AppIcon name="arrow" :size="20" />
            </div>
            <span class="text-[10px] text-muted">HTTP · JSON</span>
          </div>
          <div class="service-node">
            <span class="service-icon"><AppIcon name="server" :size="28" /></span>
            <h3>Spring Boot</h3>
            <p>Java · Backend</p>
          </div>
        </div>
        <div
          class="mx-auto mt-9 max-w-md rounded-lg border border-border bg-surface px-4 py-3 text-center text-xs text-secondary"
          role="status"
          aria-live="polite"
        >
          <p v-if="loading">Comprobando conexión…</p>
          <p v-else-if="health" data-testid="health" class="flex items-center justify-center gap-2">
            <AppIcon name="check" :size="15" class="text-primary" />{{ health.application }} ·
            {{ health.status }}
          </p>
          <p v-else>Sin respuesta del servicio. No hay datos operativos que mostrar.</p>
        </div>
      </div>
      <div
        class="flex items-start gap-3 border-t border-border bg-surface-muted/50 px-5 py-4 text-xs leading-relaxed text-secondary"
      >
        <AppIcon name="info" :size="16" class="mt-0.5" />
        <p>
          Esta comprobación confirma conectividad. El estado de los pedidos, inventario y recursos
          se consulta en sus respectivas secciones.
        </p>
      </div>
    </UiPanel>
    <UiPanel title="Detalle de la comprobación" eyebrow="SERVICIO DE INTEGRACIÓN"
      ><dl class="divide-y divide-border px-5">
        <div class="flex justify-between gap-4 py-4">
          <dt class="text-secondary">Endpoint</dt>
          <dd class="font-mono text-xs">/api/health</dd>
        </div>
        <div class="flex justify-between gap-4 py-4">
          <dt class="text-secondary">Método</dt>
          <dd><StatusBadge tone="success">GET</StatusBadge></dd>
        </div>
        <div class="flex justify-between gap-4 py-4">
          <dt class="text-secondary">Estado</dt>
          <dd>{{ loading ? 'Consultando…' : health?.status || 'Sin conexión' }}</dd>
        </div>
        <div class="py-4">
          <dt class="text-secondary">Instante de respuesta UTC</dt>
          <dd class="mt-2 break-all font-mono text-xs leading-relaxed">
            {{ health?.timestamp || 'Aún no disponible' }}
          </dd>
        </div>
      </dl>
      <div class="border-t border-border px-5 py-4">
        <button
          id="integration-toggle"
          type="button"
          class="flex w-full items-center justify-between gap-2 text-left text-xs font-medium"
          :aria-expanded="detailsOpen"
          aria-controls="integration-content"
          @click="detailsOpen = !detailsOpen"
        >
          Información de conexión<AppIcon
            name="chevron"
            :size="14"
            :class="{ 'rotate-90': detailsOpen }"
          />
        </button>
        <p
          v-if="detailsOpen"
          id="integration-content"
          class="mt-3 text-xs leading-relaxed text-secondary"
        >
          Vue consulta /api/health mediante Fetch. En desarrollo, el proxy de Vite dirige /api al
          backend Java. No se sustituye una respuesta fallida por datos de demostración.
        </p>
      </div></UiPanel
    >
  </div>
  <div class="grid gap-4 md:grid-cols-3">
    <RouterLink
      v-for="link in [
        {
          to: '/orders',
          icon: 'orders',
          title: 'Pedidos y generador',
          text: 'Registra pedidos y controla las llegadas.',
        },
        {
          to: '/resources',
          icon: 'resources',
          title: 'Recursos compartidos',
          text: 'Consulta disponibilidad, titulares y espera.',
        },
        {
          to: '/scheduling',
          icon: 'chart',
          title: 'Planificación',
          text: 'Explora las políticas y compara resultados.',
        },
      ]"
      :key="link.to"
      :to="link.to"
      class="panel group flex items-center gap-4 p-5 transition-colors hover:border-primary/40"
      ><span class="metric-icon rounded-lg"><AppIcon :name="link.icon" /></span>
      <div class="min-w-0 flex-1">
        <h2 class="text-sm">{{ link.title }}</h2>
        <p class="mt-1 text-xs leading-relaxed text-secondary">{{ link.text }}</p>
      </div>
      <AppIcon name="arrow" :size="17" class="text-muted group-hover:text-primary"
    /></RouterLink>
  </div>
</template>
