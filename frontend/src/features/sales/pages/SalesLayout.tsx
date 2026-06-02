import { Outlet } from 'react-router-dom'

export default function SalesLayout() {
  return (
    <div className="h-full flex flex-col bg-gray-50/30">
      {/* Main Content Area */}
      <div className="flex-1 overflow-auto p-4 md:p-6 relative">
        <Outlet />
      </div>
    </div>
  )
}
