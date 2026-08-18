import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { en } from "../src/i18n/en";
import { getLocale, missingKeys, registerPack, setLocale, t } from "../src/i18n";

const SRC = join(__dirname, "..", "src");

function walk(dir: string, filter: (p: string) => boolean): string[] {
  const out: string[] = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) out.push(...walk(full, filter));
    else if (filter(full)) out.push(full);
  }
  return out;
}

function read(path: string): string {
  return readFileSync(path, "utf8");
}

/**
 * These assertions are about the shape of the source tree rather than about
 * behaviour, and they exist because the failures they catch are invisible at
 * runtime in a passing app. A token quietly written to AsyncStorage still works;
 * it just also sits in plaintext on a rooted phone. Nothing else would fail.
 */

/**
 * Import specifiers only, not raw text.
 *
 * The first version of this scanned whole files and failed on
 * `core/session.ts`, whose comment explains why tokens must NOT go to
 * AsyncStorage. Matching prose meant the only way to pass was to delete the
 * warning — the check would have punished the documentation and left the
 * behaviour it cares about untested. The invariant is "does not import", so
 * that is what gets parsed.
 */
function importsOf(source: string): string[] {
  const specs: string[] = [];
  for (const m of source.matchAll(/from\s+["']([^"']+)["']/g)) {
    specs.push(m[1] as string);
  }
  for (const m of source.matchAll(/require\(\s*["']([^"']+)["']\s*\)/g)) {
    specs.push(m[1] as string);
  }
  for (const m of source.matchAll(/import\(\s*["']([^"']+)["']\s*\)/g)) {
    specs.push(m[1] as string);
  }
  return specs;
}

function offendingImports(
  files: string[],
  predicate: (spec: string) => boolean,
): string[] {
  const offenders: string[] = [];
  for (const file of files) {
    for (const spec of importsOf(read(file))) {
      if (predicate(spec)) offenders.push(`${file} -> ${spec}`);
    }
  }
  return offenders;
}

describe("token storage (WO-019 §4.4)", () => {
  it("never imports AsyncStorage anywhere in the app", () => {
    const files = walk(SRC, (p) => p.endsWith(".ts") || p.endsWith(".tsx"));
    expect(
      offendingImports(files, (s) => /async-storage/i.test(s)),
    ).toEqual([]);
  });

  it("keeps expo-secure-store out of core/, so core stays runnable under Node", () => {
    const coreFiles = walk(join(SRC, "core"), (p) => p.endsWith(".ts"));
    expect(
      offendingImports(coreFiles, (s) => s.startsWith("expo-secure-store")),
    ).toEqual([]);
  });

  it("confirms the scanner actually detects a real import", () => {
    // Guards against the check silently passing because the regex is broken.
    const sample = `import AsyncStorage from "@react-native-async-storage/async-storage";`;
    expect(importsOf(sample)).toEqual([
      "@react-native-async-storage/async-storage",
    ]);
    expect(importsOf(`// never use async-storage here`)).toEqual([]);
  });
});

describe("core layering (WO-019 §4.2)", () => {
  it("imports no react, react-native or expo module from core/", () => {
    const coreFiles = walk(join(SRC, "core"), (p) => p.endsWith(".ts"));
    expect(coreFiles.length).toBeGreaterThan(5);

    const offenders: string[] = [];
    for (const file of coreFiles) {
      const source = read(file);
      const imports = [...source.matchAll(/from\s+["']([^"']+)["']/g)].map(
        (m) => m[1] as string,
      );
      for (const spec of imports) {
        if (
          spec === "react" ||
          spec === "react-native" ||
          spec.startsWith("react-native/") ||
          spec.startsWith("expo") ||
          spec.startsWith("@react-native")
        ) {
          offenders.push(`${file} -> ${spec}`);
        }
      }
    }
    expect(offenders).toEqual([]);
  });

  it("keeps core/ free of relative imports that escape the layer", () => {
    const coreFiles = walk(join(SRC, "core"), (p) => p.endsWith(".ts"));
    const offenders: string[] = [];
    for (const file of coreFiles) {
      const imports = [...read(file).matchAll(/from\s+["'](\.[^"']+)["']/g)].map(
        (m) => m[1] as string,
      );
      for (const spec of imports) {
        if (spec.startsWith("../")) offenders.push(`${file} -> ${spec}`);
      }
    }
    expect(offenders).toEqual([]);
  });
});

describe("PHI safety in client logs (WO-019 §6)", () => {
  it("logs no field that could carry a name, diagnosis or file name", () => {
    const files = walk(SRC, (p) => p.endsWith(".ts") || p.endsWith(".tsx"));
    const banned = /log\?\.\(|log\(/;
    const sensitive =
      /(fullName|firstName|lastName|diagnosis|fileName|contactNumber|mobile\b|bloodGroup)/;

    const offenders: string[] = [];
    for (const file of files) {
      for (const line of read(file).split("\n")) {
        if (banned.test(line) && sensitive.test(line)) {
          offenders.push(`${file}: ${line.trim()}`);
        }
      }
    }
    expect(offenders).toEqual([]);
  });
});

describe("i18n", () => {
  it("resolves a key and interpolates parameters", () => {
    setLocale("en");
    expect(t("login.continue")).toBe("Continue");
    expect(t("otp.subtitle", { mobile: "9876543210" })).toBe(
      "We sent a 6-digit code to 9876543210",
    );
  });

  it("leaves an unmatched placeholder alone rather than printing undefined", () => {
    expect(t("otp.subtitle", {})).toContain("{mobile}");
  });

  it("returns the key itself when it is unknown, which is loud in review", () => {
    expect(t("no.such.key")).toBe("no.such.key");
  });

  it("falls back to English for a partially translated pack", () => {
    registerPack("ta", { "login.continue": "தொடரவும்" });
    setLocale("ta");
    expect(t("login.continue")).toBe("தொடரவும்");
    expect(t("login.title")).toBe("Welcome");
    expect(missingKeys("ta").length).toBeGreaterThan(0);
    setLocale("en");
  });

  it("ignores an unknown locale rather than blanking the UI", () => {
    setLocale("fr");
    expect(getLocale()).toBe("en");
  });

  it("has a message for every error code the client can produce", () => {
    const codes = [
      "OTP_RATE_LIMITED",
      "OTP_INVALID",
      "OTP_EXPIRED",
      "OTP_ATTEMPTS_EXCEEDED",
      "IDENTITY_TOKEN_REQUIRED",
      "PATIENT_NOT_IN_CANDIDATE_SET",
      "REGISTRATION_CAP_REACHED",
      "UNAUTHORIZED",
      "SLOT_FULL",
      "BOOKING_WINDOW_EXCEEDED",
      "CANCEL_WINDOW_CLOSED",
      "APPOINTMENT_ALREADY_CHECKED_IN",
      "APPOINTMENT_CANCELLED",
      "NOT_FOUND",
      "VALIDATION_FAILED",
      "NETWORK_UNAVAILABLE",
      "TIMEOUT",
      "UNKNOWN",
    ];
    const missing = codes.filter((c) => (en as Record<string, string>)[`error.${c}`] === undefined);
    expect(missing).toEqual([]);
  });

  it("has a message for every validation key the validators emit", () => {
    const validationSource = read(join(SRC, "core", "validation.ts"));
    const emitted = [
      ...validationSource.matchAll(/"(validation\.[a-zA-Z.]+)"/g),
    ].map((m) => m[1] as string);
    expect(emitted.length).toBeGreaterThan(10);
    const missing = [...new Set(emitted)].filter(
      (k) => (en as Record<string, string>)[k] === undefined,
    );
    expect(missing).toEqual([]);
  });

  it("has a message for every appointment eligibility reason", () => {
    const bookingSource = read(join(SRC, "core", "booking.ts"));
    const emitted = [
      ...bookingSource.matchAll(/"(appointment\.error\.[a-zA-Z]+)"/g),
    ].map((m) => m[1] as string);
    const missing = [...new Set(emitted)].filter(
      (k) => (en as Record<string, string>)[k] === undefined,
    );
    expect(missing).toEqual([]);
  });
});
