import { create } from 'zustand'

interface NotificationState {
  unreadCount: number
  increment:   () => void
  reset:       () => void
}

export const useNotificationStore = create<NotificationState>((set) => ({
  unreadCount: 0,
  increment:   () => set((s) => ({ unreadCount: s.unreadCount + 1 })),
  reset:       () => set({ unreadCount: 0 }),
}))
