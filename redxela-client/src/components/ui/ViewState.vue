<script setup>
import AppIcon from './AppIcon.vue'
defineProps({
  loading: Boolean,
  error: String,
  title: String,
  description: String,
  icon: { type: String, default: 'box' },
})
</script>
<template>
  <div v-if="loading" class="panel p-6" role="status" aria-live="polite">
    <p class="mb-5 flex items-center gap-2 text-sm text-secondary">
      <AppIcon name="refresh" class="animate-spin" :size="16" />{{
        title || 'Consultando información…'
      }}
    </p>
    <div class="grid gap-4 sm:grid-cols-3" aria-hidden="true">
      <div v-for="n in 3" :key="n" class="h-24 animate-pulse rounded-lg bg-surface-muted" />
    </div>
  </div>
  <div v-else-if="error" class="notice notice-danger" role="alert">
    <AppIcon name="alert" />
    <div>
      <p class="font-semibold">Sin conexión con el servicio</p>
      <p class="mt-1 text-sm">{{ error }}</p>
      <p class="mt-2 text-xs">
        Se reintentará automáticamente. No se muestran datos desactualizados.
      </p>
    </div>
  </div>
  <div v-else class="empty-state">
    <span class="empty-icon"><AppIcon :name="icon" :size="24" /></span>
    <p class="mt-3 font-medium">{{ title || 'Todavía no hay información' }}</p>
    <p v-if="description" class="mx-auto mt-1 max-w-md text-sm leading-relaxed text-secondary">
      {{ description }}
    </p>
    <slot />
  </div>
</template>
