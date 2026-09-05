import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useLogin, useMfaVerify } from '../../../hooks/auth/useAuth'
import { Eye, EyeOff, Activity, ArrowLeft, Mail, Key, CheckCircle, ShieldCheck } from 'lucide-react'
import { authApi } from '../../../services/auth/authApi'

// Login Schema
const loginSchema = z.object({
  username: z.string().min(1, 'Required'),
  password: z.string().min(1, 'Required'),
})
type LoginFormValues = z.infer<typeof loginSchema>

// Forgot Password Schemas
const requestSchema = z.object({
  email: z.string().min(1, 'Email is required').email('Invalid email address'),
})
type RequestFormValues = z.infer<typeof requestSchema>

const verifySchema = z.object({
  otp: z.string().min(6, 'OTP must be 6 digits').max(6, 'OTP must be 6 digits'),
})
type VerifyFormValues = z.infer<typeof verifySchema>

const resetSchema = z.object({
  newPassword: z.string().min(6, 'Password must be at least 6 characters'),
  confirmPassword: z.string().min(1, 'Confirm password is required'),
}).refine((data) => data.newPassword === data.confirmPassword, {
  message: "Passwords don't match",
  path: ['confirmPassword'],
})
type ResetFormValues = z.infer<typeof resetSchema>

type ForgotPasswordFlowState = 'idle' | 'branch_select' | 'mfa_challenge' | 'mfa_enrolment_required'
  | 'request_otp' | 'verify_otp' | 'reset_password'

export default function LoginPage() {
  const login = useLogin()
  const isAccountLocked = !!(login.error && (login.error as any).response?.data?.message?.toLowerCase().includes('locked'))
  const [showPassword, setShowPassword] = useState(false)
  const [flowState, setFlowState] = useState<ForgotPasswordFlowState>('idle')

  // Forgot password flow states
  const [email, setEmail] = useState('')
  const [otp, setOtp] = useState('')
  const [actionLoading, setActionLoading] = useState(false)
  const [flowError, setFlowError] = useState('')
  const [flowSuccess, setFlowSuccess] = useState('')

  // Eye toggles for new password fields
  const [showNewPassword, setShowNewPassword] = useState(false)
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)

  // Branch select states
  const [availableBranches, setAvailableBranches] = useState<{ id: string; name: string }[]>([])
  const [selectedBranchId, setSelectedBranchId] = useState('')

  // WO-029 / U-002. Empty unless the server raises a challenge, which it only
  // does when hms.mfa.mode is OPTIONAL or REQUIRED. With the shipped default of
  // OFF none of this is ever reached.
  const [mfaChallengeId, setMfaChallengeId] = useState('')
  const [mfaCode, setMfaCode] = useState('')
  const [mfaError, setMfaError] = useState('')
  const mfaVerify = useMfaVerify()

  const [showForceLogoutPopup, setShowForceLogoutPopup] = useState(false)
  const [pendingLoginData, setPendingLoginData] = useState<LoginFormValues | null>(null)

  const { register: registerLogin, handleSubmit: handleLoginSubmit, formState: { errors: loginErrors } } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { username: '', password: '' },
  })

  const { register: registerRequest, handleSubmit: handleRequestSubmit, formState: { errors: requestErrors } } = useForm<RequestFormValues>({
    resolver: zodResolver(requestSchema),
  })

  const { register: registerVerify, handleSubmit: handleVerifySubmit, formState: { errors: verifyErrors } } = useForm<VerifyFormValues>({
    resolver: zodResolver(verifySchema),
  })

  const { register: registerReset, handleSubmit: handleResetSubmit, formState: { errors: resetErrors } } = useForm<ResetFormValues>({
    resolver: zodResolver(resetSchema),
  })

  const handleLoginAction = async (data: LoginFormValues, forceLogout: boolean) => {
    try {
      const activeBranchId = flowState === 'branch_select' ? selectedBranchId : '';
      const res = await login.mutateAsync({
        username: data.username,
        password: data.password,
        branchId: activeBranchId || null,
        forceLogout
      })
      if (res.data?.status === 'MULTIPLE_BRANCHES') {
        setAvailableBranches(res.data.branches)
        setSelectedBranchId(res.data.branches[0].id)
        setFlowState('branch_select')
      } else if (res.data?.status === 'MFA_REQUIRED') {
        setMfaChallengeId(res.data.challengeId)
        setMfaCode('')
        setMfaError('')
        setFlowState('mfa_challenge')
      } else if (res.data?.status === 'MFA_ENROLMENT_REQUIRED') {
        setFlowState('mfa_enrolment_required')
      }
    } catch (err: any) {
      if (err?.response?.data?.message?.includes('ALREADY_LOGGED_IN')) {
        setPendingLoginData(data)
        setShowForceLogoutPopup(true)
        return
      }
      // Error handled by mutation
    }
  }

  const onLoginSubmit = (data: LoginFormValues) => handleLoginAction(data, false)

  const onMfaSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setMfaError('')
    try {
      await mfaVerify.mutateAsync({ challengeId: mfaChallengeId, code: mfaCode })
    } catch (err: any) {
      // The server's message is the useful one: it distinguishes a wrong code
      // from a reused one from an expired challenge, and each needs a different
      // action from the person standing at the machine.
      setMfaError(err?.response?.data?.message || 'That code was not accepted.')
      setMfaCode('')
    }
  }

  const abandonMfa = () => {
    // The challenge is left to expire server-side rather than cancelled. It is
    // single-use with a five-minute TTL, and giving the unauthenticated caller a
    // way to delete challenges would be a way to interfere with someone else's
    // sign-in.
    setMfaChallengeId('')
    setMfaCode('')
    setMfaError('')
    setFlowState('idle')
  }

  const confirmForceLogout = () => {
    if (pendingLoginData) {
      setShowForceLogoutPopup(false)
      handleLoginAction(pendingLoginData, true)
    }
  }

  const onRequestOtp = async (data: RequestFormValues) => {
    setActionLoading(true)
    setFlowError('')
    setFlowSuccess('')
    try {
      await authApi.forgotPasswordRequest(data.email)
      setEmail(data.email)
      setFlowSuccess('OTP sent successfully to your email.')
      setFlowState('verify_otp')
    } catch (err: any) {
      setFlowError(err?.response?.data?.message || 'Failed to send OTP. Please verify your email.')
    } finally {
      setActionLoading(false)
    }
  }

  const onVerifyOtp = async (data: VerifyFormValues) => {
    setActionLoading(true)
    setFlowError('')
    setFlowSuccess('')
    try {
      await authApi.forgotPasswordVerify(email, data.otp)
      setOtp(data.otp)
      setFlowSuccess('OTP verified. You can now reset your password.')
      setFlowState('reset_password')
    } catch (err: any) {
      setFlowError(err?.response?.data?.message || 'Invalid or expired OTP.')
    } finally {
      setActionLoading(false)
    }
  }

  const onResetPassword = async (data: ResetFormValues) => {
    setActionLoading(true)
    setFlowError('')
    setFlowSuccess('')
    try {
      await authApi.forgotPasswordReset(email, otp, data.newPassword, data.confirmPassword)
      setFlowSuccess('Password reset successfully. Please sign in with your new password.')
      setFlowState('idle')
      setEmail('')
      setOtp('')
    } catch (err: any) {
      setFlowError(err?.response?.data?.message || 'Failed to reset password.')
    } finally {
      setActionLoading(false)
    }
  }

  const handleBackToLogin = () => {
    setFlowState('idle')
    setFlowError('')
    setFlowSuccess('')
    setEmail('')
    setOtp('')
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-neutral-50 px-6 py-12">
      <div className="w-full max-w-sm">
        <div className="mb-10 flex items-center gap-2.5">
          <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-neutral-900 text-white">
            <Activity className="h-5 w-5" />
          </div>
          <span className="text-lg font-semibold tracking-tight text-neutral-900">Asthya HIMS</span>
        </div>

        {/* State Banner Notifications */}
        {flowSuccess && (
          <div role="status" className="mb-6 flex items-start gap-2 text-xs text-green-700 bg-green-50 border border-green-200 rounded-lg px-3.5 py-3">
            <CheckCircle className="h-4 w-4 shrink-0 mt-0.5" />
            <span>{flowSuccess}</span>
          </div>
        )}
        {flowError && (
          <p role="alert" className="mb-6 text-xs text-red-600 bg-red-50 border border-red-200 rounded-lg px-3.5 py-3">
            {flowError}
          </p>
        )}

        {/* 1. SIGN IN & BRANCH SELECTION MODE */}
        {(flowState === 'idle' || flowState === 'branch_select') && (
          <>
            <div className="mb-8">
              <h1 className="text-2xl font-semibold tracking-tight text-neutral-900">
                {flowState === 'branch_select' ? 'Select Branch' : 'Sign in'}
              </h1>
              <p className="mt-1.5 text-sm text-neutral-500">
                {flowState === 'branch_select' 
                  ? 'You have access to multiple branches. Please choose one.' 
                  : 'Welcome back. Please enter your details.'}
              </p>
            </div>

            <form onSubmit={handleLoginSubmit(onLoginSubmit)} className="space-y-5" aria-label="Login form" noValidate>
              {flowState === 'idle' ? (
                <>
                  <div>
                    <label htmlFor="username" className="block text-sm font-medium text-neutral-800 mb-1.5">Username</label>
                    <input id="username" type="text" autoComplete="username" placeholder="Enter your username"
                      className="w-full rounded-lg border border-neutral-200 bg-white px-3.5 py-2.5 text-sm text-neutral-900 placeholder:text-neutral-400 transition focus:border-neutral-900 focus:outline-none focus:ring-4 focus:ring-neutral-900/5 aria-invalid:border-red-400"
                      aria-invalid={!!loginErrors.username} aria-describedby={loginErrors.username ? 'username-err' : undefined}
                      {...registerLogin('username', {
                        onChange: (e) => {
                          e.target.value = e.target.value.toLowerCase();
                          login.reset?.();
                        }
                      })} />
                    {loginErrors.username && <p id="username-err" role="alert" className="text-xs text-red-600 mt-1.5">{loginErrors.username.message}</p>}
                  </div>

                  <div>
                    <div className="flex justify-between items-center mb-1.5">
                      <label htmlFor="password" className="block text-sm font-medium text-neutral-800">Password</label>
                      <button type="button" tabIndex={-1} onClick={() => setFlowState('request_otp')}
                        className="text-xs font-semibold text-neutral-600 hover:text-neutral-900 focus:outline-none">
                        Forgot password?
                      </button>
                    </div>
                    <div className="relative">
                      <input id="password" type={showPassword ? 'text' : 'password'} autoComplete="current-password" placeholder="Enter your password"
                        className="w-full rounded-lg border border-neutral-200 bg-white pl-3.5 pr-10 py-2.5 text-sm text-neutral-900 placeholder:text-neutral-400 transition focus:border-neutral-900 focus:outline-none focus:ring-4 focus:ring-neutral-900/5 aria-invalid:border-red-400"
                        aria-invalid={!!loginErrors.password} aria-describedby={loginErrors.password ? 'password-err' : undefined}
                        {...registerLogin('password', {
                          onChange: () => {
                            login.reset?.();
                          }
                        })} />
                      <button type="button" tabIndex={-1} onClick={() => setShowPassword(prev => !prev)}
                        className="absolute inset-y-0 right-0 flex items-center pr-3 text-neutral-400 hover:text-neutral-700 focus:outline-none"
                        aria-label={showPassword ? 'Hide password' : 'Show password'}>
                        {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                      </button>
                    </div>
                    {loginErrors.password && <p id="password-err" role="alert" className="text-xs text-red-600 mt-1.5">{loginErrors.password.message}</p>}
                  </div>
                </>
              ) : (
                <>
                  {/* Keep register fields present in DOM but hidden so react-hook-form can read them on submit */}
                  <input type="hidden" {...registerLogin('username')} />
                  <input type="hidden" {...registerLogin('password')} />

                  <div>
                    <label htmlFor="branchSelect" className="block text-sm font-medium text-neutral-800 mb-1.5">Branch</label>
                    <select
                      id="branchSelect"
                      value={selectedBranchId}
                      onChange={e => setSelectedBranchId(e.target.value)}
                      className="w-full rounded-lg border border-neutral-200 bg-white px-3.5 py-2.5 text-sm text-neutral-900 transition focus:border-neutral-900 focus:outline-none focus:ring-4 focus:ring-neutral-900/5"
                    >
                      {availableBranches.map(b => (
                        <option key={b.id} value={b.id}>
                          {b.name}
                        </option>
                      ))}
                    </select>
                  </div>

                  <button
                    type="button"
                    onClick={handleBackToLogin}
                    className="flex items-center gap-2 text-xs font-medium text-neutral-600 hover:text-neutral-900 transition-colors"
                  >
                    <ArrowLeft className="h-3.5 w-3.5" /> Back to credentials
                  </button>
                </>
              )}

              {login.error && (
                <p role="alert" className="text-xs text-red-600 bg-red-50 border border-red-200 rounded-lg px-3 py-2.5">
                  {(login.error as any)?.response?.data?.message || (login.error as Error).message || 'Login failed. Check credentials.'}
                </p>
              )}

              <button type="submit" disabled={login.isPending || isAccountLocked}
                className="w-full rounded-lg bg-neutral-900 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-neutral-800 focus:outline-none focus:ring-4 focus:ring-neutral-900/20 disabled:opacity-50 disabled:cursor-not-allowed">
                {login.isPending ? 'Signing in…' : flowState === 'branch_select' ? 'Confirm & Sign in' : 'Sign in'}
              </button>
            </form>
          </>
        )}

        {/* MFA CHALLENGE — WO-029 / U-002 */}
        {flowState === 'mfa_challenge' && (
          <>
            <div className="mb-8">
              <button type="button" onClick={abandonMfa}
                className="mb-4 inline-flex items-center gap-1.5 text-sm text-neutral-500 hover:text-neutral-900">
                <ArrowLeft className="h-4 w-4" /> Back to sign in
              </button>
              <h1 className="text-2xl font-semibold tracking-tight text-neutral-900">Two-step verification</h1>
              <p className="mt-1.5 text-sm text-neutral-500">
                Enter the 6-digit code from your authenticator app. You can also use
                one of your recovery codes.
              </p>
            </div>

            <form onSubmit={onMfaSubmit} className="space-y-5">
              <div>
                <label className="block text-xs font-semibold text-neutral-600 mb-1.5">
                  Authentication code
                </label>
                <input
                  value={mfaCode}
                  onChange={e => setMfaCode(e.target.value)}
                  autoFocus
                  autoComplete="one-time-code"
                  inputMode="text"
                  placeholder="123456"
                  className="w-full rounded-lg border border-neutral-200 px-3.5 py-2.5 text-center text-lg tracking-[0.3em] font-semibold focus:border-neutral-900 focus:ring-2 focus:ring-neutral-200 focus:outline-none transition-all placeholder:text-neutral-300 placeholder:tracking-normal placeholder:font-normal"
                />
                <p className="mt-1.5 text-xs text-neutral-400">
                  Codes change every 30 seconds. If yours is rejected repeatedly,
                  check the clock on the device running the app.
                </p>
              </div>

              {mfaError && (
                <p className="rounded-lg bg-red-50 px-3.5 py-2.5 text-sm text-red-600">{mfaError}</p>
              )}

              <button type="submit" disabled={mfaVerify.isPending || !mfaCode.trim()}
                className="w-full rounded-lg bg-neutral-900 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-neutral-800 focus:outline-none focus:ring-4 focus:ring-neutral-900/20 disabled:opacity-50 disabled:cursor-not-allowed">
                {mfaVerify.isPending ? 'Verifying…' : 'Verify'}
              </button>
            </form>
          </>
        )}

        {/* MFA ENROLMENT REQUIRED — privileged, unenrolled, mode REQUIRED */}
        {flowState === 'mfa_enrolment_required' && (
          <>
            <div className="mb-8">
              <button type="button" onClick={() => setFlowState('idle')}
                className="mb-4 inline-flex items-center gap-1.5 text-sm text-neutral-500 hover:text-neutral-900">
                <ArrowLeft className="h-4 w-4" /> Back to sign in
              </button>
              <div className="mb-3 inline-flex h-10 w-10 items-center justify-center rounded-lg bg-amber-50">
                <ShieldCheck className="h-5 w-5 text-amber-600" />
              </div>
              <h1 className="text-2xl font-semibold tracking-tight text-neutral-900">
                Two-step verification required
              </h1>
              <p className="mt-1.5 text-sm text-neutral-500">
                This account has access to hospital-wide records, so it needs a
                second factor before it can sign in.
              </p>
            </div>

            {/* No self-service route from here on purpose. Enrolment needs a
                session, and this caller does not have one — offering a link
                would be offering a page that redirects straight back. An
                administrator has to enrol them or clear the requirement. */}
            <p className="rounded-lg bg-neutral-50 px-3.5 py-3 text-sm text-neutral-600">
              Ask your administrator to set this up for you. They can enrol your
              account, or reset an existing second factor if you have lost the
              device it was on.
            </p>
          </>
        )}

        {/* 2. REQUEST OTP FLOW */}
        {flowState === 'request_otp' && (
          <>
            <div className="mb-8">
              <h1 className="text-2xl font-semibold tracking-tight text-neutral-900">Forgot password</h1>
              <p className="mt-1.5 text-sm text-neutral-500">We will send a 6-digit verification code to your email.</p>
            </div>

            <form onSubmit={handleRequestSubmit(onRequestOtp)} className="space-y-5" noValidate>
              <div>
                <label htmlFor="email" className="block text-sm font-medium text-neutral-800 mb-1.5">Email address</label>
                <div className="relative">
                  <input id="email" type="email" placeholder="Enter your email"
                    className="w-full rounded-lg border border-neutral-200 bg-white pl-10 pr-3.5 py-2.5 text-sm text-neutral-900 placeholder:text-neutral-400 transition focus:border-neutral-900 focus:outline-none focus:ring-4 focus:ring-neutral-900/5 aria-invalid:border-red-400"
                    aria-invalid={!!requestErrors.email}
                    {...registerRequest('email')} />
                  <Mail className="absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-neutral-400" />
                </div>
                {requestErrors.email && <p role="alert" className="text-xs text-red-600 mt-1.5">{requestErrors.email.message}</p>}
              </div>

              <button type="submit" disabled={actionLoading}
                className="w-full rounded-lg bg-neutral-900 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-neutral-800 focus:outline-none focus:ring-4 focus:ring-neutral-900/20 disabled:opacity-50 disabled:cursor-not-allowed">
                {actionLoading ? 'Sending OTP…' : 'Send OTP'}
              </button>

              <button type="button" onClick={handleBackToLogin}
                className="w-full flex items-center justify-center gap-2 text-xs font-semibold text-neutral-600 hover:text-neutral-900 focus:outline-none py-1.5">
                <ArrowLeft className="h-3.5 w-3.5" /> Back to sign in
              </button>
            </form>
          </>
        )}

        {/* 3. VERIFY OTP FLOW */}
        {flowState === 'verify_otp' && (
          <>
            <div className="mb-8">
              <h1 className="text-2xl font-semibold tracking-tight text-neutral-900">Verify OTP</h1>
              <p className="mt-1.5 text-sm text-neutral-500">Please enter the 6-digit code sent to <strong className="text-neutral-800">{email}</strong>.</p>
            </div>

            <form onSubmit={handleVerifySubmit(onVerifyOtp)} className="space-y-5" noValidate>
              <div>
                <label htmlFor="otp" className="block text-sm font-medium text-neutral-800 mb-1.5">Verification Code</label>
                <div className="relative">
                  <input id="otp" type="text" maxLength={6} placeholder="Enter 6-digit code"
                    className="w-full rounded-lg border border-neutral-200 bg-white pl-10 pr-3.5 py-2.5 text-sm text-neutral-900 placeholder:text-neutral-400 tracking-[0.2em] font-mono transition focus:border-neutral-900 focus:outline-none focus:ring-4 focus:ring-neutral-900/5 aria-invalid:border-red-400"
                    aria-invalid={!!verifyErrors.otp}
                    {...registerVerify('otp')} />
                  <Key className="absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-neutral-400" />
                </div>
                {verifyErrors.otp && <p role="alert" className="text-xs text-red-600 mt-1.5">{verifyErrors.otp.message}</p>}
              </div>

              <button type="submit" disabled={actionLoading}
                className="w-full rounded-lg bg-neutral-900 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-neutral-800 focus:outline-none focus:ring-4 focus:ring-neutral-900/20 disabled:opacity-50 disabled:cursor-not-allowed">
                {actionLoading ? 'Verifying…' : 'Verify Code'}
              </button>

              <button type="button" onClick={handleBackToLogin}
                className="w-full flex items-center justify-center gap-2 text-xs font-semibold text-neutral-600 hover:text-neutral-900 focus:outline-none py-1.5">
                <ArrowLeft className="h-3.5 w-3.5" /> Back to sign in
              </button>
            </form>
          </>
        )}

        {/* 4. RESET PASSWORD FLOW */}
        {flowState === 'reset_password' && (
          <>
            <div className="mb-8">
              <h1 className="text-2xl font-semibold tracking-tight text-neutral-900">Choose new password</h1>
              <p className="mt-1.5 text-sm text-neutral-500">Create a secure password for your account.</p>
            </div>

            <form onSubmit={handleResetSubmit(onResetPassword)} className="space-y-5" noValidate>
              <div>
                <label htmlFor="newPassword" className="block text-sm font-medium text-neutral-800 mb-1.5">New Password</label>
                <div className="relative">
                  <input id="newPassword" type={showNewPassword ? 'text' : 'password'} placeholder="New password"
                    className="w-full rounded-lg border border-neutral-200 bg-white pl-3.5 pr-10 py-2.5 text-sm text-neutral-900 placeholder:text-neutral-400 transition focus:border-neutral-900 focus:outline-none focus:ring-4 focus:ring-neutral-900/5 aria-invalid:border-red-400"
                    aria-invalid={!!resetErrors.newPassword}
                    {...registerReset('newPassword')} />
                  <button type="button" tabIndex={-1} onClick={() => setShowNewPassword(prev => !prev)}
                    className="absolute inset-y-0 right-0 flex items-center pr-3 text-neutral-400 hover:text-neutral-700 focus:outline-none"
                    aria-label={showNewPassword ? 'Hide password' : 'Show password'}>
                    {showNewPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
                {resetErrors.newPassword && <p role="alert" className="text-xs text-red-600 mt-1.5">{resetErrors.newPassword.message}</p>}
              </div>

              <div>
                <label htmlFor="confirmPassword" className="block text-sm font-medium text-neutral-800 mb-1.5">Confirm Password</label>
                <div className="relative">
                  <input id="confirmPassword" type={showConfirmPassword ? 'text' : 'password'} placeholder="Confirm new password"
                    className="w-full rounded-lg border border-neutral-200 bg-white pl-3.5 pr-10 py-2.5 text-sm text-neutral-900 placeholder:text-neutral-400 transition focus:border-neutral-900 focus:outline-none focus:ring-4 focus:ring-neutral-900/5 aria-invalid:border-red-400"
                    aria-invalid={!!resetErrors.confirmPassword}
                    {...registerReset('confirmPassword')} />
                  <button type="button" tabIndex={-1} onClick={() => setShowConfirmPassword(prev => !prev)}
                    className="absolute inset-y-0 right-0 flex items-center pr-3 text-neutral-400 hover:text-neutral-700 focus:outline-none"
                    aria-label={showConfirmPassword ? 'Hide password' : 'Show password'}>
                    {showConfirmPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
                {resetErrors.confirmPassword && <p role="alert" className="text-xs text-red-600 mt-1.5">{resetErrors.confirmPassword.message}</p>}
              </div>

              <button type="submit" disabled={actionLoading}
                className="w-full rounded-lg bg-neutral-900 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-neutral-800 focus:outline-none focus:ring-4 focus:ring-neutral-900/20 disabled:opacity-50 disabled:cursor-not-allowed">
                {actionLoading ? 'Resetting password…' : 'Reset Password'}
              </button>

              <button type="button" onClick={handleBackToLogin}
                className="w-full flex items-center justify-center gap-2 text-xs font-semibold text-neutral-600 hover:text-neutral-900 focus:outline-none py-1.5">
                <ArrowLeft className="h-3.5 w-3.5" /> Back to sign in
              </button>
            </form>
          </>
        )}

        <p className="mt-8 text-xs text-neutral-400">Secured with role-based access control</p>
      </div>

      {showForceLogoutPopup && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="w-full max-w-sm rounded-xl bg-white p-6 shadow-xl">
            <h2 className="text-lg font-semibold text-neutral-900">Active Session Detected</h2>
            <p className="mt-2 text-sm text-neutral-500">
              You are already logged in on another device or browser. Do you want to log out of the other session and sign in here?
            </p>
            <div className="mt-6 flex justify-end gap-3">
              <button
                type="button"
                onClick={() => setShowForceLogoutPopup(false)}
                className="rounded-lg px-4 py-2 text-sm font-medium text-neutral-600 hover:bg-neutral-100"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={confirmForceLogout}
                className="rounded-lg bg-neutral-900 px-4 py-2 text-sm font-medium text-white hover:bg-neutral-800"
              >
                Yes, log out other
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
