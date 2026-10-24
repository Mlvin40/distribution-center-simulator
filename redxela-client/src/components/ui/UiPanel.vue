<script setup>
defineProps({ title: String, description: String, eyebrow: String })
</script>
<template>
  <section class="panel">
    <header v-if="title || $slots.actions" class="panel-header">
      <div class="min-w-0">
        <p v-if="eyebrow" class="eyebrow mb-1.5">{{ eyebrow }}</p>
        <h2 v-if="title">{{ title }}</h2>
        <p v-if="description" class="mt-1 text-sm leading-relaxed text-secondary">
          {{ description }}
        </p>
      </div>
      <div v-if="$slots.actions" class="flex flex-wrap items-center gap-2">
        <slot name="actions" />
      </div>
    </header>
    <slot />
  </section>
</template>
