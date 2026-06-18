import type { ReactNode } from 'react'
import { Sidebar } from './Sidebar'
import { TopBar } from './TopBar'
import { useAuthStore } from '../../store/authStore'

export function AppShell({ children }: { children: ReactNode }) {
  const selectedBranchId = useAuthStore(s => s.selectedBranchId)
  return (
    <div className="flex h-screen bg-gray-50 overflow-hidden">
      <Sidebar />
      <div className="flex flex-col flex-1 min-w-0">
        <TopBar />
        <main key={selectedBranchId || 'default'} className="flex-1 overflow-auto p-6" id="main-content" role="main">
          {children}
        </main>
      </div>
    </div>
  )
}
