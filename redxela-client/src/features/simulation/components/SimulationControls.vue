<script setup>
import { ref } from 'vue'
import { controlSimulation } from '../services/simulation'
import UiPanel from '@/components/ui/UiPanel.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import AppIcon from '@/components/ui/AppIcon.vue'
defineProps({ status: { type: Object, required: true } })
const emit = defineEmits(['changed'])
const pending = ref(false)
const error = ref('')
const states = {
  RUNNING: 'Procesando pedidos',
  STOPPED: 'Motor detenido',
  STOPPING: 'Liberando operaciones',
}
async function command(value) {
  pending.value = true
  error.value = ''
  try {
    await controlSimulation(value)
    emit('changed')
  } catch (cause) {
    error.value = cause.message
  } finally {
    pending.value = false
  }
}
</script>
<template>
  <UiPanel
    title="Motor de procesamiento"
    description="Trabajadores Java independientes del generador y del navegador."
  >
    <template #actions
      ><StatusBadge :tone="status.state === 'RUNNING' ? 'success' : 'neutral'">{{
        states[status.state]
      }}</StatusBadge></template
    >
    <div class="flex flex-wrap items-center justify-between gap-4 p-5">
      <div class="text-sm text-secondary">
        <p>
          {{ status.workerCount }} trabajadores · {{ status.timeScale }} segundos simulados /
          segundo real
        </p>
        <p class="mt-2 text-xs">
          Detener cancela el trabajo activo y devuelve sus reservas. Los pedidos pendientes se
          conservan. El generador se controla por separado.
        </p>
      </div>
      <div class="flex gap-2">
        <button
          class="btn btn-primary"
          :disabled="pending || status.state !== 'STOPPED'"
          @click="command('start')"
        >
          <AppIcon name="play" :size="16" />Iniciar motor</button
        ><button
          class="btn btn-secondary"
          :disabled="pending || status.state !== 'RUNNING'"
          @click="command('stop')"
        >
          <AppIcon name="stop" :size="16" />Detener motor
        </button>
      </div>
    </div>
    <p v-if="error" role="alert" class="notice notice-danger mx-5 mb-5">{{ error }}</p>
  </UiPanel>
</template>
