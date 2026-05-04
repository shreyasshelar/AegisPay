'use client'

import { useEffect, useRef, useCallback } from 'react'
import type { TransactionNotification } from '@aegispay/shared-types'

interface UseTransactionSocketOptions {
  userId: string
  accessToken: string | null
  wsBaseUrl: string
  onNotification: (notification: TransactionNotification) => void
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
  const wsRef = useRef<WebSocket | null>(null)
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const onNotificationRef = useRef(onNotification)
  onNotificationRef.current = onNotification

  const connect = useCallback(() => {
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
      // Reconnect after 5 s
      reconnectTimerRef.current = setTimeout(connect, 5_000)
    }

    ws.onerror = () => {
      ws.close()
    }
  }, [accessToken, userId, wsBaseUrl])

  useEffect(() => {
    connect()
    return () => {
      if (reconnectTimerRef.current) clearTimeout(reconnectTimerRef.current)
      wsRef.current?.close()
    }
  }, [connect])
}
