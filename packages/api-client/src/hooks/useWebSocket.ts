'use client'

import { useEffect, useRef, useCallback } from 'react'
import type { TransactionNotification, TransactionStatusUpdate } from '@aegispay/shared-types'
import { TransactionStatusUpdateSchema } from '@aegispay/shared-types'

interface UseTransactionSocketOptions {
  userId: string
  accessToken: string | null
  wsBaseUrl: string
  onNotification: (notification: TransactionNotification) => void
}

// ── Per-transaction status socket ────────────────────────────────────────────
// Connects to transaction-service WS and subscribes to the topic for a single
// transaction.  Delivers typed status updates so the UI can patch the cache
// immediately without waiting for a polling round-trip.

interface UseTransactionStatusSocketOptions {
  transactionId: string | null
  accessToken:   string | null
  wsBaseUrl:     string   // should point at transaction-service (default: ws://localhost:8082)
  onStatusUpdate: (update: TransactionStatusUpdate) => void
  enabled?: boolean
}

interface StompFrame {
  command: string
  headers: Record<string, string>
  body: string
}

function buildStompFrame(frame: StompFrame): string {
  const headerStr = Object.entries(frame.headers)
    .map(([k, v]) => `${k}:${v}`)
    .join('\n')
  return `${frame.command}\n${headerStr}\n\n${frame.body}\0`
}

export function useTransactionSocket({
  userId,
  accessToken,
  wsBaseUrl,
  onNotification,
}: UseTransactionSocketOptions) {
  const wsRef             = useRef<WebSocket | null>(null)
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const onNotificationRef = useRef(onNotification)
  const stoppedRef        = useRef(false)   // set true on cleanup; prevents onclose from re-scheduling
  onNotificationRef.current = onNotification

  const connect = useCallback(() => {
    if (stoppedRef.current) return
    if (!accessToken || !userId) return
    if (wsRef.current?.readyState === WebSocket.OPEN) return

    const url = `${wsBaseUrl}/ws/websocket`
    const ws = new WebSocket(url)
    wsRef.current = ws

    ws.onopen = () => {
      // STOMP CONNECT
      ws.send(buildStompFrame({
        command: 'CONNECT',
        headers: {
          'accept-version': '1.2',
          'heart-beat': '10000,10000',
          Authorization: `Bearer ${accessToken}`,
        },
        body: '',
      }))
    }

    ws.onmessage = (event: MessageEvent<string>) => {
      const raw: string = event.data
      if (raw.startsWith('CONNECTED')) {
        // Subscribe to personal notification queue
        ws.send(buildStompFrame({
          command: 'SUBSCRIBE',
          headers: {
            id: 'sub-0',
            destination: `/user/${userId}/queue/notifications`,
          },
          body: '',
        }))
        return
      }

      if (raw.startsWith('MESSAGE')) {
        const bodyStart = raw.indexOf('\n\n')
        if (bodyStart === -1) return
        const body = raw.slice(bodyStart + 2).replace(/\0$/, '')
        try {
          const payload = JSON.parse(body) as TransactionNotification
          onNotificationRef.current(payload)
        } catch {
          // non-JSON frame, ignore
        }
      }
    }

    ws.onclose = () => {
      // Guard: if cleanup already ran, do NOT reconnect (fixes StrictMode double-mount
      // race where the onclose of the first mount's WS fires after cleanup and creates
      // a phantom third connection).
      if (stoppedRef.current) return
      reconnectTimerRef.current = setTimeout(connect, 5_000)
    }

    ws.onerror = () => {
      ws.close()
    }
  }, [accessToken, userId, wsBaseUrl])

  useEffect(() => {
    stoppedRef.current = false
    connect()
    return () => {
      stoppedRef.current = true
      if (reconnectTimerRef.current) clearTimeout(reconnectTimerRef.current)
      wsRef.current?.close()
    }
  }, [connect])
}

// ── useTransactionStatusSocket ────────────────────────────────────────────────

export function useTransactionStatusSocket({
  transactionId,
  accessToken,
  wsBaseUrl,
  onStatusUpdate,
  enabled = true,
}: UseTransactionStatusSocketOptions) {
  const wsRef              = useRef<WebSocket | null>(null)
  const reconnectTimerRef  = useRef<ReturnType<typeof setTimeout> | null>(null)
  const onStatusUpdateRef  = useRef(onStatusUpdate)
  const stoppedRef         = useRef(false)
  onStatusUpdateRef.current = onStatusUpdate

  const connect = useCallback(() => {
    if (stoppedRef.current) return
    if (!enabled || !accessToken || !transactionId) return
    if (wsRef.current?.readyState === WebSocket.OPEN) return

    const ws = new WebSocket(`${wsBaseUrl}/ws/websocket`)
    wsRef.current = ws

    ws.onopen = () => {
      ws.send(buildStompFrame({
        command: 'CONNECT',
        headers: {
          'accept-version': '1.2',
          'heart-beat':     '10000,10000',
          Authorization:    `Bearer ${accessToken}`,
        },
        body: '',
      }))
    }

    ws.onmessage = (event: MessageEvent<string>) => {
      const raw = event.data
      if (raw.startsWith('CONNECTED')) {
        ws.send(buildStompFrame({
          command: 'SUBSCRIBE',
          headers: {
            id:          'sub-txstatus',
            destination: `/topic/transactions/${transactionId}/status`,
          },
          body: '',
        }))
        return
      }

      if (raw.startsWith('MESSAGE')) {
        const bodyStart = raw.indexOf('\n\n')
        if (bodyStart === -1) return
        const body = raw.slice(bodyStart + 2).replace(/\0$/, '')
        try {
          const parsed = JSON.parse(body)
          const result = TransactionStatusUpdateSchema.safeParse(parsed)
          if (result.success) {
            onStatusUpdateRef.current(result.data)
          }
        } catch {
          // non-JSON frame, ignore
        }
      }
    }

    ws.onclose = () => {
      if (stoppedRef.current) return
      reconnectTimerRef.current = setTimeout(connect, 5_000)
    }

    ws.onerror = () => { ws.close() }
  }, [enabled, accessToken, transactionId, wsBaseUrl])

  useEffect(() => {
    stoppedRef.current = false
    connect()
    return () => {
      stoppedRef.current = true
      if (reconnectTimerRef.current) clearTimeout(reconnectTimerRef.current)
      wsRef.current?.close()
    }
  }, [connect])
}
