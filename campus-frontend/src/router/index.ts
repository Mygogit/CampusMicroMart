import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('../views/Login.vue'),
      meta: { guest: true }
    },
    {
      path: '/register',
      name: 'Register',
      component: () => import('../views/Register.vue'),
      meta: { guest: true }
    },
    {
      path: '/',
      component: () => import('../components/Layout.vue'),
      children: [
        {
          path: '',
          name: 'Recommend',
          component: () => import('../views/Recommend.vue'),
          meta: { title: '推荐' }
        },
        {
          path: 'products',
          name: 'ProductList',
          component: () => import('../views/ProductList.vue'),
          meta: { title: '商品集市' }
        },
        {
          path: 'products/create',
          name: 'ProductCreate',
          component: () => import('../views/ProductCreate.vue'),
          meta: { requiresAuth: true }
        },
        {
          path: 'products/:id',
          name: 'ProductDetail',
          component: () => import('../views/ProductDetail.vue'),
          meta: { requiresAuth: true }
        },
        {
          path: 'products/mine',
          name: 'MyProducts',
          component: () => import('../views/MyProducts.vue'),
          meta: { requiresAuth: true }
        },
        {
          path: 'orders',
          name: 'OrderList',
          component: () => import('../views/OrderList.vue'),
          meta: { requiresAuth: true }
        },
        {
          path: 'orders/:id',
          name: 'OrderDetail',
          component: () => import('../views/OrderDetail.vue'),
          meta: { requiresAuth: true }
        },
        {
          path: 'payment/:orderId',
          name: 'PaymentPage',
          component: () => import('../views/PaymentPage.vue'),
          meta: { requiresAuth: true }
        },
        {
          path: 'profile',
          name: 'UserProfile',
          component: () => import('../views/UserProfile.vue'),
          meta: { requiresAuth: true }
        },
        {
          path: 'admin/dashboard',
          name: 'AdminDashboard',
          component: () => import('../views/admin/Dashboard.vue'),
          meta: { requiresAuth: true, requiresAdmin: true }
        },
        {
          path: 'admin/audit',
          name: 'ProductAudit',
          component: () => import('../views/admin/ProductAudit.vue'),
          meta: { requiresAuth: true, requiresAdmin: true }
        },
        {
          path: 'admin/users',
          name: 'UserManagement',
          component: () => import('../views/admin/UserManagement.vue'),
          meta: { requiresAuth: true, requiresAdmin: true }
        },
        {
          path: 'admin/orders',
          name: 'AdminOrders',
          component: () => import('../views/admin/OrderManagement.vue'),
          meta: { requiresAuth: true, requiresAdmin: true }
        }
      ]
    }
  ]
})

router.beforeEach((to, _from, next) => {
  const token = sessionStorage.getItem('token')
  const role = sessionStorage.getItem('role')

  if (to.meta.guest && token) {
    next('/')
    return
  }

  if (to.meta.requiresAuth && !token) {
    next('/login')
    return
  }

  if (to.meta.requiresAdmin && role !== 'ADMIN') {
    next('/')
    return
  }

  next()
})

export default router
