import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getUnread, markAllRead as apiMarkAllRead } from '../api/notifications'

export const useNotificationStore = defineStore('notifications', () => {
  const unread = ref([])
  const badgeCount = computed(() => unread.value.length)

  async function fetch() {
    const res = await getUnread()
    unread.value = res.data
  }

  async function markAllRead() {
    await apiMarkAllRead()
    unread.value = []
  }

  return { unread, badgeCount, fetch, markAllRead }
})
