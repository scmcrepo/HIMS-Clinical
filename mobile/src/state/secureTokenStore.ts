import * as SecureStore from "expo-secure-store";
import type { StoredSession, TokenStore } from "../core/session";

/**
 * The real TokenStore: iOS Keychain / Android Keystore via expo-secure-store.
 *
 * This is the only file in the app that touches token persistence, and it is
 * outside core/ specifically so that core/session.ts stays testable under Node.
 * The invariants test asserts that no file anywhere imports AsyncStorage —
 * SecureStore is hardware-backed on both platforms, AsyncStorage is a plaintext
 * file that any backup or rooted-device dump reads straight out.
 */

const KEY = "hims.portal.session.v1";

/**
 * WHEN_UNLOCKED_THIS_DEVICE_ONLY rather than the default: the session must not
 * ride an iCloud Keychain sync to the patient's other devices, and must not be
 * readable while the phone is locked. WO-017 caps a patient at two concurrent
 * devices; a silently synced credential would consume the second slot without
 * the patient ever logging in there.
 */
const OPTIONS: SecureStore.SecureStoreOptions = {
  keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
};

export class SecureTokenStore implements TokenStore {
  async read(): Promise<StoredSession | null> {
    try {
      const raw = await SecureStore.getItemAsync(KEY, OPTIONS);
      if (!raw) return null;
      const parsed = JSON.parse(raw) as Partial<StoredSession>;
      // A partially-written or schema-drifted record is treated as absent
      // rather than half-trusted: a session missing its tenantId would send
      // requests with no scope and fail confusingly much later.
      if (
        !parsed.accessToken ||
        !parsed.refreshToken ||
        !parsed.patientId ||
        !parsed.tenantId ||
        !parsed.branchId
      ) {
        await this.clear();
        return null;
      }
      return parsed as StoredSession;
    } catch {
      // Corrupt keychain entry, or a keystore invalidated because the user
      // changed their device passcode or re-enrolled biometrics. Both mean the
      // patient logs in again; neither should crash the splash screen.
      await this.clear();
      return null;
    }
  }

  async write(session: StoredSession): Promise<void> {
    await SecureStore.setItemAsync(KEY, JSON.stringify(session), OPTIONS);
  }

  async clear(): Promise<void> {
    try {
      await SecureStore.deleteItemAsync(KEY, OPTIONS);
    } catch {
      // Deleting something already absent is not a failure worth propagating
      // into a logout flow.
    }
  }
}

export async function isSecureStoreAvailable(): Promise<boolean> {
  return SecureStore.isAvailableAsync();
}
