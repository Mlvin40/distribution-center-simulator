<script setup>
import { RouterLink } from 'vue-router'
import AppIcon from '../ui/AppIcon.vue'
defineProps({ collapsed: Boolean })
const links = [
  { to: '/dashboard', label: 'Dashboard', icon: 'grid', group: 'MONITOREO' },
  { to: '/', label: 'Conexión', icon: 'connection' },
  { to: '/resources', label: 'Recursos compartidos', icon: 'resources' },
  { to: '/orders', label: 'Pedidos y generador', icon: 'orders', group: 'OPERACIÓN' },
  { to: '/inventory', label: 'Inventario y recepción', icon: 'truck' },
  { to: '/storage', label: 'Almacén', icon: 'box' },
  { to: '/scheduling', label: 'Planificación', icon: 'chart' },
  { to: '/deadlock', label: 'Conflicto de recursos', icon: 'alert' },
]
</script>
<template>
  <nav aria-label="Navegación principal" class="sidebar-nav">
    <template v-for="link in links" :key="link.to"
      ><p v-if="link.group" class="nav-group" :class="{ 'sr-only': collapsed }">{{ link.group }}</p>
      <RouterLink
        :to="link.to"
        class="nav-item"
        active-class="nav-active"
        :title="collapsed ? link.label : undefined"
        ><AppIcon :name="link.icon" :size="19" /><span :class="{ 'sr-only': collapsed }">{{
          link.label
        }}</span
        ><AppIcon
          v-if="!collapsed"
          name="chevron"
          :size="14"
          class="nav-chevron ml-auto" /></RouterLink
    ></template>
  </nav>
</template>
