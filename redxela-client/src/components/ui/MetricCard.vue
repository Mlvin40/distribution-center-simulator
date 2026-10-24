<script setup>
import AppIcon from './AppIcon.vue'
defineProps({
  label: String,
  value: [String, Number],
  detail: String,
  icon: { type: String, default: 'chart' },
})
</script>
<template>
  <article class="metric-card">
    <span class="metric-icon"><AppIcon :name="icon" :size="19" /></span>
    <p class="eyebrow mt-3">{{ label }}</p>
    <div class="mt-1 flex flex-wrap items-center gap-2">
      <p class="metric-value">{{ value }}</p>
      <slot />
    </div>
    <p v-if="detail" class="mt-1 text-xs text-muted">{{ detail }}</p>
  </article>
</template>
