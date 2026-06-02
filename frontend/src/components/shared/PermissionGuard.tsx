import type { ReactNode } from 'react'
import { usePermission } from '../../hooks/auth/useAuth'
interface Props { featureKey: string; fallback?: ReactNode; children: ReactNode }
export function PermissionGuard({ featureKey, fallback = null, children }: Props) {
  const allowed = usePermission(featureKey)
  return allowed ? <>{children}</> : <>{fallback}</>
}
