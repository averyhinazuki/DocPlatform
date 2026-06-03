import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getUnread, markAllRead as apiMarkAllRead } from '../api/notifications'

export const useNotificationStore = defineStore('notifications', () => {
  const unread = ref([])
  const badgeCount = computed(() => unread.value.length)
  let eventSource = null

  async function fetch() {
    const res = await getUnread()
    unread.value = res.data
  }

  function connect() {
    if (eventSource) return
    eventSource = new EventSource('/api/notifications/stream')
    eventSource.onmessage = (e) => {
      const notif = JSON.parse(e.data)
      unread.value = [notif, ...unread.value]
    }
    eventSource.onerror = () => {
      fetch().catch(() => {})
    }
  }

  function disconnect() {
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }
  }

  async function markAllRead() {
    await apiMarkAllRead()
    unread.value = []
  }

  return { unread, badgeCount, fetch, connect, disconnect, markAllRead }
})
