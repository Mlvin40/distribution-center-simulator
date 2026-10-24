<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import AppIcon from '../ui/AppIcon.vue'
import SidebarNavigation from './SidebarNavigation.vue'
import AppearanceMenu from './AppearanceMenu.vue'
const route = useRoute()
const collapsed = ref(false)
const drawer = ref(null)
const dark = ref(false)
const sections = {
  dashboard: ['Monitoreo', 'Dashboard'],
  inventory: ['Operación', 'Inventario y recepción'],
  storage: ['Operación', 'Almacén'],
  deadlock: ['Operación', 'Conflicto de recursos'],
  integration: ['Monitoreo', 'Conexión'],
  resources: ['Monitoreo', 'Recursos compartidos'],
  orders: ['Operación', 'Pedidos y generador'],
  scheduling: ['Operación', 'Planificación'],
}
const section = computed(() => sections[route.name] || ['REDXela', 'Centro de distribución'])
function setAppearance(value) {
  dark.value = value
  document.documentElement.dataset.appearance = dark.value ? 'dark' : 'light'
  try {
    localStorage.setItem('redxela-appearance', dark.value ? 'dark' : 'light')
  } catch {
    /* La preferencia visual es opcional si el almacenamiento no está disponible */
  }
}
function trapDrawerFocus(event) {
  if (event.key !== 'Tab') return
  const controls = [...drawer.value.querySelectorAll('a[href], button:not(:disabled)')]
  const first = controls[0]
  const last = controls.at(-1)
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last?.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first?.focus()
  }
}
function closeDrawer() {
  drawer.value?.close()
}
watch(
  () => route.path,
  () => {
    closeDrawer()
    document.title = `${section.value[1]} · REDXela`
  },
)
onMounted(() => {
  try {
    dark.value = localStorage.getItem('redxela-appearance') === 'dark'
  } catch {
    /* Se conserva la apariencia clara por defecto */
  }
  document.documentElement.dataset.appearance = dark.value ? 'dark' : 'light'
  document.title = `${section.value[1]} · REDXela`
})
</script>
<template>
  <div class="app-shell" :class="{ 'sidebar-collapsed': collapsed }">
    <a class="skip-link" href="#main-content">Saltar al contenido</a>
    <aside class="desktop-sidebar" aria-label="Barra lateral">
      <RouterLink to="/" class="brand" aria-label="REDXela · Conexión"
        ><span class="brand-mark"><AppIcon name="box" :size="24" /></span
        ><span v-if="!collapsed" class="brand-word"
          >RED<span>X</span>ela<small>Centro de distribución</small></span
        ></RouterLink
      >
      <SidebarNavigation :collapsed="collapsed" />
    </aside>
    <dialog
      ref="drawer"
      class="mobile-drawer"
      @keydown="trapDrawerFocus"
      aria-label="Menú de navegación"
      @click="
        (event) => {
          if (event.target === drawer) closeDrawer()
        }
      "
    >
      <div class="flex h-full flex-col">
        <div class="flex items-center justify-between border-b border-border p-5">
          <span class="brand-word">RED<span>X</span>ela<small>Centro de distribución</small></span
          ><button class="icon-button" type="button" aria-label="Cerrar menú" @click="closeDrawer">
            <AppIcon name="close" />
          </button>
        </div>
        <SidebarNavigation :collapsed="false" />
      </div>
    </dialog>
    <div class="app-workspace">
      <header class="topbar">
        <div class="flex min-w-0 items-center gap-3">
          <button
            type="button"
            class="icon-button hidden lg:inline-flex"
            :aria-label="collapsed ? 'Expandir barra lateral' : 'Contraer barra lateral'"
            :aria-expanded="!collapsed"
            @click="collapsed = !collapsed"
          >
            <AppIcon name="collapse" :size="19" /></button
          ><button
            type="button"
            class="icon-button lg:hidden"
            aria-label="Abrir menú"
            @click="drawer.showModal()"
          >
            <AppIcon name="menu" /></button
          ><span class="topbar-title">{{ section[1] }}</span>
        </div>
        <div class="flex items-center gap-3">
          <span class="hidden text-xs text-secondary sm:inline">Centro de distribución</span
          ><span class="topbar-divider" /><AppearanceMenu :dark="dark" @change="setAppearance" />
        </div>
      </header>
      <div class="context-bar">
        <span class="flex min-w-0 items-center gap-2 text-xs text-secondary"
          ><AppIcon name="box" :size="15" /><span>REDXela</span
          ><AppIcon name="chevron" :size="12" /><span>{{ section[0] }}</span
          ><AppIcon name="chevron" :size="12" /><span class="truncate text-main">{{
            section[1]
          }}</span></span
        ><span class="hidden items-center gap-1.5 text-xs text-muted sm:flex"
          ><AppIcon name="shield" :size="14" />Simulador de operaciones</span
        >
      </div>
      <main id="main-content" class="page-content" tabindex="-1"><RouterView /></main>
      <footer class="app-footer">
        <span>REDXela · Centro de distribución</span
        ><span>Los datos de la ejecución se conservan en memoria</span>
      </footer>
    </div>
  </div>
</template>
