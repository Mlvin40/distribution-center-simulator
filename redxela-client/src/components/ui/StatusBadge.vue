<script setup>
import AppIcon from './AppIcon.vue'
defineProps({ tone: { type: String, default: 'neutral' }, icon: { type: String, default: '' } })
</script>
<template>
  <span class="status-badge" :class="`badge-${tone}`"
    ><AppIcon v-if="icon" :name="icon" :size="13" /><span
      v-else
      class="size-1.5 shrink-0 rounded-full bg-current" /><slot
  /></span>
</template>
