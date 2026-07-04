import { useState, useRef } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { attachmentApi } from '../../../services/attachment/attachmentApi'
import { toast } from '../../../hooks/useToast'

interface PatientAvatarProps {
  patientId: string
  firstName: string
  lastName: string
  /** CSS size classes for the avatar, defaults to 'w-14 h-14' */
  size?: string
  /** Whether the user can upload/change the photo */
  editable?: boolean
}

export default function PatientAvatar({
  patientId,
  firstName,
  lastName,
  size = 'w-14 h-14',
  editable = true,
}: PatientAvatarProps) {
  const qc = useQueryClient()
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [uploading, setUploading] = useState(false)
  const [showModal, setShowModal] = useState(false)

  // Fetch the patient's attachments and find PATIENT_PICTURE
  const { data: pictureUrl } = useQuery({
    queryKey: ['patient-picture', patientId],
    queryFn: async () => {
      const attachments = await attachmentApi.getByPatient(patientId)
      const pic = attachments.find(a => a.attachmentType === 'PATIENT_PICTURE')
      if (pic) {
        return `${attachmentApi.getDownloadUrl(pic.id)}?t=${Date.now()}`
      }
      return null
    },
    enabled: !!patientId,
    staleTime: 5 * 60 * 1000,
  })

  const handleAvatarClick = () => {
    if (uploading) return
    if (pictureUrl) {
      // Photo exists → show view modal
      setShowModal(true)
    } else if (editable) {
      // No photo → directly open file picker
      fileInputRef.current?.click()
    }
  }

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return

    if (!file.type.startsWith('image/')) {
      toast({ title: 'Invalid file', description: 'Please select an image file (JPG, PNG, etc.)', variant: 'destructive' })
      return
    }

    if (file.size > 5 * 1024 * 1024) {
      toast({ title: 'File too large', description: 'Image must be smaller than 5MB', variant: 'destructive' })
      return
    }

    setUploading(true)
    setShowModal(false)
    try {
      await attachmentApi.upload(file, 'PATIENT_PICTURE', undefined, patientId, undefined, undefined)
      qc.invalidateQueries({ queryKey: ['patient-picture', patientId] })
      toast({ title: 'Photo updated', variant: 'success' })
    } catch (err: any) {
      toast({ title: 'Upload failed', description: err.message || 'Could not upload photo', variant: 'destructive' })
    } finally {
      setUploading(false)
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  return (
    <>
      {/* Avatar button */}
      <div className="relative group">
        <button
          type="button"
          onClick={handleAvatarClick}
          disabled={uploading}
          className={`${size} rounded-full overflow-hidden flex items-center justify-center shrink-0 
            border-2 border-gray-200 transition-all duration-200
            ${editable || pictureUrl ? 'cursor-pointer hover:border-blue-400 hover:shadow-md' : 'cursor-default'}
            ${uploading ? 'opacity-60' : ''}
            focus:outline-none focus:ring-2 focus:ring-blue-400 focus:ring-offset-2`}
          title={pictureUrl ? 'View photo' : editable ? 'Upload photo' : undefined}
          aria-label={pictureUrl ? 'View patient photo' : 'Upload patient photo'}
        >
          {pictureUrl ? (
            <img
              src={pictureUrl}
              alt={`${firstName} ${lastName}`}
              className="w-full h-full object-cover"
            />
          ) : (
            /* WhatsApp-style default person silhouette */
            <div className="w-full h-full bg-gray-200 flex items-center justify-center">
              <svg viewBox="0 0 212 212" className="w-full h-full" fill="none">
                <rect width="212" height="212" fill="#D9DBE1" />
                <path
                  d="M106 100c16.569 0 30-13.431 30-30 0-16.569-13.431-30-30-30-16.569 0-30 13.431-30 30 0 16.569 13.431 30 30 30z"
                  fill="#fff"
                />
                <path
                  d="M160 170c0-29.823-24.177-54-54-54s-54 24.177-54 54"
                  fill="#fff"
                />
              </svg>
            </div>
          )}

          {/* Hover overlay */}
          {(editable || pictureUrl) && !uploading && (
            <div className="absolute inset-0 rounded-full bg-black/40 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity duration-200">
              {pictureUrl ? (
                /* Eye icon for view */
                <svg xmlns="http://www.w3.org/2000/svg" className="w-5 h-5 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                  <path strokeLinecap="round" strokeLinejoin="round" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                </svg>
              ) : (
                /* Camera icon for upload */
                <svg xmlns="http://www.w3.org/2000/svg" className="w-5 h-5 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M3 9a2 2 0 012-2h.93a2 2 0 001.664-.89l.812-1.22A2 2 0 0110.07 4h3.86a2 2 0 011.664.89l.812 1.22A2 2 0 0018.07 7H19a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2V9z" />
                  <path strokeLinecap="round" strokeLinejoin="round" d="M15 13a3 3 0 11-6 0 3 3 0 016 0z" />
                </svg>
              )}
            </div>
          )}

          {/* Loading spinner */}
          {uploading && (
            <div className="absolute inset-0 rounded-full bg-black/50 flex items-center justify-center">
              <svg className="animate-spin w-5 h-5 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
              </svg>
            </div>
          )}
        </button>

        {/* Hidden file input */}
        <input
          ref={fileInputRef}
          type="file"
          accept="image/*"
          className="hidden"
          onChange={handleFileChange}
          aria-hidden="true"
        />
      </div>

      {/* Photo view modal */}
      {showModal && pictureUrl && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center"
          onClick={() => setShowModal(false)}
          role="dialog"
          aria-modal="true"
          aria-label="Patient photo"
        >
          {/* Backdrop */}
          <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" />

          {/* Modal content */}
          <div
            className="relative bg-white rounded-2xl shadow-2xl overflow-hidden max-w-md w-full mx-4 animate-in fade-in zoom-in-95 duration-200"
            onClick={e => e.stopPropagation()}
          >
            {/* Close button */}
            <button
              onClick={() => setShowModal(false)}
              className="absolute top-3 right-3 z-10 w-8 h-8 rounded-full bg-black/40 hover:bg-black/60 flex items-center justify-center transition-colors"
              aria-label="Close"
            >
              <svg xmlns="http://www.w3.org/2000/svg" className="w-4 h-4 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>

            {/* Image */}
            <div className="bg-gray-100">
              <img
                src={pictureUrl}
                alt={`${firstName} ${lastName}`}
                className="w-full max-h-[60vh] object-contain"
              />
            </div>

            {/* Footer with patient name and actions */}
            <div className="px-5 py-4 flex items-center justify-between border-t border-gray-100">
              <p className="text-sm font-semibold text-gray-800">{firstName} {lastName}</p>
              {editable && (
                <button
                  onClick={() => fileInputRef.current?.click()}
                  className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-blue-600 hover:text-blue-700 bg-blue-50 hover:bg-blue-100 rounded-lg transition-colors"
                >
                  <svg xmlns="http://www.w3.org/2000/svg" className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M3 9a2 2 0 012-2h.93a2 2 0 001.664-.89l.812-1.22A2 2 0 0110.07 4h3.86a2 2 0 011.664.89l.812 1.22A2 2 0 0018.07 7H19a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2V9z" />
                    <path strokeLinecap="round" strokeLinejoin="round" d="M15 13a3 3 0 11-6 0 3 3 0 016 0z" />
                  </svg>
                  Change Photo
                </button>
              )}
            </div>
          </div>
        </div>
      )}
    </>
  )
}
