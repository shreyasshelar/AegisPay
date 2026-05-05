'use client'

import { createContext, useContext, type ReactNode } from 'react'
import {
  AegisApiClient,
  TransactionsClient,
  UsersClient,
  LedgerClient,
  AiClient,
  NotificationsClient,
  RiskClient,
} from '../client/index.js'

interface ApiContextValue {
  transactions:  TransactionsClient
  users:         UsersClient
  ledger:        LedgerClient
  ai:            AiClient
  notifications: NotificationsClient
  risk:          RiskClient
}

const ApiContext = createContext<ApiContextValue | null>(null)

interface ApiProviderProps {
  children:       ReactNode
  baseURL:        string
  getAccessToken: () => Promise<string | null>
  onUnauthorized?: () => void
}

export function ApiProvider({ children, baseURL, getAccessToken, onUnauthorized }: ApiProviderProps) {
  const client = new AegisApiClient({ baseURL, getAccessToken, onUnauthorized })

  const value: ApiContextValue = {
    transactions:  new TransactionsClient(client),
    users:         new UsersClient(client),
    ledger:        new LedgerClient(client),
    ai:            new AiClient(client),
    notifications: new NotificationsClient(client),
    risk:          new RiskClient(client),
  }

  return <ApiContext.Provider value={value}>{children}</ApiContext.Provider>
}

export function useApiClient(): ApiContextValue {
  const ctx = useContext(ApiContext)
  if (!ctx) throw new Error('useApiClient must be used inside <ApiProvider>')
  return ctx
}
