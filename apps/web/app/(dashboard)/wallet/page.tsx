import { getServerSession } from 'next-auth'
import { redirect }         from 'next/navigation'
import { authOptions }      from '@/lib/auth'
import { WalletClient }     from './wallet-client'

export const metadata = { title: 'Wallet — AegisPay' }

export default async function WalletPage() {
  const session = await getServerSession(authOptions)
  if (!session) redirect('/api/auth/signin')

  return <WalletClient userId={session.user.id} />
}
