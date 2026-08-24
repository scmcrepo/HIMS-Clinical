/**
 * English string pack. WO-019 §4.6 — every user-facing string goes through t()
 * from the first commit, so adding Tamil and Hindi later is a translation job
 * rather than a refactor of every screen.
 */
export const en = {
  "app.name": "HIMS Patient",

  "login.title": "Welcome",
  "login.subtitle": "Enter your mobile number to continue",
  "login.mobileLabel": "Mobile number",
  "login.continue": "Continue",
  "login.privacyNote":
    "We send a one-time code to confirm this number is yours. Your health records are only shown after the code is verified.",

  "otp.title": "Enter the code",
  "otp.subtitle": "We sent a 6-digit code to {mobile}",
  "otp.resend": "Resend code",
  "otp.resendIn": "Resend in {seconds}s",
  "otp.verify": "Verify",

  "hospital.title": "Choose your hospital",
  "hospital.subtitle": "We found your records at more than one hospital",
  "profile.title": "Who is visiting today?",
  "branch.title": "Choose a branch",

  "dashboard.greeting": "Hello, {name}",
  "dashboard.upcoming": "Upcoming appointments",
  "dashboard.noUpcoming": "No upcoming appointments",
  "dashboard.recentVisits": "Recent visits",
  "dashboard.bookAppointment": "Book appointment",
  "dashboard.viewHistory": "Visit history",
  "dashboard.cachedAt": "Showing information saved at {time}",

  "consultants.title": "Choose a doctor",
  "consultants.search": "Search by name",
  "consultants.empty": "No doctors are listed at this branch yet",

  "slots.title": "Choose a time",
  "slots.available": "{count} available",
  "slots.full": "Full",
  "slots.noneForDate": "No slots on this date",

  "booking.confirmTitle": "Confirm your appointment",
  "booking.hospital": "Hospital",
  "booking.branch": "Branch",
  "booking.doctor": "Doctor",
  "booking.date": "Date",
  "booking.time": "Time",
  "booking.patient": "Patient",
  "booking.confirm": "Confirm booking",
  "booking.successTitle": "Appointment booked",
  "booking.successBody": "We have sent a confirmation to your mobile number.",
  "booking.goHome": "Go to Home",

  "appointments.title": "Appointments",
  "appointments.upcoming": "Upcoming",
  "appointments.past": "Past",
  "appointments.noUpcoming": "No upcoming appointments",
  "appointments.noPast": "No past appointments",
  "appointments.cancel": "Cancel",
  "appointments.confirmCancel": "Yes, cancel",
  "appointments.reschedule": "Reschedule",
  "appointments.cancelConfirm": "Cancel this appointment?",

  "visits.title": "Visit history",
  "visits.empty": "You have no recorded visits yet",
  "visit.tab.casesheet": "Casesheet",
  "visit.tab.lab": "Lab",
  "visit.tab.diagnostic": "Diagnostic",
  "visit.tab.attachments": "Files",
  "visit.noApprovedReports":
    "No approved results yet. Results appear here once the doctor has signed them off.",
  "visit.offlineDetail":
    "Your records are only available online. Reconnect to view this visit.",

  "register.title": "Register as a new patient",
  "register.subtitle": "We could not find records for this number",
  "register.selectHospital": "Choose a hospital",
  "register.submit": "Register",
  "register.consent":
    "I agree that this hospital may show me my own health records in this app.",
  "register.verifyNote":
    "Please carry photo ID to your first visit so the hospital can confirm your identity.",

  "settings.title": "Profile & settings",
  "settings.logout": "Log out",
  "settings.withdrawConsent": "Withdraw portal access",
  "settings.withdrawExplain":
    "This removes your access to records in this app and deletes what is stored on this device. Your hospital records are unaffected.",
  "settings.language": "Language",

  "appointment.error.alreadyCancelled": "This appointment is already cancelled.",
  "appointment.error.alreadyCheckedIn":
    "You have already checked in at the hospital, so this can no longer be changed.",
  "appointment.error.cancelWindowClosed":
    "Appointments can only be cancelled more than 2 hours before the start time. Please call the hospital.",

  "validation.mobile.required": "Enter your mobile number",
  "validation.mobile.format": "Enter a 10-digit mobile number",
  "validation.mobile.notMobile": "This does not look like a mobile number",
  "validation.otp.required": "Enter the code we sent you",
  "validation.otp.format": "The code is 6 digits",
  "validation.firstName.required": "Enter your first name",
  "validation.firstName.tooLong": "First name is too long",
  "validation.firstName.format": "Use letters only",
  "validation.lastName.required": "Enter your last name",
  "validation.lastName.tooLong": "Last name is too long",
  "validation.lastName.format": "Use letters only",
  "validation.gender.required": "Select a gender",
  "validation.dob.required": "Enter your date of birth",
  "validation.dob.format": "Enter a valid date",
  "validation.dob.future": "Date of birth cannot be in the future",
  "validation.dob.implausible": "Check the year of birth",
  "validation.email.format": "Enter a valid email address",
  "validation.bloodGroup.invalid": "Select a valid blood group",
  "validation.address.tooLong": "Address is too long",

  "error.OTP_RATE_LIMITED": "Too many attempts. Please try again in a few minutes.",
  "error.OTP_INVALID": "That code is not correct.",
  "error.OTP_EXPIRED": "That code has expired. Request a new one.",
  "error.OTP_ATTEMPTS_EXCEEDED": "Too many incorrect codes. Request a new one.",
  "error.IDENTITY_TOKEN_REQUIRED": "Please verify your mobile number again.",
  "error.PATIENT_NOT_IN_CANDIDATE_SET": "Please verify your mobile number again.",
  "error.REGISTRATION_CAP_REACHED":
    "This number has already been used to register the maximum number of patients.",
  "error.UNAUTHORIZED": "Please log in again.",
  "error.SLOT_FULL": "That slot has just filled up. Please choose another.",
  "error.BOOKING_WINDOW_EXCEEDED": "You can book up to 30 days ahead.",
  "error.CANCEL_WINDOW_CLOSED":
    "This can no longer be cancelled in the app. Please call the hospital.",
  "error.APPOINTMENT_ALREADY_CHECKED_IN":
    "You have already checked in for this appointment.",
  "error.APPOINTMENT_CANCELLED": "This appointment is already cancelled.",
  "error.NOT_FOUND": "We could not find that.",
  "error.VALIDATION_FAILED": "Please check the details you entered.",
  "error.NETWORK_UNAVAILABLE": "You appear to be offline.",
  "error.TIMEOUT": "The hospital system is slow to respond. Please try again.",
  "error.UNKNOWN": "Something went wrong. Please try again.",
  "error.reference": "Reference: {correlationId}",

  "common.back": "Back",
  "common.retry": "Try again",
  "common.cancel": "Cancel",
  "common.goBack": "Go back",
  "common.close": "Close",
  "common.download": "Download",
  "common.share": "Share",
  "common.offline": "Offline",
} as const;

export type MessageKey = keyof typeof en;
