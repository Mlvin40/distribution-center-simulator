<script setup>
import { computed, ref } from 'vue'
import { createCustomer, createOrder, getCustomer } from '../services/orders'
import AppIcon from '@/components/ui/AppIcon.vue'
import UiPanel from '@/components/ui/UiPanel.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import { serviceNames, merchandiseNames } from '@/utils/display'
const props = defineProps({
  customers: { type: Array, required: true },
  products: { type: Array, required: true },
  levels: { type: Array, required: true },
})
const emit = defineEmits(['changed'])
const customerId = ref('')
const name = ref('')
const searchId = ref('')
const level = ref(3)
const createdCustomer = ref(null)
const choices = computed(() =>
  createdCustomer.value
    ? [createdCustomer.value, ...props.customers.filter((c) => c.id !== createdCustomer.value.id)]
    : props.customers,
)
const lines = ref([{ key: 0, productId: '', quantity: 1 }])
let nextKey = 1
const pending = ref(false)
const error = ref('')
const message = ref('')
async function run(action) {
  pending.value = true
  error.value = ''
  message.value = ''
  try {
    await action()
    emit('changed')
  } catch (cause) {
    error.value = cause.message
  } finally {
    pending.value = false
  }
}
function registerCustomer() {
  return run(async () => {
    createdCustomer.value = await createCustomer({ name: name.value, serviceLevel: level.value })
    customerId.value = createdCustomer.value.id
    name.value = ''
    message.value = 'Cuenta registrada y seleccionada'
  })
}
function searchCustomer() {
  return run(async () => {
    createdCustomer.value = await getCustomer(searchId.value.trim())
    customerId.value = createdCustomer.value.id
    message.value = 'Cliente existente seleccionado'
  })
}
function submitOrder() {
  return run(async () => {
    const order = await createOrder({
      customerId: customerId.value,
      lines: lines.value.map(({ productId, quantity }) => ({ productId, quantity })),
    })
    message.value = `Pedido registrado: ${order.id}`
  })
}
</script>
<template>
  <UiPanel
    title="Registrar un pedido"
    description="Selecciona un cliente y los productos que necesita."
  >
    <template #actions><StatusBadge icon="orders">Ingreso manual</StatusBadge></template>
    <div class="panel-body space-y-5" :aria-busy="pending">
      <div class="rounded-lg border border-border bg-surface-muted/50 p-4">
        <h3 class="mb-3 flex items-center gap-2 text-xs font-semibold">
          <AppIcon name="users" :size="16" class="text-primary-ink" />¿Cliente nuevo? Crea su cuenta
        </h3>
        <form
          class="grid items-end gap-3 sm:grid-cols-[minmax(0,1fr)_minmax(0,.7fr)_auto]"
          @submit.prevent="registerCustomer"
        >
          <label class="field"
            >Nombre de cliente nuevo<input
              v-model="name"
              required
              maxlength="120"
              class="input"
              placeholder="Nombre del cliente" /></label
          ><label class="field"
            >Nivel de servicio<select v-model.number="level" class="input">
              <option v-for="service in levels" :key="service.level" :value="service.level">
                {{ service.level }} · {{ service.name }}
              </option>
            </select></label
          ><button :disabled="pending" class="btn">Crear cuenta</button>
        </form>
      </div>
      <div class="grid items-end gap-3 sm:grid-cols-[minmax(0,1fr)_auto]">
        <label class="field"
          >Cliente existente por UUID<input
            v-model="searchId"
            class="input font-mono text-xs"
            placeholder="Buscar una cuenta anterior por su identificador" /></label
        ><button
          type="button"
          :disabled="pending || !searchId.trim()"
          class="btn"
          @click="searchCustomer"
        >
          <AppIcon name="search" :size="15" />Buscar cliente
        </button>
      </div>
      <form class="space-y-4 border-t border-border pt-5" @submit.prevent="submitOrder">
        <label class="field"
          >Cliente<select v-model="customerId" required class="input">
            <option disabled value="">Seleccionar cliente (100 más recientes)</option>
            <option v-for="customer in choices" :key="customer.id" :value="customer.id">
              {{ customer.name }} ·
              {{ serviceNames[customer.serviceLevel] || customer.serviceLevel }} · {{ customer.id }}
            </option>
          </select></label
        >
        <fieldset class="space-y-3">
          <legend class="mb-3 text-xs font-semibold text-secondary">Productos del pedido</legend>
          <div
            v-for="(line, index) in lines"
            :key="line.key"
            class="grid grid-cols-[minmax(0,1fr)_76px_36px] items-end gap-2 sm:grid-cols-[minmax(0,1fr)_90px_36px]"
          >
            <label class="field"
              >Producto {{ index + 1
              }}<select v-model="line.productId" required class="input">
                <option disabled value="">Seleccionar producto</option>
                <option v-for="product in products" :key="product.id" :value="product.id">
                  {{ product.name }} ·
                  {{ merchandiseNames[product.merchandiseType] || product.merchandiseType }}
                </option>
              </select></label
            ><label class="field"
              >Cantidad<input
                v-model.number="line.quantity"
                type="number"
                required
                min="1"
                step="1"
                class="input" /></label
            ><button
              type="button"
              class="icon-button mb-0.5 border-border text-muted disabled:opacity-35"
              :disabled="lines.length === 1 || pending"
              :aria-label="`Quitar línea ${index + 1}`"
              :title="`Quitar línea ${index + 1}`"
              @click="lines.splice(index, 1)"
            >
              <AppIcon name="trash" :size="16" />
            </button>
          </div>
        </fieldset>
        <div class="flex flex-wrap items-center justify-between gap-3 pt-1">
          <button
            type="button"
            class="text-link min-h-10"
            @click="lines.push({ key: nextKey++, productId: '', quantity: 1 })"
          >
            <AppIcon name="plus" :size="16" />Agregar producto</button
          ><button :disabled="pending" class="btn btn-primary">
            <AppIcon
              :name="pending ? 'refresh' : 'plus'"
              :class="{ 'animate-spin': pending }"
              :size="16"
            />Registrar pedido
          </button>
        </div>
      </form>
      <p v-if="error" role="alert" class="notice notice-danger">
        <AppIcon name="alert" :size="17" />{{ error }}
      </p>
      <p v-if="message" role="status" class="notice notice-success break-all">
        <AppIcon name="check" :size="17" />{{ message }}
      </p>
    </div>
  </UiPanel>
</template>
