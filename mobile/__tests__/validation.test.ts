import { describe, expect, it } from "vitest";
import {
  hasErrors,
  normaliseMobile,
  validateDateOfBirth,
  validateMobile,
  validateOtp,
  validateRegistration,
  type RegistrationFormValues,
} from "../src/core/validation";
import {
  formatAge,
  formatFileSize,
  formatIsoDate,
  formatTime,
  formatTimeRange,
  initials,
} from "../src/core/format";

const NOW = new Date(2026, 6, 20, 10, 30, 0);

describe("mobile normalisation", () => {
  it("accepts the shapes patients actually type", () => {
    expect(normaliseMobile("9876543210")).toBe("9876543210");
    expect(normaliseMobile("+91 98765 43210")).toBe("9876543210");
    expect(normaliseMobile("098765-43210")).toBe("9876543210");
    expect(normaliseMobile("(987) 654 3210")).toBe("9876543210");
    expect(normaliseMobile("91 9876543210")).toBe("9876543210");
  });

  it("does not strip a leading 9 from a bare ten-digit number", () => {
    // Guards the 91-prefix rule against eating a legitimate 91xxxxxxxx number.
    expect(normaliseMobile("9187654321")).toBe("9187654321");
  });
});

describe("mobile validation", () => {
  it("accepts a valid Indian mobile number", () => {
    expect(validateMobile("9876543210")).toEqual({});
    expect(validateMobile("6123456789")).toEqual({});
  });

  it("requires a number", () => {
    expect(validateMobile("")).toEqual({ mobile: "validation.mobile.required" });
    expect(validateMobile("   ")).toEqual({ mobile: "validation.mobile.required" });
  });

  it("rejects the wrong number of digits", () => {
    expect(validateMobile("98765").mobile).toBe("validation.mobile.format");
    expect(validateMobile("98765432101234").mobile).toBe("validation.mobile.format");
  });

  it("rejects numbers that cannot receive an SMS", () => {
    expect(validateMobile("1234567890").mobile).toBe("validation.mobile.notMobile");
    expect(validateMobile("5876543210").mobile).toBe("validation.mobile.notMobile");
  });
});

describe("otp validation", () => {
  it("wants exactly six digits", () => {
    expect(validateOtp("123456")).toEqual({});
    expect(validateOtp(" 123456 ")).toEqual({});
    expect(validateOtp("").code).toBe("validation.otp.required");
    expect(validateOtp("12345").code).toBe("validation.otp.format");
    expect(validateOtp("12345a").code).toBe("validation.otp.format");
  });
});

describe("date of birth", () => {
  it("accepts a real past date", () => {
    expect(validateDateOfBirth("1994-03-15", NOW)).toEqual({});
  });

  it("rejects a date that does not exist", () => {
    expect(validateDateOfBirth("2026-02-31", NOW).dateOfBirth).toBe(
      "validation.dob.format",
    );
    expect(validateDateOfBirth("2025-13-01", NOW).dateOfBirth).toBe(
      "validation.dob.format",
    );
  });

  it("rejects the future", () => {
    expect(validateDateOfBirth("2027-01-01", NOW).dateOfBirth).toBe(
      "validation.dob.future",
    );
  });

  it("rejects an implausible year", () => {
    expect(validateDateOfBirth("1850-01-01", NOW).dateOfBirth).toBe(
      "validation.dob.implausible",
    );
  });

  it("rejects a non-ISO format rather than guessing the order", () => {
    // 15/03/1994 is ambiguous across locales; refusing beats guessing wrong.
    expect(validateDateOfBirth("15/03/1994", NOW).dateOfBirth).toBe(
      "validation.dob.format",
    );
  });
});

describe("registration form", () => {
  const valid: RegistrationFormValues = {
    salutation: "Mr",
    firstName: "Rajesh",
    lastName: "Kumar",
    gender: "MALE",
    dateOfBirth: "1994-03-15",
    mobile: "9876543210",
    email: "rajesh@example.com",
    bloodGroup: "B+",
    address: "12, MG Road, Bangalore",
  };

  it("passes a complete valid form", () => {
    expect(validateRegistration(valid, NOW)).toEqual({});
    expect(hasErrors({})).toBe(false);
  });

  it("passes with only the required fields", () => {
    const minimal: RegistrationFormValues = {
      firstName: "Asha",
      lastName: "R",
      gender: "FEMALE",
      dateOfBirth: "2001-11-02",
      mobile: "9812345678",
    };
    expect(validateRegistration(minimal, NOW)).toEqual({});
  });

  it("rejects the punctuation the backend rejects, even though the PRD allowed it", () => {
    // RegisterPatientRequest enforces ^[a-zA-Z\s]+$. Accepting these on the
    // client would produce a form that submits and then fails server-side with
    // a message the patient cannot act on.
    expect(
      validateRegistration({ ...valid, firstName: "Jean-Pierre" }, NOW).firstName,
    ).toBe("validation.firstName.format");
    expect(
      validateRegistration({ ...valid, lastName: "St. John" }, NOW).lastName,
    ).toBe("validation.lastName.format");
  });

  it("accepts multi-part names separated by spaces", () => {
    expect(
      validateRegistration({ ...valid, firstName: "Lakshmi Devi" }, NOW),
    ).toEqual({});
  });

  it("rejects names containing punctuation or digits", () => {
    expect(
      validateRegistration({ ...valid, firstName: ".Rajesh" }, NOW).firstName,
    ).toBe("validation.firstName.format");
    expect(
      validateRegistration({ ...valid, lastName: "Kumar2" }, NOW).lastName,
    ).toBe("validation.lastName.format");
  });

  it("enforces the backend length caps of 60 and 40", () => {
    expect(
      validateRegistration({ ...valid, firstName: "A".repeat(61) }, NOW).firstName,
    ).toBe("validation.firstName.tooLong");
    expect(
      validateRegistration({ ...valid, lastName: "B".repeat(41) }, NOW).lastName,
    ).toBe("validation.lastName.tooLong");
    expect(
      validateRegistration({ ...valid, firstName: "A".repeat(60) }, NOW).firstName,
    ).toBeUndefined();
  });

  it("requires a gender from the allowed set", () => {
    expect(validateRegistration({ ...valid, gender: "" }, NOW).gender).toBe(
      "validation.gender.required",
    );
    expect(validateRegistration({ ...valid, gender: "Male" }, NOW).gender).toBe(
      "validation.gender.required",
    );
  });

  it("validates optional fields only when present", () => {
    expect(validateRegistration({ ...valid, email: "" }, NOW).email).toBeUndefined();
    expect(validateRegistration({ ...valid, email: "not-an-email" }, NOW).email).toBe(
      "validation.email.format",
    );
    expect(validateRegistration({ ...valid, bloodGroup: "C+" }, NOW).bloodGroup).toBe(
      "validation.bloodGroup.invalid",
    );
  });

  it("reports every broken field at once rather than one at a time", () => {
    const errors = validateRegistration(
      { firstName: "", lastName: "", gender: "", dateOfBirth: "", mobile: "" },
      NOW,
    );
    expect(Object.keys(errors).sort()).toEqual([
      "dateOfBirth",
      "firstName",
      "gender",
      "lastName",
      "mobile",
    ]);
    expect(hasErrors(errors)).toBe(true);
  });
});

describe("formatting", () => {
  it("formats dates and times for display", () => {
    expect(formatIsoDate("2026-07-20")).toBe("20 Jul 2026");
    expect(formatIsoDate("2026-01-05T08:30:00Z")).toBe("5 Jan 2026");
    expect(formatTime("09:00:00")).toBe("9:00 AM");
    expect(formatTime("14:30")).toBe("2:30 PM");
    expect(formatTime("00:15")).toBe("12:15 AM");
    expect(formatTime("12:00")).toBe("12:00 PM");
    expect(formatTimeRange("09:00:00", "10:00:00")).toBe("9:00 AM – 10:00 AM");
  });

  it("returns the input unchanged when it cannot be parsed", () => {
    expect(formatIsoDate("garbage")).toBe("garbage");
    expect(formatTime("garbage")).toBe("garbage");
  });

  it("formats age and file size", () => {
    expect(formatAge(32)).toBe("32 yrs");
    expect(formatAge(null)).toBe("—");
    expect(formatFileSize(512)).toBe("512 B");
    expect(formatFileSize(2048)).toBe("2 KB");
    expect(formatFileSize(3_500_000)).toBe("3.3 MB");
    expect(formatFileSize(null)).toBe("");
  });

  it("derives avatar initials, ignoring the salutation", () => {
    expect(initials("Mr. Rajesh Kumar")).toBe("RK");
    expect(initials("Dr Srinivas Rao")).toBe("SR");
    expect(initials("Asha")).toBe("A");
    expect(initials("")).toBe("?");
  });
});
