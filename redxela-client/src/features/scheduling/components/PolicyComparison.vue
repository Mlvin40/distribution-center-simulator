<script setup>
import { computed, ref } from 'vue'
import { comparePolicies } from '../services/scheduling'
import AppIcon from '@/components/ui/AppIcon.vue'
import UiPanel from '@/components/ui/UiPanel.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import ViewState from '@/components/ui/ViewState.vue'
import AreaChart from '@/components/ui/AreaChart.vue'
import { policyNames, resourceNames } from '@/utils/display'
const displayedPolicy = ref('STRICT_PRIORITY')
const displayed = computed(() =>
  report.value?.results.find((result) => result.policy === displayedPolicy.value),
)
const points = computed(
  () =>
    displayed.value?.executions.map((execution) => ({
      label: execution.candidate.id,
      value: Math.max(0, execution.start - execution.candidate.arrivalSimulatedSeconds),
    })) || [],
)
const report = ref(null)
const error = ref('')
const pending = ref(false)
const seed = ref(42)
const count = ref(30)
async function compare() {
  pending.value = true
  error.value = ''
  try {
    report.value = await comparePolicies({ seed: seed.value, orderCount: count.value })
  } catch (cause) {
    error.value = cause.message
    report.value = null
  } finally {
    pending.value = false
  }
}
</script>
<template>
  <UiPanel
    title="Comparación de políticas"
    description="La misma carga. Las mismas capacidades. Tres criterios de atención."
    eyebrow="LABORATORIO DE PLANIFICACIÓN"
  >
    <template #actions
      ><StatusBadge tone="warning" icon="chart">Estimación · modelo aislado</StatusBadge></template
    >
    <div class="border-b border-border bg-surface-muted/40 px-5 py-4">
      <form class="flex flex-wrap items-end gap-3" @submit.prevent="compare">
        <label class="field w-full sm:w-36"
          >Semilla de comparación<input
            v-model.number="seed"
            type="number"
            step="1"
            required
            class="input" /></label
        ><label class="field w-full sm:w-44"
          >Entradas del experimento<input
            v-model.number="count"
            type="number"
            min="1"
            max="200"
            step="1"
            required
            class="input" /></label
        ><button :disabled="pending" class="btn btn-primary" :aria-busy="pending">
          <AppIcon
            :name="pending ? 'refresh' : 'play'"
            :class="{ 'animate-spin': pending }"
            :size="15"
          />Comparar políticas
        </button>
        <p class="ml-auto max-w-sm text-[11px] leading-relaxed text-secondary">
          Modelo analítico con existencias suficientes y trabajo proporcional a unidades. Sus
          resultados no completan pedidos reales.
        </p>
      </form>
    </div>
    <div v-if="error" role="alert" class="notice notice-danger m-5">
      <AppIcon name="alert" :size="17" />{{ error }}
    </div>
    <ViewState
      v-if="!report && !pending"
      title="Aún no hay un experimento"
      description="Elige una semilla y ejecuta la comparación. Aquí verás los tiempos calculados para cada política, sin modificar la operación real."
      icon="chart"
    />
    <ViewState
      v-if="pending"
      loading
      title="Calculando resultados comparables…"
      class="m-5 border-0 shadow-none"
    />
    <template v-if="report && !pending">
      <div class="flex flex-wrap items-center justify-between gap-4 px-5 pt-5">
        <div>
          <p class="eyebrow">ESPERA MEDIA ESTIMADA</p>
          <p class="mt-1 text-3xl font-semibold tracking-tight tabular-nums">
            {{ displayed?.averageWaitSimulatedSeconds.toFixed(1) }}
            <span class="text-sm font-normal text-muted">s simulados</span>
          </p>
        </div>
        <div
          class="flex max-w-full flex-wrap rounded-lg border border-border bg-surface-muted p-1"
          aria-label="Política visualizada en la gráfica"
        >
          <button
            v-for="result in report.results"
            :key="result.policy"
            type="button"
            class="rounded-md px-3 py-2 text-xs transition-colors"
            :class="
              displayedPolicy === result.policy
                ? 'bg-surface font-medium text-primary-ink shadow-xs'
                : 'text-secondary hover:text-main'
            "
            :aria-pressed="displayedPolicy === result.policy"
            @click="displayedPolicy = result.policy"
          >
            {{ policyNames[result.policy] }}
          </button>
        </div>
      </div>
      <AreaChart
        :points="points"
        :title="`Espera por orden de atención · ${policyNames[displayedPolicy]}`"
      />
      <p
        class="border-t border-border bg-surface-muted/40 px-5 py-3 text-[11px] leading-relaxed text-secondary"
      >
        {{ report.assumptions }}
      </p>
      <div class="table-scroll" tabindex="0" aria-label="Desplazar resultados de comparación">
        <table class="data-table" data-testid="comparison-results">
          <caption class="sr-only">
            Resultados del modelo aislado, tiempos en segundos simulados
          </caption>
          <thead>
            <tr>
              <th scope="col">Política</th>
              <th scope="col">Completados del modelo</th>
              <th scope="col">Espera media (s sim.)</th>
              <th scope="col">Proceso medio (s sim.)</th>
              <th scope="col">Objetivos incumplidos</th>
              <th scope="col">Ocupación por recurso</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="result in report.results" :key="result.policy">
              <td class="font-medium" :title="result.policy">{{ policyNames[result.policy] }}</td>
              <td class="tabular-nums">{{ result.modeledCompletedOrders }}</td>
              <td class="font-medium tabular-nums text-primary-ink">
                {{ result.averageWaitSimulatedSeconds.toFixed(1) }}
              </td>
              <td class="tabular-nums">
                {{ result.averageProcessingSimulatedSeconds.toFixed(1) }}
              </td>
              <td>
                <p
                  v-for="(missed, level) in result.missedTargetsByLevel"
                  :key="level"
                  class="whitespace-nowrap text-xs leading-6 text-secondary"
                >
                  Nivel {{ level }}:
                  <span :class="missed ? 'font-medium text-amber-800' : ''">{{ missed }}</span>
                </p>
              </td>
              <td>
                <div
                  v-for="(fraction, resource) in result.resourceUtilization"
                  :key="resource"
                  class="mb-1.5 flex min-w-36 items-center justify-between gap-3 text-[11px] text-secondary"
                >
                  <span :title="resource">{{ resourceNames[resource] || resource }}</span
                  ><span class="tabular-nums">{{ (fraction * 100).toFixed(1) }} %</span>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </UiPanel>
</template>
