import { useState } from 'react'
import { useSpecimens, useSpecimenMutations } from '../../../hooks/diagnostic/useDiagnostic'
import type { Specimen } from '../../../services/diagnostic/specimenApi'

export default function SpecimensConfigPage() {
  const { data: specimens = [], isLoading } = useSpecimens()
  const { saveSpecimen } = useSpecimenMutations()
  const [editingId, setEditingId] = useState<string | null>(null)
  const [formData, setFormData] = useState<Partial<Specimen>>({})

  const startEdit = (s?: Specimen) => {
    if (s) {
      setEditingId(s.id)
      setFormData(s)
    } else {
      setEditingId('new')
      setFormData({ name: '', description: '', status: 'ACTIVE' })
    }
  }

  const cancelEdit = () => {
    setEditingId(null)
    setFormData({})
  }

  const handleSave = () => {
    if (!formData.name) return
    saveSpecimen.mutate(formData as any, {
      onSuccess: () => cancelEdit()
    })
  }

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <div className="flex justify-between items-center">
        <div className="flex items-center gap-4">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">Specimen Configuration</h1>
            <p className="text-sm text-gray-500 mt-1">Manage specimen types for diagnostics</p>
          </div>
          {/* <BackButton /> */}
        </div>
        <button onClick={() => startEdit()} className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-semibold hover:bg-indigo-700 shadow-sm">
          + Add Specimen
        </button>
      </div>

      <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
        <table className="w-full text-sm text-left">
          <thead className="bg-gray-50 text-gray-500 font-semibold text-xs uppercase tracking-wider">
            <tr>
              <th className="px-6 py-4">Name</th>
              <th className="px-6 py-4">Description</th>
              <th className="px-6 py-4 text-center">Status</th>
              <th className="px-6 py-4 text-right">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {isLoading ? (
              <tr><td colSpan={4} className="text-center py-12 text-gray-400">Loading...</td></tr>
            ) : specimens.length === 0 && editingId !== 'new' ? (
              <tr><td colSpan={4} className="text-center py-12 text-gray-400">No specimens configured</td></tr>
            ) : (
              <>
                {editingId === 'new' && (
                  <tr className="bg-indigo-50/30">
                    <td className="px-6 py-3">
                      <input autoFocus type="text" value={formData.name || ''} onChange={e => setFormData({ ...formData, name: e.target.value })}
                        className="w-full px-3 py-1.5 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500" placeholder="e.g. Blood" />
                    </td>
                    <td className="px-6 py-3">
                      <input type="text" value={formData.description || ''} onChange={e => setFormData({ ...formData, description: e.target.value })}
                        className="w-full px-3 py-1.5 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500" placeholder="Optional details..." />
                    </td>
                    <td className="px-6 py-3 text-center">
                      <span className="px-2.5 py-1 rounded-full text-xs font-semibold bg-emerald-100 text-emerald-800">Active</span>
                    </td>
                    <td className="px-6 py-3 text-right space-x-2">
                      <button onClick={cancelEdit} className="text-gray-500 hover:text-gray-700 font-medium">Cancel</button>
                      <button onClick={handleSave} disabled={!formData.name || saveSpecimen.isPending} className="text-indigo-600 hover:text-indigo-800 font-bold disabled:opacity-50">Save</button>
                    </td>
                  </tr>
                )}
                {specimens.map((s) => (
                  editingId === s.id ? (
                    <tr key={s.id} className="bg-indigo-50/30">
                      <td className="px-6 py-3">
                        <input autoFocus type="text" value={formData.name || ''} onChange={e => setFormData({ ...formData, name: e.target.value })}
                          className="w-full px-3 py-1.5 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500" />
                      </td>
                      <td className="px-6 py-3">
                        <input type="text" value={formData.description || ''} onChange={e => setFormData({ ...formData, description: e.target.value })}
                          className="w-full px-3 py-1.5 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500" />
                      </td>
                      <td className="px-6 py-3 text-center">
                        <select value={formData.status} onChange={e => setFormData({ ...formData, status: e.target.value as 'ACTIVE' | 'INACTIVE' })}
                          className="px-2 py-1.5 border border-gray-300 rounded-lg text-xs focus:ring-2 focus:ring-indigo-500">
                          <option value="ACTIVE">Active</option>
                          <option value="INACTIVE">Inactive</option>
                        </select>
                      </td>
                      <td className="px-6 py-3 text-right space-x-2">
                        <button onClick={cancelEdit} className="text-gray-500 hover:text-gray-700 font-medium">Cancel</button>
                        <button onClick={handleSave} disabled={!formData.name || saveSpecimen.isPending} className="text-indigo-600 hover:text-indigo-800 font-bold disabled:opacity-50">Save</button>
                      </td>
                    </tr>
                  ) : (
                    <tr key={s.id} className="hover:bg-gray-50">
                      <td className="px-6 py-4 font-medium text-gray-900">{s.name}</td>
                      <td className="px-6 py-4 text-gray-500">{s.description || '—'}</td>
                      <td className="px-6 py-4 text-center">
                        <span className={`px-2.5 py-1 rounded-full text-xs font-semibold ${s.status === 'ACTIVE' ? 'bg-emerald-100 text-emerald-800' : 'bg-gray-100 text-gray-600'}`}>
                          {s.status === 'ACTIVE' ? 'Active' : 'Inactive'}
                        </span>
                      </td>
                      <td className="px-6 py-4 text-right">
                        <button onClick={() => startEdit(s)} className="text-indigo-600 hover:text-indigo-900 font-medium">Edit</button>
                      </td>
                    </tr>
                  )
                ))}
              </>
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}
