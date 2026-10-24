import { nextTick } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import { HSStaticMethods } from 'preline/non-auto'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/dashboard',
      name: 'dashboard',
      component: () => import('@/features/simulation/views/DashboardView.vue'),
    },
    {
      path: '/inventory',
      name: 'inventory',
      component: () => import('@/features/inventory/views/InventoryView.vue'),
    },
    {
      path: '/storage',
      name: 'storage',
      component: () => import('@/features/storage/views/StorageView.vue'),
    },
    {
      path: '/deadlock',
      name: 'deadlock',
      component: () => import('@/features/deadlock/views/DeadlockView.vue'),
    },
    {
      path: '/scheduling',
      name: 'scheduling',
      component: () => import('@/features/scheduling/views/SchedulingView.vue'),
    },
    {
      path: '/orders',
      name: 'orders',
      component: () => import('@/features/order/views/OrdersView.vue'),
    },
    {
      path: '/',
      name: 'integration',
      component: () => import('@/features/integration/views/HealthView.vue'),
    },
    {
      path: '/resources',
      name: 'resources',
      component: () => import('@/features/resource/views/ResourcesView.vue'),
    },
  ],
})

router.afterEach(async (to, from, failure) => {
  if (failure) return

  // Espera el DOM actualizado antes de limpiar e inicializar los componentes de Preline
  await nextTick()
  HSStaticMethods.cleanCollection()
  HSStaticMethods.autoInit()
})

export default router
