import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useLogin } from '../../../hooks/auth/useAuth'
import { Eye, EyeOff, Activity } from 'lucide-react'

const schema = z.object({ username: z.string().min(1, 'Required'), password: z.string().min(1, 'Required') })
type FormValues = z.infer<typeof schema>

export default function LoginPage() {
  const login = useLogin()
  const [showPassword, setShowPassword] = useState(false)
  const { register, handleSubmit, formState: { errors } } = useForm<FormValues>({ resolver: zodResolver(schema) })
  const onSubmit = (data: FormValues) => login.mutate(data)

  return (
    <div className="min-h-screen flex items-center justify-center bg-neutral-50 px-6 py-12">
      <div className="w-full max-w-sm">
        {/* brand mark */}
        <div className="mb-10 flex items-center gap-2.5">
          <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-neutral-900 text-white">
            <Activity className="h-5 w-5" />
          </div>
          <span className="text-lg font-semibold tracking-tight text-neutral-900">HMS</span>
        </div>

        <div className="mb-8">
          <h1 className="text-2xl font-semibold tracking-tight text-neutral-900">Sign in</h1>
          <p className="mt-1.5 text-sm text-neutral-500">Welcome back. Please enter your details.</p>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-5" aria-label="Login form" noValidate>
          <div>
            <label htmlFor="username" className="block text-sm font-medium text-neutral-800 mb-1.5">Username</label>
            <input id="username" type="text" autoComplete="username" placeholder="Enter your username"
              className="w-full rounded-lg border border-neutral-200 bg-white px-3.5 py-2.5 text-sm text-neutral-900 placeholder:text-neutral-400 transition focus:border-neutral-900 focus:outline-none focus:ring-4 focus:ring-neutral-900/5 aria-invalid:border-red-400"
              aria-invalid={!!errors.username} aria-describedby={errors.username ? 'username-err' : undefined}
              {...register('username')} />
            {errors.username && <p id="username-err" role="alert" className="text-xs text-red-600 mt-1.5">{errors.username.message}</p>}
          </div>
          <div>
            <label htmlFor="password" className="block text-sm font-medium text-neutral-800 mb-1.5">Password</label>
            <div className="relative">
              <input id="password" type={showPassword ? 'text' : 'password'} autoComplete="current-password" placeholder="Enter your password"
                className="w-full rounded-lg border border-neutral-200 bg-white pl-3.5 pr-10 py-2.5 text-sm text-neutral-900 placeholder:text-neutral-400 transition focus:border-neutral-900 focus:outline-none focus:ring-4 focus:ring-neutral-900/5 aria-invalid:border-red-400"
                aria-invalid={!!errors.password} aria-describedby={errors.password ? 'password-err' : undefined}
                {...register('password')} />
              <button
                type="button"
                onClick={() => setShowPassword(prev => !prev)}
                className="absolute inset-y-0 right-0 flex items-center pr-3 text-neutral-400 hover:text-neutral-700 focus:outline-none"
                aria-label={showPassword ? "Hide password" : "Show password"}
              >
                {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </button>
            </div>
            {errors.password && <p id="password-err" role="alert" className="text-xs text-red-600 mt-1.5">{errors.password.message}</p>}
          </div>
          {login.error && (
            <p role="alert" className="text-xs text-red-600 bg-red-50 border border-red-200 rounded-lg px-3 py-2.5">
              {(login.error as any)?.response?.data?.message || (login.error as Error).message || 'Login failed. Check credentials.'}
            </p>
          )}
          <button type="submit" disabled={login.isPending}
            className="w-full rounded-lg bg-neutral-900 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-neutral-800 focus:outline-none focus:ring-4 focus:ring-neutral-900/20 disabled:opacity-50 disabled:cursor-not-allowed">
            {login.isPending ? 'Signing in…' : 'Sign in'}
          </button>
        </form>

        <p className="mt-8 text-xs text-neutral-400">
          Secured with role-based access control
        </p>
      </div>
    </div>
  )
}
