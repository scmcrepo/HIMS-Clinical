import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { cn } from '../../../../lib/utils';
import { toast } from '../../../../hooks/useToast';
import { inputCls, Section, AddButton, StatusBadge } from '../MasterSharedUI';
import { userApi, roleApi, CreateUserCmd, UserRecord } from '../../../../services/user/userApi';
import { deptCreateApi } from '../../../../services/masters/masterApi';
import { useAuthStore } from '../../../../store/authStore';

export default function UsersTab() {
  const qc = useQueryClient()
  const { data: users = [], isLoading } = useQuery({ queryKey: ['users'], queryFn: userApi.getAll })
  const { data: roles = [] } = useQuery({ queryKey: ['roles'], queryFn: roleApi.getAll })
  const { data: departments = [] } = useQuery({ queryKey: ['departments'], queryFn: deptCreateApi.getAll })
  const loggedInUser = useAuthStore(s => s.user)

  const [searchValue, setSearchValue] = useState('')
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<UserRecord | null>(null)
  const [confirmPassword, setConfirmPassword] = useState('')

  // Password reset modal state
  const [resetPasswordUser, setResetPasswordUser] = useState<UserRecord | null>(null)
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmNewPassword, setConfirmNewPassword] = useState('')
  const [isCurrentPasswordValid, setIsCurrentPasswordValid] = useState<boolean | null>(null)

  const handleCheckCurrentPassword = async (pwd: string) => {
    if (!pwd) {
      setIsCurrentPasswordValid(null)
      return
    }
    try {
      console.log("[Password Check] Logged in user:", loggedInUser?.username, "entered password:", pwd)
      const isValid = await userApi.checkCurrentPassword(pwd)
      console.log("[Password Check] Verification result:", isValid)
      setIsCurrentPasswordValid(isValid)
    } catch (err: any) {
      console.error("Password check failed:", err)
      setIsCurrentPasswordValid(false)
      toast({
        title: "Error checking password",
        description: err.response?.data?.message || err.message,
        variant: "destructive"
      })
    }
  }

  const blank: CreateUserCmd = {
    username: '',
    password: '',
    firstName: '',
    lastName: '',
    email: '',
    roleIds: [],
    departmentIds: [],
    salutation: '',
    phoneNo: '',
    showCasesheet: false
  }

  const [form, setForm] = useState<CreateUserCmd>(blank)

  const mut = useMutation({
    mutationFn: () => editing ? userApi.update(editing.id, form) : userApi.create(form),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['users'] })
      reset()
      toast({ title: editing ? 'User updated successfully' : 'User created successfully', variant: 'success' })
    },
    onError: (e: any) => toast({ title: 'Error', description: e.response?.data?.message || e.message, variant: 'destructive' }),
  })

  const resetPassMut = useMutation({
    mutationFn: () => userApi.resetPassword(resetPasswordUser!.id, newPassword),
    onSuccess: () => {
      closeResetPasswordModal()
      toast({ title: 'Password updated successfully', variant: 'success' })
    },
    onError: (e: any) => toast({ title: 'Error', description: e.response?.data?.message || e.message, variant: 'destructive' }),
  })

  function reset() {
    setShowForm(false)
    setEditing(null)
    setForm(blank)
    setConfirmPassword('')
  }

  function closeResetPasswordModal() {
    setResetPasswordUser(null)
    setCurrentPassword('')
    setNewPassword('')
    setConfirmNewPassword('')
    setIsCurrentPasswordValid(null)
  }

  function startEdit(u: UserRecord) {
    setEditing(u)
    setForm({
      username: u.username,
      firstName: u.firstName,
      lastName: u.lastName,
      email: u.email ?? '',
      roleIds: u.roles.map(r => r.id),
      departmentIds: u.departmentIds || [],
      salutation: u.salutation || '',
      phoneNo: u.phoneNo || '',
      showCasesheet: u.showCasesheet
    })
    setConfirmPassword('')
    setShowForm(true)
  }

  const isValid = (() => {
    if (!form.username || form.username.length < 3) return false;
    if (!form.firstName || !form.lastName) return false;
    if (!form.roleIds || form.roleIds.length === 0) return false;
    if (!editing) {
      if (!form.password || form.password.length < 6) return false;
      if (form.password !== confirmPassword) return false;
    }
    return true;
  })()

  const filteredUsers = users.filter(u => {
    if (!searchValue) return true
    const term = searchValue.toLowerCase()
    return (
      u.username.toLowerCase().includes(term) ||
      `${u.firstName} ${u.lastName}`.toLowerCase().includes(term) ||
      (u.email && u.email.toLowerCase().includes(term))
    )
  })

  return (
    <Section
      title="Users"
      description="System user accounts with role assignments"
      action={
        <div className="flex gap-4 items-center">
          <input
            type="text"
            className="px-3 py-1.5 border border-gray-200 rounded-lg text-sm bg-gray-50 focus:outline-none focus:ring-2 focus:ring-neutral-500 focus:bg-white transition-all w-64"
            value={searchValue}
            onChange={e => setSearchValue(e.target.value)}
          />
          <AddButton label="Add User" onClick={() => { reset(); setShowForm(true) }} />
        </div>
      }
    >
      {/* Add / Edit User Modal */}
      {showForm && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 animate-in fade-in duration-150 p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-4xl overflow-hidden border border-gray-100 flex flex-col max-h-[90vh] animate-in zoom-in-95 duration-150">
            
            {/* Modal Header */}
            <div className="bg-gradient-to-r from-neutral-600 to-neutral-600 px-6 py-4 flex justify-between items-center text-white">
              <h3 className="text-lg font-bold tracking-tight">
                {editing ? 'Edit User' : 'Add User'}
              </h3>
              <button
                onClick={reset}
                className="text-white/80 hover:text-white hover:bg-white/10 p-1.5 rounded-lg transition-colors focus:outline-none focus:ring-2 focus:ring-white/20"
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            {/* Modal Body */}
            <div className="p-6 overflow-y-auto space-y-4 flex-1 bg-gray-50/50">
              <div className="space-y-4 bg-white p-6 rounded-xl border border-gray-150 shadow-sm">
                
                {/* Dummy inputs to prevent browser autofill */}
                <input type="text" style={{ display: 'none' }} tabIndex={-1} aria-hidden="true" />
                <input type="password" style={{ display: 'none' }} tabIndex={-1} aria-hidden="true" />

                {/* Name */}
                <div className="grid grid-cols-[120px_1fr] items-center gap-4">
                  <label className="text-sm font-bold text-gray-700 text-right">Name</label>
                  <div className="flex gap-2">
                    <select
                      className={cn(inputCls, "w-32")}
                      value={form.salutation || ''}
                      onChange={e => setForm(f => ({ ...f, salutation: e.target.value }))}
                    >
                      <option value="">Prefix</option>
                      <option value="Mr">Mr</option>
                      <option value="Ms">Ms</option>
                      <option value="Mrs">Mrs</option>
                      <option value="Dr">Dr</option>
                      <option value="Prof">Prof</option>
                    </select>
                    <input
                      type="text"
                      className={cn(inputCls, "flex-1")}
                      value={form.firstName}
                      onChange={e => setForm(f => ({ ...f, firstName: e.target.value }))}
                    />
                    <input
                      type="text"
                      className={cn(inputCls, "flex-1")}
                      value={form.lastName}
                      onChange={e => setForm(f => ({ ...f, lastName: e.target.value }))}
                    />
                  </div>
                </div>

                {/* Username */}
                <div className="grid grid-cols-[120px_1fr] items-center gap-4">
                  <label className="text-sm font-bold text-gray-700 text-right">Username</label>
                  <div className="w-1/2">
                    <input
                      type="text"
                      className={inputCls}
                      value={form.username}
                      onChange={e => setForm(f => ({ ...f, username: e.target.value }))}
                      disabled={!!editing}
                      autoComplete="off"
                    />
                  </div>
                </div>

                {/* Password / Confirm Password */}
                {!editing && (
                  <div className="grid grid-cols-2 gap-8">
                    <div className="grid grid-cols-[120px_1fr] items-center gap-4">
                      <label className="text-sm font-bold text-gray-700 text-right">Password</label>
                      <input
                        type="password"
                        className={inputCls}
                        value={form.password || ''}
                        onChange={e => setForm(f => ({ ...f, password: e.target.value }))}
                        autoComplete="new-password"
                      />
                    </div>
                    <div className="grid grid-cols-[130px_1fr] items-center gap-4">
                      <label className="text-sm font-bold text-gray-700 text-right">Confirm Password</label>
                      <input
                        type="password"
                        className={inputCls}
                        value={confirmPassword}
                        onChange={e => setConfirmPassword(e.target.value)}
                        autoComplete="new-password"
                      />
                    </div>
                  </div>
                )}

                {/* Email / Contact No */}
                <div className="grid grid-cols-2 gap-8">
                  <div className="grid grid-cols-[120px_1fr] items-center gap-4">
                    <label className="text-sm font-bold text-gray-700 text-right">Email</label>
                    <input
                      type="email"
                      className={inputCls}
                      value={form.email || ''}
                      onChange={e => setForm(f => ({ ...f, email: e.target.value }))}
                    />
                  </div>
                  <div className="grid grid-cols-[130px_1fr] items-center gap-4">
                    <label className="text-sm font-bold text-gray-700 text-right">Contact No</label>
                    <input
                      type="text"
                      className={inputCls}
                      value={form.phoneNo || ''}
                      onChange={e => setForm(f => ({ ...f, phoneNo: e.target.value }))}
                    />
                  </div>
                </div>

                {/* Roles */}
                <div className="grid grid-cols-[120px_1fr] items-start gap-4">
                  <label className="text-sm font-bold text-gray-700 text-right mt-2">Roles</label>
                  <div className="w-1/2">
                    <select
                      multiple
                      className={cn(inputCls, "h-36")}
                      value={form.roleIds}
                      onChange={e => {
                        const selected = Array.from(e.target.selectedOptions, o => o.value);
                        setForm(f => ({ ...f, roleIds: selected }));
                      }}
                    >
                      {roles.map(r => (
                        <option key={r.id} value={r.id}>
                          {r.name}
                        </option>
                      ))}
                    </select>
                    <p className="text-[10px] text-gray-400 mt-1">Hold Ctrl/Cmd to select multiple</p>
                  </div>
                </div>

                {/* Departments */}
                <div className="grid grid-cols-[120px_1fr] items-start gap-4">
                  <label className="text-sm font-bold text-gray-700 text-right mt-2">Departments</label>
                  <div className="w-1/2">
                    <select
                      multiple
                      className={cn(inputCls, "h-36")}
                      value={form.departmentIds || []}
                      onChange={e => {
                        const selected = Array.from(e.target.selectedOptions, o => o.value);
                        setForm(f => ({ ...f, departmentIds: selected }));
                      }}
                    >
                      {departments.map(d => (
                        <option key={d.id} value={d.id}>
                          {d.name}
                        </option>
                      ))}
                    </select>
                    <p className="text-[10px] text-gray-400 mt-1">Hold Ctrl/Cmd to select multiple</p>
                  </div>
                </div>

              </div>
            </div>

            {/* Modal Footer */}
            <div className="px-6 py-4 border-t bg-white flex justify-end gap-3">
              <button
                type="button"
                onClick={reset}
                className="px-4 py-2 border border-gray-200 text-sm text-gray-600 rounded-lg hover:bg-white transition-colors uppercase focus:outline-none"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => mut.mutate()}
                disabled={mut.isPending || !isValid}
                className="px-5 py-2 bg-neutral-600 text-white text-sm font-semibold rounded-lg hover:bg-neutral-700 disabled:opacity-50 transition-colors uppercase focus:outline-none"
              >
                {mut.isPending ? (editing ? 'Updating…' : 'Creating…') : (editing ? 'Update User' : 'Create User')}
              </button>
            </div>

          </div>
        </div>
      )}

      {/* Password Reset Modal */}
      {resetPasswordUser && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 animate-in fade-in duration-150 p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-xl overflow-hidden border border-gray-100 flex flex-col animate-in zoom-in-95 duration-150">
            
            {/* Header */}
            <div className="bg-gradient-to-r from-neutral-600 to-neutral-600 px-6 py-4 flex justify-between items-center text-white">
              <h3 className="text-lg font-bold tracking-tight">Update Password</h3>
              <button
                onClick={closeResetPasswordModal}
                className="text-white/80 hover:text-white hover:bg-white/10 p-1.5 rounded-lg transition-colors focus:outline-none"
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            {/* Body */}
            <div className="p-6 space-y-4 bg-gray-50/50">
              <div className="space-y-4 bg-white p-5 rounded-xl border border-gray-150 shadow-sm">
                
                {/* Dummy inputs to prevent browser autofill */}
                <input type="text" style={{ display: 'none' }} tabIndex={-1} aria-hidden="true" />
                <input type="password" style={{ display: 'none' }} tabIndex={-1} aria-hidden="true" />

                {/* Current Password */}
                <div className="grid grid-cols-[160px_1fr] items-center gap-4">
                  <label className="text-sm font-bold text-gray-700 text-right">Current Password</label>
                  <div>
                    <input
                      type="password"
                      className={inputCls}
                      value={currentPassword}
                      onChange={e => {
                        setCurrentPassword(e.target.value);
                        setIsCurrentPasswordValid(null);
                      }}
                      onBlur={e => handleCheckCurrentPassword(e.target.value)}
                      autoComplete="new-password"
                    />
                    {isCurrentPasswordValid === false && (
                      <p className="text-red-500 text-xs mt-1">Invalid Current Password !</p>
                    )}
                    <span className="text-[10px] text-gray-400 mt-1 block">
                      Confirming password for logged-in user: <span className="font-semibold text-gray-600">{loggedInUser?.username}</span>
                    </span>
                  </div>
                </div>

                {/* Password */}
                <div className="grid grid-cols-[160px_1fr] items-center gap-4">
                  <label className="text-sm font-bold text-gray-700 text-right">Password</label>
                  <div>
                    <input
                      type="password"
                      className={inputCls}
                      value={newPassword}
                      onChange={e => setNewPassword(e.target.value)}
                      autoComplete="new-password"
                    />
                  </div>
                </div>

                {/* Confirm Password */}
                <div className="grid grid-cols-[160px_1fr] items-center gap-4">
                  <label className="text-sm font-bold text-gray-700 text-right">Confirm Password</label>
                  <div>
                    <input
                      type="password"
                      className={inputCls}
                      value={confirmNewPassword}
                      onChange={e => setConfirmNewPassword(e.target.value)}
                      autoComplete="new-password"
                    />
                    {newPassword && confirmNewPassword && newPassword !== confirmNewPassword && (
                      <p className="text-red-500 text-xs mt-1">ConfirmPassword not matched !</p>
                    )}
                  </div>
                </div>

              </div>
            </div>

            {/* Footer */}
            <div className="px-6 py-4 border-t bg-white flex justify-end gap-3">
              <button
                type="button"
                onClick={closeResetPasswordModal}
                className="px-4 py-2 border border-gray-200 text-sm text-gray-600 rounded-lg hover:bg-white transition-colors uppercase focus:outline-none"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => resetPassMut.mutate()}
                disabled={
                  !currentPassword ||
                  isCurrentPasswordValid !== true ||
                  !newPassword ||
                  newPassword.length < 6 ||
                  newPassword !== confirmNewPassword ||
                  resetPassMut.isPending
                }
                className="px-5 py-2 bg-neutral-600 text-white text-sm font-semibold rounded-lg hover:bg-neutral-700 disabled:opacity-50 transition-colors uppercase focus:outline-none"
              >
                {resetPassMut.isPending ? 'Updating...' : 'Update Password'}
              </button>
            </div>

          </div>
        </div>
      )}

      {/* Main Table */}
      <div className="bg-white border border-gray-200 rounded-xl overflow-hidden shadow-sm mt-4">
        <table className="w-full text-sm text-left">
          <thead>
            <tr className="bg-gray-50 border-b border-gray-100 text-xs font-semibold text-gray-600 uppercase tracking-wider">
              <th className="px-6 py-3 text-center w-16">S.NO</th>
              <th className="px-6 py-3 text-left">User Name</th>
              <th className="px-6 py-3 text-left">Name</th>
              <th className="px-6 py-3 text-left">Role</th>
              <th className="px-6 py-3 text-center w-28">STATUS</th>
              <th className="px-6 py-3 text-center w-32">ACTION</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100 bg-white">
            {isLoading ? (
              <tr>
                <td colSpan={6} className="px-6 py-10 text-center text-gray-400 text-sm">
                  Loading…
                </td>
              </tr>
            ) : filteredUsers.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-6 py-10 text-center text-gray-400 text-sm">
                  No users found
                </td>
              </tr>
            ) : (
              filteredUsers.map((u, idx) => (
                <tr key={u.id} className="hover:bg-gray-50/50 transition-colors">
                  <td className="px-6 py-3.5 text-center text-xs font-bold text-gray-400">
                    {idx + 1}
                  </td>
                  <td className="px-6 py-3.5 text-left font-mono text-sm text-gray-800">
                    {u.username}
                  </td>
                  <td className="px-6 py-3.5 text-left font-medium text-gray-900">
                    {u.salutation ? `${u.salutation} ` : ''}{u.firstName} {u.lastName}
                  </td>
                  <td className="px-6 py-3.5 text-left">
                    <span className="text-xs font-semibold text-gray-600">
                      {u.roles.map(r => r.name).join(', ')}
                    </span>
                  </td>
                  <td className="px-6 py-3.5 text-center">
                    <StatusBadge active={u.status === 'ACTIVE'} />
                  </td>
                  <td className="px-6 py-3.5 text-center">
                    <div className="flex justify-center gap-1.5">
                      <button
                        onClick={() => startEdit(u)}
                        className="p-1.5 border border-gray-300 bg-white rounded hover:bg-gray-100 active:bg-gray-200 text-gray-700 transition-colors focus:outline-none"
                        title="Update User"
                      >
                        <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z" />
                        </svg>
                      </button>
                      <button
                        onClick={() => {
                          setResetPasswordUser(u)
                          setCurrentPassword('')
                          setNewPassword('')
                          setConfirmNewPassword('')
                          setIsCurrentPasswordValid(null)
                        }}
                        className="p-1.5 border border-gray-300 bg-white rounded hover:bg-gray-100 active:bg-gray-200 text-gray-700 transition-colors focus:outline-none"
                        title="Update Password"
                      >
                        <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
                        </svg>
                      </button>
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </Section>
  )
}