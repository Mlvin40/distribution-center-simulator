<script setup>
import { ref } from 'vue'
import { usePolling } from '@/composables/usePolling'
import { getReception, registerArrival } from '../services/inventory'
import UiPanel from '@/components/ui/UiPanel.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import ViewState from '@/components/ui/ViewState.vue'
defineProps({ products: { type: Array, required: true } })
const { data, error, loading, refresh } = usePolling(getReception)
const productId = ref('PRD-001')
const quantity = ref(20)
const pending = ref(false)
const message = ref('')
const actionError = ref('')
let retryId
const names = {
  RECEIVED: 'Recibida',
  SCANNING: 'Escaneo',
  QUEUED: 'En cola',
  PROCESSING: 'Registrando',
  WAITING_CAPACITY: 'Espera de espacio',
  RETRYING: 'Reintento',
  COMPLETED: 'Almacenada',
}
async function submit() {
  pending.value = true
  actionError.value = ''
  message.value = ''
  retryId ||= crypto.randomUUID()
  try {
    const arrival = await registerArrival({
      arrivalId: retryId,
      productId: productId.value,
      quantity: Number(quantity.value),
    })
    message.value = `Llegada ${arrival.arrivalId} aceptada. Inventario la procesará automáticamente.`
    retryId = null
    await refresh()
  } catch (cause) {
    actionError.value = cause.message
  } finally {
    pending.value = false
  }
}
</script>
<template>
  <UiPanel
    title="Recepción de mercancía"
    description="El escáner y una cola de mensajes conectan Recepción con Inventario."
  >
    <template #actions
      ><StatusBadge v-if="data"
        >{{ data.completed }} / {{ data.registered }} entradas almacenadas</StatusBadge
      ></template
    >
    <form class="flex flex-wrap items-end gap-3 p-5" @submit.prevent="submit">
      <label class="field min-w-40 flex-1"
        >Producto<select
          v-model="productId"
          class="input"
          :disabled="pending"
          @change="retryId = null"
        >
          <option v-for="product in products" :key="product.id" :value="product.id">
            {{ product.name }}
          </option>
        </select></label
      >
      <label class="field"
        >Unidades<input
          v-model="quantity"
          class="input"
          type="number"
          min="1"
          max="600"
          step="1"
          required
          :disabled="pending"
          @input="retryId = null"
      /></label>
      <button class="btn btn-primary" :disabled="pending">
        {{ pending ? 'Registrando…' : 'Registrar llegada' }}
      </button>
    </form>
    <p v-if="actionError" role="alert" class="notice notice-danger mx-5 mb-4">{{ actionError }}</p>
    <p v-if="message" role="status" class="notice mx-5 mb-4">{{ message }}</p>
    <ViewState
      v-if="loading || error"
      :loading="loading"
      :error="error"
      title="Consultando Recepción…"
    />
    <template v-if="data && !error">
      <p class="border-t border-border px-5 py-3 text-xs text-secondary">
        Pendientes en Recepción: {{ data.pendingReception }} · Mensajes para Inventario:
        {{ data.pendingInventory }}. Las entradas sin espacio se conservan y reintentan.
      </p>
      <ViewState
        v-if="!data.arrivals.length"
        title="Sin llegadas registradas"
        description="Selecciona un producto para abastecer el almacén."
      />
      <div v-else class="table-scroll" tabindex="0" aria-label="Llegadas de mercancía">
        <table class="data-table">
          <thead>
            <tr>
              <th>Lote / llegada</th>
              <th>Producto</th>
              <th>Unidades</th>
              <th>Estado</th>
              <th>Detalle</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="arrival in data.arrivals" :key="arrival.arrivalId">
              <td class="id-text">{{ arrival.arrivalId }}</td>
              <td>{{ arrival.productId }}</td>
              <td>{{ arrival.quantity }}</td>
              <td>
                <StatusBadge :tone="arrival.status === 'COMPLETED' ? 'success' : 'warning'">{{
                  names[arrival.status]
                }}</StatusBadge>
              </td>
              <td class="min-w-48">
                {{ arrival.message
                }}<span v-if="arrival.receipt">
                  · {{ arrival.receipt.reactivatedOrders.length }} pedidos reactivados</span
                >
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </UiPanel>
</template>
