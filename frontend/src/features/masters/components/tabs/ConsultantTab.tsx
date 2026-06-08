import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { cn } from '../../../../lib/utils';
import { toast } from '../../../../hooks/useToast';
import { EmptyState, AddButton, Section, Table, LoadingRow, StatusBadge, Field } from '../MasterSharedUI';
import { useConsultantTypes } from '../../../../hooks/consultant/useConsultant';
import { consultantApi, type Consultant } from '../../../../services/consultant/consultantApi';
import { deptCreateApi } from '../../../../services/masters/masterApi';

export default function ConsultantTab() {
  const qc = useQueryClient();
  const navigate = useNavigate();
  const [view, setView] = useState<'grid' | 'table'>('table');
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<Consultant | null>(null);

  const { data: pageData, isLoading } = useQuery({
    queryKey: ['consultants', page, search],
    queryFn: () => consultantApi.getPaginated({ start: page * 10, limit: 10, ...(search ? { value: search } : {}) }),
  });

  const { data: types } = useConsultantTypes();
  const { data: departments = [] } = useQuery({
    queryKey: ['departments'],
    queryFn: deptCreateApi.getAll,
  });

  const consultantsList = pageData?.content ?? [];
  const totalPages = pageData?.totalPages ?? 0;

  const blank = {
    salutation: 'Dr',
    firstName: '',
    lastName: '',
    specialisation: '',
    contact: '',
    email: '',
    consultantType: 'PERMANENT',
    qualification: '',
    address: '',
    departmentId: '',
    status: 'ACTIVE' as 'ACTIVE' | 'INACTIVE',
  };

  const [form, setForm] = useState(blank);
  const [fullName, setFullName] = useState('');
  const [photoFile, setPhotoFile] = useState<File | undefined>(undefined);

  const mut = useMutation({
    mutationFn: () => {
      const payload = { ...form };
      if (editing) {
        return consultantApi.update(editing.id, payload, photoFile);
      } else {
        return consultantApi.create(payload, photoFile);
      }
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['consultants'] });
      reset();
      toast({
        title: editing ? 'Consultant updated successfully' : 'Consultant registered successfully',
        variant: 'success',
      });
    },
    onError: (e: Error) => {
      toast({
        title: 'Error',
        description: e.message,
        variant: 'destructive',
      });
    },
  });

  function reset() {
    setShowForm(false);
    setEditing(null);
    setForm(blank);
    setFullName('');
    setPhotoFile(undefined);
  }

  function startEdit(c: Consultant) {
    setEditing(c);
    setFullName((c.firstName + ' ' + (c.lastName || '')).trim());
    setForm({
      salutation: c.salutation || 'Dr',
      firstName: c.firstName || '',
      lastName: c.lastName || '',
      specialisation: c.specialisation || '',
      contact: c.contact || '',
      email: c.email || '',
      consultantType: c.consultantType || 'PERMANENT',
      qualification: c.qualification || '',
      address: c.address || '',
      departmentId: c.departmentId || '',
      status: (c.status as any) || 'ACTIVE',
    });
    setPhotoFile(undefined);
    setShowForm(true);
  }

  const handleNameChange = (val: string) => {
    setFullName(val);
    const parts = val.trim().split(/\s+/);
    const first = parts[0] || '';
    const last = parts.slice(1).join(' ') || '';
    setForm((f) => ({ ...f, firstName: first, lastName: last }));
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      setPhotoFile(e.target.files[0]);
    }
  };

  return (
    <Section
      title="Consultants"
      description="Hospital doctors and consulting medical specialists"
      action={
        <div className="flex gap-4 items-center">
          {/* View toggle switcher */}
          <div className="flex items-center gap-1 bg-gray-100 p-1 rounded-xl border border-gray-200/80">
            <button
              onClick={() => setView('grid')}
              className={cn(
                'flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold rounded-lg transition-all duration-150',
                view === 'grid'
                  ? 'bg-white text-neutral-600 shadow-sm border border-gray-150 font-bold'
                  : 'text-gray-500 hover:text-gray-900'
              )}
            >
              <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z" />
              </svg>
              Grid
            </button>
            <button
              onClick={() => setView('table')}
              className={cn(
                'flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold rounded-lg transition-all duration-150',
                view === 'table'
                  ? 'bg-white text-neutral-600 shadow-sm border border-gray-150 font-bold'
                  : 'text-gray-500 hover:text-gray-900'
              )}
            >
              <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M4 6h16M4 12h16M4 18h16" />
              </svg>
              Table
            </button>
          </div>

          <input
            type="text"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
            className="px-3 py-2 border border-gray-200 rounded-lg text-sm bg-gray-50 focus:outline-none focus:ring-2 focus:ring-neutral-500 focus:bg-white transition-all w-64"
          />
          <AddButton label="ADD CONSULTANT" onClick={() => { reset(); setShowForm(true); }} />
        </div>
      }
    >
      {showForm && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 animate-in fade-in duration-150 p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-4xl overflow-hidden border border-gray-100 flex flex-col max-h-[95vh] animate-in zoom-in-95 duration-150">
            {/* Modal Header */}
            <div className="bg-gradient-to-r from-neutral-600 to-neutral-600 px-6 py-4 flex justify-between items-center text-white">
              <h3 className="text-lg font-bold tracking-tight">
                {editing ? 'Update Consultant' : 'Create Consultant'}
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
            <div className="p-6 overflow-y-auto space-y-6 flex-1 bg-gray-50/50">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-x-6 gap-y-4 bg-white p-5 rounded-xl border border-gray-150 shadow-sm">
                
                <Field label="Consultant Salutation *">
                  <select
                    value={form.salutation}
                    onChange={(e) => setForm((f) => ({ ...f, salutation: e.target.value }))}
                    className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm bg-gray-50 focus:outline-none focus:ring-2 focus:ring-neutral-500 focus:bg-white transition-all"
                  >
                    <option>Dr</option>
                    <option>Mr</option>
                    <option>Mrs</option>
                    <option>Ms</option>
                  </select>
                </Field>

                <Field label="Full Name *">
                  <input
                    required
                    type="text"
                    value={fullName}
                    onChange={(e) => handleNameChange(e.target.value)}
                    className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm bg-gray-50 focus:outline-none focus:ring-2 focus:ring-neutral-500 focus:bg-white transition-all"
                  />
                </Field>

                <Field label="Qualification">
                  <input
                    type="text"
                    value={form.qualification}
                    onChange={(e) => setForm((f) => ({ ...f, qualification: e.target.value }))}
                    className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm bg-gray-50 focus:outline-none focus:ring-2 focus:ring-neutral-500 focus:bg-white transition-all"
                  />
                </Field>

                <Field label="Contact No">
                  <input
                    type="text"
                    value={form.contact}
                    onChange={(e) => setForm((f) => ({ ...f, contact: e.target.value }))}
                    className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm bg-gray-50 focus:outline-none focus:ring-2 focus:ring-neutral-500 focus:bg-white transition-all"
                  />
                </Field>

                <div className="col-span-1 md:col-span-2">
                  <Field label="Address">
                    <textarea
                      rows={2}
                      value={form.address}
                      onChange={(e) => setForm((f) => ({ ...f, address: e.target.value }))}
                      className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm bg-gray-50 focus:outline-none focus:ring-2 focus:ring-neutral-500 focus:bg-white transition-all resize-none"
                    />
                  </Field>
                </div>

                <Field label="Department">
                  <select
                    value={form.departmentId}
                    onChange={(e) => setForm((f) => ({ ...f, departmentId: e.target.value }))}
                    className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm bg-gray-50 focus:outline-none focus:ring-2 focus:ring-neutral-500 focus:bg-white transition-all"
                  >
                    <option value="">Select Department</option>
                    {departments.filter((dept: any) => dept.departmentType === 'Clinical').map((dept: any) => (
                      <option key={dept.id} value={dept.id}>
                        {dept.name}
                      </option>
                    ))}
                  </select>
                </Field>

                <Field label="Consultant Type">
                  <select
                    value={form.consultantType}
                    onChange={(e) => setForm((f) => ({ ...f, consultantType: e.target.value }))}
                    className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm bg-gray-50 focus:outline-none focus:ring-2 focus:ring-neutral-500 focus:bg-white transition-all"
                  >
                    {types?.map((t) => (
                      <option key={t} value={t}>
                        {t}
                      </option>
                    ))}
                  </select>
                </Field>

                <Field label="Signature">
                  <div className="flex-1 flex flex-col space-y-2">
                    <input
                      type="file"
                      accept="image/*"
                      onChange={handleFileChange}
                      className="text-xs text-gray-500 file:mr-3 file:py-1.5 file:px-3 file:rounded-md file:border file:border-gray-200 file:text-xs file:font-semibold file:bg-white hover:file:bg-gray-50 transition-all cursor-pointer"
                    />
                    {photoFile && (
                      <div className="flex flex-col gap-1 mt-1">
                        <span className="text-[10px] text-green-600 font-semibold">
                          ✓ {photoFile.name} selected
                        </span>
                        <div className="h-14 w-40 border border-dashed border-gray-300 rounded-lg p-1 bg-gray-50 overflow-hidden flex items-center justify-center">
                          <img
                            src={URL.createObjectURL(photoFile)}
                            alt="Signature Preview"
                            className="h-full object-contain"
                          />
                        </div>
                      </div>
                    )}
                    {editing?.photoAttachmentId && !photoFile && (
                      <div className="flex flex-col gap-1 mt-1">
                        <span className="text-[10px] text-gray-400 font-bold uppercase tracking-wider">
                          Current Saved Signature
                        </span>
                        <div className="h-14 w-40 border border-gray-200 rounded-lg p-1 bg-white overflow-hidden flex items-center justify-center">
                          <img
                            src={`/api/attachment/download/${editing.photoAttachmentId}`}
                            alt="Current Signature"
                            className="h-full object-contain"
                          />
                        </div>
                      </div>
                    )}
                  </div>
                </Field>

                {editing && (
                  <Field label="Status">
                    <select
                      value={form.status}
                      onChange={(e) => setForm((f) => ({ ...f, status: e.target.value as any }))}
                      className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm bg-gray-50 focus:outline-none focus:ring-2 focus:ring-neutral-500 focus:bg-white transition-all"
                    >
                      <option value="ACTIVE">Active</option>
                      <option value="INACTIVE">Inactive</option>
                    </select>
                  </Field>
                )}

              </div>
            </div>

            {/* Modal Footer */}
            <div className="px-6 py-4 border-t bg-white flex justify-end gap-3">
              <button
                onClick={reset}
                className="px-4 py-2 border border-gray-200 text-sm text-gray-600 rounded-lg hover:bg-white transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={() => mut.mutate()}
                disabled={!form.firstName || mut.isPending}
                className="px-5 py-2 bg-neutral-600 text-white text-sm font-semibold rounded-lg hover:bg-neutral-700 disabled:opacity-50 transition-colors"
              >
                {mut.isPending ? (editing ? 'Updating…' : 'Creating…') : (editing ? 'Update Consultant' : 'Create Consultant')}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Grid view */}
      {view === 'grid' && (
        <div className="bg-white rounded-xl border border-gray-200 p-6 shadow-sm mt-4">
          {isLoading ? (
            <div className="py-12 text-center text-gray-400 text-sm">Loading consultants…</div>
          ) : consultantsList.length === 0 ? (
            <div className="py-12 text-center text-gray-400 text-sm">No consultants found</div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
              {consultantsList.map((c) => (
                <div
                  key={c.id}
                  className="bg-white border border-gray-200 rounded-2xl p-5 hover:shadow-lg transition-all group relative overflow-hidden"
                >
                  {/* Edit floating button on hover */}
                  <div className="absolute top-3 right-3 opacity-0 group-hover:opacity-100 transition-opacity">
                    <button
                      onClick={() => startEdit(c)}
                      className="p-1.5 text-neutral-600 hover:bg-neutral-50 rounded-lg border border-transparent hover:border-neutral-100 transition-all shadow-sm bg-white"
                    >
                      <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          strokeWidth={2}
                          d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z"
                        />
                      </svg>
                    </button>
                  </div>

                  <div className="flex items-start gap-4">
                    <div className="w-12 h-12 rounded-2xl bg-neutral-50 flex items-center justify-center shrink-0 border border-neutral-100 overflow-hidden">
                      {c.photoAttachmentId ? (
                        <img
                          src={`/api/attachment/download/${c.photoAttachmentId}`}
                          alt="Signature"
                          className="w-full h-full object-contain bg-white"
                        />
                      ) : (
                        <span className="text-neutral-700 font-bold text-lg">
                          {c.firstName[0]}
                          {c.lastName ? c.lastName[0] : ''}
                        </span>
                      )}
                    </div>
                    <div className="flex-1 min-w-0">
                      <h5 className="font-bold text-gray-900 truncate">
                        {(c.salutation ? c.salutation + ' ' : '') + c.firstName + ' ' + (c.lastName || '')}
                      </h5>
                      <p className="text-xs text-neutral-600 font-medium">
                        {c.specialisation || 'General Practice'}
                      </p>
                      {c.qualification && (
                        <p className="text-[10px] text-gray-400 font-medium truncate mt-0.5">
                          {c.qualification}
                        </p>
                      )}
                      <div className="mt-3 flex items-center gap-2">
                        <span className="px-2 py-0.5 rounded-md bg-gray-100 text-[10px] font-bold text-gray-500 uppercase tracking-tight">
                          {c.consultantType}
                        </span>
                        <StatusBadge active={c.status === 'ACTIVE' || (c.status as any) === 1} />
                      </div>
                    </div>
                  </div>

                  <div className="mt-5 pt-4 border-t border-gray-100 flex items-center justify-between">
                    <button
                      onClick={() =>
                        navigate(
                          `/settings/consultants/${c.id}/slots?name=${encodeURIComponent(
                            (c.salutation ? c.salutation + ' ' : '') + c.firstName + ' ' + (c.lastName || '')
                          )}`
                        )
                      }
                      className="flex items-center gap-2 text-xs font-bold text-gray-600 hover:text-neutral-600 transition-colors"
                    >
                      <svg className="w-4 h-4 text-neutral-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          strokeWidth={2}
                          d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"
                        />
                      </svg>
                      Manage Slots
                    </button>
                    <div className="flex items-center gap-3 text-gray-400">
                      {c.contact && <span title={c.contact}>📞</span>}
                      {c.email && <span title={c.email}>✉️</span>}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Table view */}
      {view === 'table' && (
        <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden flex flex-col mt-4">
          <Table
            headers={['S.NO', 'CONSULTANT', 'CONTACT NO', 'STATUS', 'ACTIONS']}
            className="border-0 shadow-none rounded-none border-b border-gray-200"
          >
            {isLoading ? (
              <LoadingRow />
            ) : consultantsList.length === 0 ? (
              <EmptyState label="consultants" />
            ) : (
              consultantsList.map((c: any, i: number) => (
                <tr key={c.id} className="hover:bg-gray-50 transition-colors duration-150">
                  <td className="px-4 py-4 font-semibold text-gray-500 text-sm">
                    {page * 10 + i + 1}
                  </td>
                  <td className="px-4 py-4 text-left">
                    <div className="flex items-center gap-3 pl-4">
                      <div className="w-9 h-9 rounded-xl bg-neutral-50/80 flex items-center justify-center border border-neutral-100/50 shrink-0 overflow-hidden">
                        {c.photoAttachmentId ? (
                          <img
                            src={`/api/attachment/download/${c.photoAttachmentId}`}
                            alt="Signature"
                            className="w-full h-full object-contain bg-white"
                          />
                        ) : (
                          <span className="text-neutral-600 font-bold text-sm">
                            {c.firstName[0]}
                            {c.lastName ? c.lastName[0] : ''}
                          </span>
                        )}
                      </div>
                      <div className="flex flex-col items-start">
                        <div className="font-bold text-gray-900 text-sm">
                          {(c.salutation ? c.salutation + ' ' : '') + c.firstName + ' ' + (c.lastName || '')}
                        </div>
                        <div className="text-xs text-gray-500 font-medium">
                          {c.specialisation || 'General Practice'}
                          {c.qualification ? ` · ${c.qualification}` : ''}
                        </div>
                      </div>
                    </div>
                  </td>
                  <td className="px-4 py-4 text-gray-700 text-sm font-medium">
                    {c.contact || '—'}
                  </td>
                  <td className="px-4 py-4">
                    <StatusBadge active={c.status === 'ACTIVE' || c.status === 1} />
                  </td>
                  <td className="px-4 py-4">
                    <div className="flex items-center justify-center gap-3">
                      {/* Edit Button */}
                      <button
                        onClick={() => startEdit(c)}
                        className="p-1 text-neutral-600 hover:text-neutral-800 hover:bg-neutral-50 rounded-lg transition-colors border border-transparent hover:border-neutral-100"
                        title="Edit Profile"
                      >
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            strokeWidth="2"
                            d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z"
                          />
                        </svg>
                      </button>

                      {/* Manage Slots */}
                      <button
                        onClick={() =>
                          navigate(
                            `/settings/consultants/${c.id}/slots?name=${encodeURIComponent(
                              (c.salutation ? c.salutation + ' ' : '') + c.firstName + ' ' + (c.lastName || '')
                            )}`
                          )
                        }
                        className="p-1 text-green-600 hover:text-green-800 hover:bg-green-50 rounded-lg transition-colors border border-transparent hover:border-green-100"
                        title="Manage Slots"
                      >
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            strokeWidth="2"
                            d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"
                          />
                        </svg>
                      </button>
                    </div>
                  </td>
                </tr>
              ))
            )}
          </Table>
        </div>
      )}

      {/* Pagination component */}
      <div className="px-6 py-4 bg-gray-50 flex items-center justify-between border border-t-0 border-gray-200 rounded-b-xl">
        <div className="text-xs text-gray-500">
          Page <span className="font-medium text-gray-900">{page + 1}</span> of{' '}
          <span className="font-medium text-gray-900">{totalPages || 1}</span>
          <span className="ml-2">· {pageData?.totalElements || 0} total records</span>
        </div>
        <div className="flex items-center gap-1">
          <button
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={page === 0 || isLoading}
            className="p-1.5 text-gray-500 hover:text-neutral-600 hover:bg-neutral-50 rounded transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 19l-7-7 7-7" />
            </svg>
          </button>
          {Array.from({ length: Math.min(5, totalPages) }, (_, i) => {
            let pageNum = i;
            if (totalPages > 5 && page > 2) pageNum = Math.min(page - 2 + i, totalPages - 5 + i);
            return (
              <button
                key={pageNum}
                onClick={() => setPage(pageNum)}
                className={cn(
                  'min-w-[32px] h-8 flex items-center justify-center rounded text-xs font-semibold transition-all',
                  page === pageNum ? 'bg-neutral-600 text-white shadow-sm' : 'text-gray-600 hover:bg-gray-100'
                )}
              >
                {pageNum + 1}
              </button>
            );
          })}
          <button
            onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
            disabled={page >= totalPages - 1 || isLoading}
            className="p-1.5 text-gray-500 hover:text-neutral-600 hover:bg-neutral-50 rounded transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 5l7 7-7 7" />
            </svg>
          </button>
        </div>
      </div>
    </Section>
  );
}
