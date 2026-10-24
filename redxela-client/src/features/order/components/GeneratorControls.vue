<script setup>
import { ref } from 'vue'
import { controlGenerator } from '../services/orders'
import AppIcon from '@/components/ui/AppIcon.vue'
import UiPanel from '@/components/ui/UiPanel.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
const actionIcons = { start: 'play', pause: 'pause', resume: 'play', stop: 'stop' }
defineProps({ status: { type: Object, required: true } })
const emit = defineEmits(['changed'])
const interval = ref(60)
const seed = ref(42)
const pending = ref(false)
const error = ref('')
const states = {
  STOPPED: 'Detenido',
  RUNNING: 'Activo',
  PAUSED: 'Pausado',
  ERROR: 'Error',
  CLOSED: 'Cerrado',
}
async function command(action) {
  pending.value = true
  error.value = ''
  try {
    await controlGenerator(
      action,
      action === 'start'
        ? { intervalSimulatedSeconds: interval.value, seed: seed.value, minUnits: 1, maxUnits: 5 }
        : undefined,
    )
    emit('changed')
  } catch (cause) {
    error.value = cause.message
  } finally {
    pending.value = false
  }
}
</script>
<template>
  <UiPanel title="Generador automático" description="Llegadas continuas de todos los niveles.">
    <template #actions
      ><StatusBadge
        :tone="
          status.state === 'RUNNING' ? 'success' : status.state === 'ERROR' ? 'danger' : 'neutral'
        "
        :icon="status.state === 'RUNNING' ? 'activity' : 'clock'"
        >{{ states[status.state] }}</StatusBadge
      ></template
    >
    <div class="panel-body space-y-5">
      <div
        class="flex items-center justify-between gap-3 rounded-lg border border-border bg-surface-muted/50 p-4"
      >
        <div>
          <p class="eyebrow">PEDIDOS GENERADOS</p>
          <p class="mt-2 text-3xl font-semibold tracking-tight tabular-nums">
            {{ status.generatedOrders }}
          </p>
        </div>
        <span class="metric-icon size-12"><AppIcon name="refresh" :size="23" /></span>
      </div>
      <p class="sr-only" data-testid="generator-status">
        {{ states[status.state] }} · {{ status.generatedOrders }} generados ·
        {{ status.totalOrders }} pedidos registrados
      </p>
      <div class="grid grid-cols-2 gap-3">
        <label class="field"
          >Intervalo base (segundos simulados)<input
            v-model.number="interval"
            type="number"
            min="0.1"
            max="3600"
            step="0.1"
            class="input"
            :disabled="status.state === 'RUNNING' || status.state === 'PAUSED'" /></label
        ><label class="field"
          >Semilla<input
            v-model.number="seed"
            type="number"
            step="1"
            class="input"
            :disabled="status.state === 'RUNNING' || status.state === 'PAUSED'"
        /></label>
      </div>
      <div class="grid grid-cols-2 gap-2" :aria-busy="pending">
        <button
          v-for="action in [
            { code: 'start', text: 'Iniciar', allowed: ['STOPPED', 'ERROR'] },
            { code: 'pause', text: 'Pausar', allowed: ['RUNNING'] },
            { code: 'resume', text: 'Reanudar', allowed: ['PAUSED'] },
            { code: 'stop', text: 'Detener', allowed: ['RUNNING', 'PAUSED', 'ERROR'] },
          ]"
          :key="action.code"
          type="button"
          class="btn"
          :class="action.code === 'start' ? 'btn-primary' : ''"
          :disabled="pending || !action.allowed.includes(status.state)"
          @click="command(action.code)"
        >
          <AppIcon :name="actionIcons[action.code]" :size="14" />{{ action.text }}
        </button>
      </div>
      <div class="space-y-2 border-t border-border pt-4 text-xs text-secondary">
        <p class="flex items-start gap-2">
          <AppIcon name="clock" :size="15" /><span
            >1 segundo real =
            <strong class="font-medium text-main"
              >{{ status.timeScale }} segundos simulados</strong
            ></span
          >
        </p>
        <p class="leading-relaxed">
          Las llegadas varían entre 50 % y 150 % del intervalo, con mínimo de 20 ms reales.
        </p>
        <p>{{ status.totalOrders }} pedidos registrados en esta ejecución</p>
      </div>
      <p v-if="status.throttled" class="notice notice-warning text-xs">
        Llegadas ralentizadas por presión de memoria. Se conservan los pedidos admitidos.
      </p>
      <p v-if="error || status.lastError" role="alert" class="notice notice-danger text-xs">
        {{ error || status.lastError }}
      </p>
    </div>
  </UiPanel>
</template>
