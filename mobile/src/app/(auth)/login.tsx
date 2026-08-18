import React, { useState } from "react";
import { useRouter } from "expo-router";
import { useAuthStore } from "../../state/authStore";
import { useContainer } from "../_layout";
import { validateMobile } from "../../core/validation";
import { t } from "../../i18n";
import {
  Body,
  Button,
  ErrorBanner,
  Screen,
  Subtitle,
  TextField,
  Title,
} from "../../ui/components";

/** Screen 1 — mobile number entry (PRD §7). */
export default function LoginScreen() {
  const router = useRouter();
  const container = useContainer();
  const { busy, error, requestOtp, clearError } = useAuthStore();
  const [mobile, setMobile] = useState("");
  const [fieldError, setFieldError] = useState<string | undefined>();

  async function onContinue() {
    const errors = validateMobile(mobile);
    setFieldError(errors.mobile);
    if (errors.mobile) return;
    clearError();
    await requestOtp(container, mobile);
    router.push("/(auth)/verify")
    if (!useAuthStore.getState().error) router.push("/(auth)/verify");
  }

  return (
    <Screen>
      <Title>{t("login.title")}</Title>
      <Subtitle>{t("login.subtitle")}</Subtitle>

      <TextField
        label={t("login.mobileLabel")}
        value={mobile}
        onChangeText={(v) => {
          setMobile(v);
          if (fieldError) setFieldError(undefined);
        }}
        keyboardType="phone-pad"
        autoComplete="tel"
        textContentType="telephoneNumber"
        maxLength={16}
        error={fieldError}
        returnKeyType="done"
        onSubmitEditing={onContinue}
      />

      {error ? (
        <ErrorBanner
          messageKey={error.message}
          correlationId={error.correlationId}
        />
      ) : null}

      <Button label={t("login.continue")} onPress={onContinue} busy={busy} />

      {/* States plainly why a code is being sent. A health app that asks for a
          number without explaining what happens next gets abandoned. */}
      <Body>{t("login.privacyNote")}</Body>
    </Screen>
  );
}
