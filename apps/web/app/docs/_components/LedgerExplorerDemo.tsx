// FILE: apps/web/app/docs/_components/LedgerExplorerDemo.tsx
'use client'

import { useState } from 'react'
import { CheckCircle2 } from 'lucide-react'

type Account = {
  name: string
  emoji: string
  available: number
  reserved: number
}

type LedgerEntry = {
  id: number
  ts: string
  account: string
  type: 'DEBIT' | 'CREDIT' | 'REVERSAL'
  amount: number
  balance: number
}

const INITIAL_PRIYA: Account = { name: 'Priya', emoji: '👩', available: 50000, reserved: 0 }
const INITIAL_ARJUN: Account = { name: 'Arjun', emoji: '👨', available: 5000, reserved: 0 }

const AMOUNTS = [500, 1000, 2000, 5000]

type Phase = 'idle' | 'reserved' | 'committed'

function fmtINR(n: number) {
  return `₹${n.toLocaleString('en-IN')}`
}

function timestamp() {
  return new Date().toLocaleTimeString('en-IN', { hour12: false })
}

export default function LedgerExplorerDemo() {
  const [amount, setAmount] = useState(1000)
  const [phase, setPhase] = useState<Phase>('idle')
  const [priya, setPriya] = useState<Account>(INITIAL_PRIYA)
  const [arjun, setArjun] = useState<Account>(INITIAL_ARJUN)
  const [entries, setEntries] = useState<LedgerEntry[]>([])
  const [nextId, setNextId] = useState(1)

  const addEntry = (account: string, type: LedgerEntry['type'], amt: number, bal: number) => {
    setEntries((prev) => [
      ...prev,
      { id: nextId, ts: timestamp(), account, type, amount: amt, balance: bal },
    ])
    setNextId((n) => n + 1)
  }

  const reserve = () => {
    if (phase !== 'idle') return
    if (priya.available < amount) return
    const newAvail = priya.available - amount
    const newReserved = priya.reserved + amount
    setPriya({ ...priya, available: newAvail, reserved: newReserved })
    setPhase('reserved')
  }

  const commit = () => {
    if (phase !== 'reserved') return
    const newPriyaReserved = priya.reserved - amount
    const newArjunAvail = arjun.available + amount
    setPriya({ ...priya, reserved: newPriyaReserved })
    setArjun({ ...arjun, available: newArjunAvail })
    addEntry('Priya', 'DEBIT', amount, priya.available)
    addEntry('Arjun', 'CREDIT', amount, newArjunAvail)
    setPhase('committed')
  }

  const release = () => {
    if (phase !== 'reserved') return
    const newAvail = priya.available + amount
    const newReserved = priya.reserved - amount
    setPriya({ ...priya, available: newAvail, reserved: newReserved })
    addEntry('Priya', 'REVERSAL', amount, newAvail)
    setPhase('committed')
  }

  const reset = () => {
    setPriya(INITIAL_PRIYA)
    setArjun(INITIAL_ARJUN)
    setEntries([])
    setPhase('idle')
  }

  // Invariant: SUM(all entries) — debits add, credits subtract, reversals add
  const invariantSum = entries.reduce((sum, e) => {
    if (e.type === 'DEBIT') return sum + e.amount
    if (e.type === 'CREDIT') return sum - e.amount
    if (e.type === 'REVERSAL') return sum + 0 // reversal is an undo — net 0 added
    return sum
  }, 0)

  const entryTypeColor: Record<LedgerEntry['type'], string> = {
    DEBIT: 'text-red-600 bg-red-50',
    CREDIT: 'text-green-600 bg-green-50',
    REVERSAL: 'text-amber-600 bg-amber-50',
  }

  return (
    <div className="rounded-xl border border-gray-100 shadow-sm bg-white overflow-hidden">
      <div className="px-5 py-4 border-b border-gray-100">
        <h3 className="font-semibold text-gray-900">Live Balance Simulation</h3>
        <p className="text-sm text-gray-500 mt-0.5">
          Simulate a payment from Priya to Arjun step-by-step
        </p>
      </div>

      {/* Accounts */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 p-5 border-b border-gray-100">
        {[
          { acc: priya, role: 'Payer' },
          { acc: arjun, role: 'Payee' },
        ].map(({ acc, role }) => (
          <div key={acc.name} className="rounded-xl border border-gray-100 p-4 bg-gray-50">
            <div className="flex items-center gap-2 mb-3">
              <span className="text-xl">{acc.emoji}</span>
              <div>
                <p className="font-semibold text-gray-900">{acc.name}</p>
                <p className="text-xs text-gray-400">{role}</p>
              </div>
            </div>
            <div className="space-y-2">
              <div className="flex justify-between text-sm">
                <span className="text-gray-500">Available</span>
                <span className="font-bold text-gray-900">{fmtINR(acc.available)}</span>
              </div>
              <div className="flex justify-between text-sm">
                <span className="text-gray-500">Reserved</span>
                <span className={`font-bold ${acc.reserved > 0 ? 'text-amber-600' : 'text-gray-400'}`}>
                  {fmtINR(acc.reserved)}
                </span>
              </div>
              <div className="h-px bg-gray-200" />
              <div className="flex justify-between text-sm">
                <span className="text-gray-500">Total</span>
                <span className="font-bold text-gray-700">{fmtINR(acc.available + acc.reserved)}</span>
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* Controls */}
      <div className="px-5 py-4 border-b border-gray-100 space-y-3">
        <div>
          <p className="text-xs font-medium text-gray-500 mb-2 uppercase tracking-wide">Amount</p>
          <div className="flex gap-2 flex-wrap">
            {AMOUNTS.map((a) => (
              <button
                key={a}
                onClick={() => { if (phase === 'idle') setAmount(a) }}
                disabled={phase !== 'idle'}
                className={`text-sm px-3 py-1.5 rounded-lg font-medium transition-colors ${
                  amount === a && phase === 'idle'
                    ? 'bg-blue-500 text-white'
                    : 'bg-gray-100 text-gray-600 hover:bg-gray-200 disabled:opacity-40'
                }`}
              >
                {fmtINR(a)}
              </button>
            ))}
          </div>
        </div>
        <div>
          <p className="text-xs font-medium text-gray-500 mb-2 uppercase tracking-wide">Actions</p>
          <div className="flex gap-2 flex-wrap">
            <button
              onClick={reserve}
              disabled={phase !== 'idle' || priya.available < amount}
              className="text-sm px-3 py-1.5 rounded-lg font-medium bg-purple-500 text-white hover:bg-purple-600 disabled:opacity-40 transition-colors"
            >
              1. Reserve
            </button>
            <button
              onClick={commit}
              disabled={phase !== 'reserved'}
              className="text-sm px-3 py-1.5 rounded-lg font-medium bg-green-500 text-white hover:bg-green-600 disabled:opacity-40 transition-colors"
            >
              2. Commit
            </button>
            <button
              onClick={release}
              disabled={phase !== 'reserved'}
              className="text-sm px-3 py-1.5 rounded-lg font-medium bg-amber-500 text-white hover:bg-amber-600 disabled:opacity-40 transition-colors"
            >
              3. Release (rollback)
            </button>
            <button
              onClick={reset}
              className="text-sm px-3 py-1.5 rounded-lg font-medium bg-gray-100 text-gray-600 hover:bg-gray-200 transition-colors"
            >
              Reset
            </button>
          </div>
        </div>
      </div>

      {/* Ledger entries */}
      <div className="p-5">
        <div className="flex items-center justify-between mb-3">
          <p className="text-sm font-semibold text-gray-700">Ledger Entry Log</p>
          {entries.length > 0 && (
            <div className="flex items-center gap-1.5 text-xs">
              <CheckCircle2 size={13} className="text-green-500" />
              <span className="text-gray-500">
                Invariant check: SUM = {invariantSum === 0 ? '0 ✅' : `${invariantSum} ⚠️`}
              </span>
            </div>
          )}
        </div>

        {entries.length === 0 ? (
          <div className="text-center py-8 text-sm text-gray-400 bg-gray-50 rounded-xl border border-dashed border-gray-200">
            No entries yet — use the controls above
          </div>
        ) : (
          <div className="rounded-xl border border-gray-100 overflow-hidden">
            <table className="w-full text-xs">
              <thead className="bg-gray-50 border-b border-gray-100">
                <tr>
                  <th className="text-left px-3 py-2 text-gray-500 font-medium">#</th>
                  <th className="text-left px-3 py-2 text-gray-500 font-medium">Time</th>
                  <th className="text-left px-3 py-2 text-gray-500 font-medium">Account</th>
                  <th className="text-left px-3 py-2 text-gray-500 font-medium">Type</th>
                  <th className="text-right px-3 py-2 text-gray-500 font-medium">Amount</th>
                  <th className="text-right px-3 py-2 text-gray-500 font-medium">Balance</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {entries.map((e) => (
                  <tr key={e.id} className="hover:bg-gray-50">
                    <td className="px-3 py-2 text-gray-400">{e.id}</td>
                    <td className="px-3 py-2 font-mono text-gray-500">{e.ts}</td>
                    <td className="px-3 py-2 font-medium text-gray-700">{e.account}</td>
                    <td className="px-3 py-2">
                      <span className={`px-1.5 py-0.5 rounded font-bold ${entryTypeColor[e.type]}`}>
                        {e.type}
                      </span>
                    </td>
                    <td className="px-3 py-2 text-right font-mono">{fmtINR(e.amount)}</td>
                    <td className="px-3 py-2 text-right font-mono text-gray-600">{fmtINR(e.balance)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}
