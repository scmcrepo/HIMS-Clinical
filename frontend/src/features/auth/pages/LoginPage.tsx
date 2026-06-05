import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useLogin } from '../../../hooks/auth/useAuth'
import { Eye, EyeOff } from 'lucide-react'

const schema = z.object({ username: z.string().min(1, 'Required'), password: z.string().min(1, 'Required') })
type FormValues = z.infer<typeof schema>

export default function LoginPage() {
  const login = useLogin()
  const [showPassword, setShowPassword] = useState(false)
  const { register, handleSubmit, formState: { errors } } = useForm<FormValues>({ resolver: zodResolver(schema) })
  const onSubmit = (data: FormValues) => login.mutate(data)

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <div className="bg-white rounded-2xl shadow-sm border border-gray-200 w-full max-w-sm p-8">
        <div className="mb-8 text-center">
          <h1 className="text-2xl font-bold text-blue-700">HMS</h1>
          <p className="text-sm text-gray-500 mt-1">Hospital Management System</p>
        </div>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" aria-label="Login form" noValidate>
          <div>
            <label htmlFor="username" className="block text-sm font-medium text-gray-700 mb-1">Username</label>
            <input id="username" type="text" autoComplete="username"
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 aria-invalid:border-red-400"
              aria-invalid={!!errors.username} aria-describedby={errors.username ? 'username-err' : undefined}
              {...register('username')} />
            {errors.username && <p id="username-err" role="alert" className="text-xs text-red-600 mt-1">{errors.username.message}</p>}
          </div>
          <div>
            <label htmlFor="password" className="block text-sm font-medium text-gray-700 mb-1">Password</label>
            <div className="relative">
              <input id="password" type={showPassword ? 'text' : 'password'} autoComplete="current-password"
                className="w-full pl-3 pr-10 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                aria-invalid={!!errors.password} aria-describedby={errors.password ? 'password-err' : undefined}
                {...register('password')} />
              <button
                type="button"
                onClick={() => setShowPassword(prev => !prev)}
                className="absolute inset-y-0 right-0 flex items-center pr-3 text-gray-400 hover:text-gray-600 focus:outline-none"
                aria-label={showPassword ? "Hide password" : "Show password"}
              >
                {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </button>
            </div>
            {errors.password && <p id="password-err" role="alert" className="text-xs text-red-600 mt-1">{errors.password.message}</p>}
          </div>
          {login.error && (
            <p role="alert" className="text-xs text-red-600 bg-red-50 border border-red-200 rounded px-3 py-2">
              {(login.error as any)?.response?.data?.message || (login.error as Error).message || 'Login failed. Check credentials.'}
            </p>
          )}
          <button type="submit" disabled={login.isPending}
            className="w-full py-2.5 bg-blue-600 text-white text-sm font-semibold rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors">
            {login.isPending ? 'Signing in…' : 'Sign In'}
          </button>
        </form>
      </div>
    </div>
  )
}
