<script setup>
import { nextTick, onMounted, onUnmounted, ref, useId } from 'vue'
import AppIcon from '../ui/AppIcon.vue'
const props = defineProps({ dark: Boolean })
const emit = defineEmits(['change'])
const wrapper = ref(null)
const trigger = ref(null)
const menu = ref(null)
const open = ref(false)
const id = useId()
const options = [
  { dark: false, label: 'Apariencia clara', icon: 'sun' },
  { dark: true, label: 'Apariencia oscura', icon: 'moon' },
]
function close(restoreFocus = false) {
  open.value = false
  if (restoreFocus) trigger.value?.focus()
}
async function show(index = props.dark ? 1 : 0) {
  open.value = true
  await nextTick()
  menu.value?.querySelectorAll('[role="menuitemradio"]')[index]?.focus()
}
function onTriggerKeydown(event) {
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault()
    show(event.key === 'ArrowDown' ? 0 : options.length - 1)
  }
}
function onMenuKeydown(event) {
  if (event.key === 'Escape') {
    event.preventDefault()
    event.stopPropagation()
    close(true)
  } else if (event.key === 'Tab') {
    // Devolver el foco antes del Tab nativo permite continuar hacia el resto de la interfaz
    close(true)
  } else if (['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) {
    event.preventDefault()
    const items = [...menu.value.querySelectorAll('[role="menuitemradio"]')]
    const current = items.indexOf(document.activeElement)
    const next =
      event.key === 'Home'
        ? 0
        : event.key === 'End'
          ? items.length - 1
          : (current + (event.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length
    items[next]?.focus()
  }
}
function choose(value) {
  emit('change', value)
  close(true)
}
function onOutsidePointer(event) {
  if (!wrapper.value?.contains(event.target)) close()
}
function onFocusout(event) {
  if (!wrapper.value?.contains(event.relatedTarget)) close()
}
onMounted(() => document.addEventListener('pointerdown', onOutsidePointer))
onUnmounted(() => document.removeEventListener('pointerdown', onOutsidePointer))
</script>
<template>
  <div ref="wrapper" class="relative" @focusout="onFocusout">
    <button
      :id="`${id}-trigger`"
      ref="trigger"
      type="button"
      class="workspace-avatar border border-transparent transition-colors hover:border-primary/40 hover:bg-primary/15"
      aria-label="Abrir menú de apariencia"
      aria-haspopup="menu"
      :aria-expanded="open"
      :aria-controls="`${id}-menu`"
      @click="open ? close() : show()"
      @keydown="onTriggerKeydown"
    >
      RX
    </button>
    <div
      v-if="open"
      :id="`${id}-menu`"
      ref="menu"
      role="menu"
      :aria-labelledby="`${id}-trigger`"
      class="absolute right-0 top-full z-50 mt-3 w-60 max-w-[calc(100vw-2rem)] rounded-xl border border-border bg-surface p-1.5 text-main shadow-lg shadow-black/5"
      @keydown="onMenuKeydown"
    >
      <p class="eyebrow border-b border-border px-3 py-2.5">Apariencia</p>
      <button
        v-for="option in options"
        :key="option.label"
        type="button"
        role="menuitemradio"
        :aria-checked="dark === option.dark"
        tabindex="-1"
        class="mt-1 flex min-h-10 w-full items-center gap-2.5 rounded-lg px-3 py-2 text-left text-[13px] transition-colors"
        :class="
          dark === option.dark
            ? 'bg-primary-soft font-medium text-primary-ink'
            : 'text-secondary hover:bg-surface-muted hover:text-main'
        "
        @click="choose(option.dark)"
      >
        <AppIcon :name="option.icon" :size="17" />
        <span class="flex-1">{{ option.label }}</span>
        <AppIcon v-if="dark === option.dark" name="check" :size="16" />
      </button>
    </div>
  </div>
</template>
