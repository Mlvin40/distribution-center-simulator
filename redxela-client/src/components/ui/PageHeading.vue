<script setup>
defineProps({ title: String, description: String, eyebrow: String })
</script>
<template>
  <header class="page-heading">
    <div class="min-w-0">
      <p v-if="eyebrow" class="eyebrow mb-1.5">{{ eyebrow }}</p>
      <h1>{{ title }}</h1>
      <p v-if="description" class="mt-2 max-w-3xl text-sm leading-relaxed text-secondary">
        {{ description }}
      </p>
    </div>
    <div v-if="$slots.default" class="flex shrink-0 flex-wrap items-center gap-2"><slot /></div>
  </header>
</template>
