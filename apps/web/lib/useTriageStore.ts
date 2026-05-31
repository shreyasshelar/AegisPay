import { create } from 'zustand'

export interface TriageSession {
  id:          string
  serviceName: string
  description: string
  analysis:    string
  degraded:    boolean
  timestamp:   Date
}

interface TriageState {
  sessions:        TriageSession[]
  addSession:      (session: TriageSession) => void
  clearSessions:   () => void
}

/**
 * Global triage session history — persists across page navigations within the
 * tab.  Stored in memory (not localStorage) so it clears on a full page reload
 * or tab close, which is the right lifetime for an admin investigation session.
 */
export const useTriageStore = create<TriageState>((set) => ({
  sessions:      [],
  addSession:    (session) => set((s) => ({ sessions: [session, ...s.sessions] })),
  clearSessions: () => set({ sessions: [] }),
}))
