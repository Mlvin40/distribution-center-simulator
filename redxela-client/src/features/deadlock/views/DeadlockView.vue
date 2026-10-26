<script setup>
import { computed, ref } from 'vue'
import { useOperations } from '@/composables/useOperations'
import { startDeadlock, resolveDeadlock } from '../services/deadlock'
import PageHeading from '@/components/ui/PageHeading.vue'
import UiPanel from '@/components/ui/UiPanel.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import ViewState from '@/components/ui/ViewState.vue'
import AppIcon from '@/components/ui/AppIcon.vue'
const { data: live, error, loading, refresh } = useOperations()
const data = computed(() => live.value?.deadlock)
const pending = ref(false)
const actionError = ref('')
const states = {
  IDLE: 'Sin conflicto',
  PREPARING: 'Preparando operaciones',
  DEADLOCK: 'Espera circular detectada',
  RESOLVING: 'Recuperación en curso',
  COMPLETED: 'Sistema recuperado',
  HOLDING_FIRST: 'Retiene el primer recurso',
  WAITING_SECOND: 'Espera el segundo recurso',
  RUNNING: 'Continuando',
  WAITING: 'De vuelta en espera',
  RETRYING: 'Atendida después',
  FAILED: 'Interrumpida',
}
async function action(operation) {
  pending.value = true
  actionError.value = ''
  try {
    await operation()
    await refresh()
  } catch (cause) {
    actionError.value = cause.message
  } finally {
    pending.value = false
  }
}
</script>
<template>
  <PageHeading
    eyebrow="SINCRONIZACIÓN Y RECUPERACIÓN"
    title="Conflicto de recursos"
    description="Provoca una espera circular real y decide qué operación continúa."
  >
    <StatusBadge
      v-if="data && !error"
      :tone="data.detected ? 'danger' : data.state === 'COMPLETED' ? 'success' : 'neutral'"
      icon="alert"
      >{{ states[data.state] }}</StatusBadge
    >
  </PageHeading>
  <ViewState
    v-if="loading || error"
    :loading="loading"
    :error="error"
    title="Consultando conflicto…"
  />
  <template v-if="data && !error">
    <UiPanel
      title="Experimento controlado"
      description="Dos hilos Java disputan SCANNER-01 y FORKLIFT-01 en orden inverso."
    >
      <div class="flex flex-wrap items-center justify-between gap-4 p-5">
        <p class="max-w-3xl text-sm leading-relaxed text-secondary">
          El mecanismo normal evita retener recursos mientras solicita otros. Este experimento
          permite esa condición deliberadamente, usando los mismos recursos físicos del centro.
          Recepción y despacho pueden esperar mientras se resuelve.
        </p>
        <button
          class="btn btn-primary"
          :disabled="pending || !['IDLE', 'COMPLETED'].includes(data.state)"
          @click="action(startDeadlock)"
        >
          <AppIcon name="play" :size="16" />Provocar conflicto
        </button>
      </div>
      <p v-if="actionError || data.error" role="alert" class="notice notice-danger mx-5 mb-5">
        {{ actionError || data.error }}
      </p>
    </UiPanel>
    <div v-if="data.detected" class="notice notice-danger" role="alert">
      <strong>Espera circular real.</strong> A retiene el escáner y espera el montacargas; B retiene
      el montacargas y espera el escáner. Ninguna puede continuar hasta que elijas una operación.
    </div>
    <p v-if="data.waitingOperations?.length" role="status" class="notice">
      Cola de operaciones postergadas: {{ data.waitingOperations.join(', ') }}
    </p>
    <div v-if="data.operations.length" class="grid gap-5 lg:grid-cols-2">
      <UiPanel
        v-for="operation in data.operations"
        :key="operation.id"
        :title="operation.name"
        :description="operation.id"
      >
        <template #actions
          ><StatusBadge
            :tone="
              data.winner === operation.id ? 'success' : operation.waiting ? 'danger' : 'neutral'
            "
            >{{ states[operation.state] || operation.state }}</StatusBadge
          ></template
        >
        <div class="space-y-5 p-5">
          <div class="grid gap-4 sm:grid-cols-2">
            <div class="rounded-xl border border-border bg-primary-soft/40 p-4">
              <p class="eyebrow">RECURSOS RETENIDOS</p>
              <p class="mt-3 font-mono text-sm">{{ operation.held.join(', ') || 'Ninguno' }}</p>
            </div>
            <div class="rounded-xl border border-border bg-surface-muted p-4">
              <p class="eyebrow">RECURSO SOLICITADO</p>
              <p class="mt-3 font-mono text-sm">{{ operation.requested || 'Ninguno' }}</p>
            </div>
          </div>
          <button
            v-if="data.detected"
            class="btn btn-primary w-full"
            :disabled="pending"
            @click="action(() => resolveDeadlock(operation.id))"
          >
            Continuar {{ operation.name.includes(' A ') ? 'operación A' : 'operación B'
            }}<AppIcon name="arrow" :size="16" />
          </button>
          <p v-if="data.winner === operation.id" class="text-sm text-primary-ink">
            Operación elegida manualmente para continuar primero.
          </p>
        </div>
      </UiPanel>
    </div>
    <UiPanel
      v-if="data.history.length"
      title="Registro de recuperación"
      description="Transiciones emitidas por los trabajadores reales."
    >
      <ol class="max-h-80 space-y-2 overflow-y-auto p-5">
        <li v-for="(entry, index) in data.history" :key="index" class="flex gap-3 text-xs">
          <span class="text-muted tabular-nums">{{ index + 1 }}</span
          ><span class="id-text">{{ entry }}</span>
        </li>
      </ol>
      <p
        v-if="data.state === 'COMPLETED'"
        role="status"
        class="border-t border-border p-5 text-sm text-primary-ink"
      >
        Ambas operaciones terminaron. La operación postergada volvió a espera y fue atendida
        después; los recursos están liberados.
      </p>
    </UiPanel>
    <ViewState
      v-else-if="data.state === 'IDLE'"
      title="Listo para demostrar el conflicto"
      description="El escenario será detectado a partir de posesión y solicitudes reales, sin alterar pedidos ni existencias."
      icon="shield"
    />
  </template>
</template>
