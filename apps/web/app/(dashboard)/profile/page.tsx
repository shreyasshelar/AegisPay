import type { Metadata } from 'next'
import { getServerSession } from 'next-auth'
import { authOptions } from '@/lib/auth'
import { ProfileClient } from './profile-client'

export const metadata: Metadata = { title: 'Profile & KYC' }

export default async function ProfilePage() {
  const session = await getServerSession(authOptions)
  return <ProfileClient userId={session?.user?.id ?? ''} />
}
