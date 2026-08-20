import React, { useEffect, useState } from "react";
import { Pressable, Text } from "react-native";
import { useRouter } from "expo-router";
import { useAuthStore } from "../../state/authStore";
import { useContainer } from "../_layout";
import { validateOtp } from "../../core/validation";
import { t } from "../../i18n";
import {
  Button,
  Caption,
  ErrorBanner,
  Screen,
  Subtitle,
  TextField,
  Title,
} from "../../ui/components";
import { colors, typography } from "../../ui/tokens";

/**
 * OTP entry.
 *
 * Not in the PRD's 16-screen list — it exists because WO-017 §4.0 requires
 * possession of the number to be proved before any clinical data is returned.
 */
export default function VerifyScreen() {
  const router = useRouter();
  const container = useContainer();
  const { busy, error, mobile, verifyOtp, requestOtp, clearError, resendAvailableInSeconds } =
    useAuthStore();
  const [code, setCode] = useState("");
  const [fieldError, setFieldError] = useState<string | undefined>();
  const [cooldown, setCooldown] = useState(resendAvailableInSeconds);

  useEffect(() => {
    if (cooldown <= 0) return;
    const id = setInterval(() => setCooldown((s) => Math.max(0, s - 1)), 1000);
    return () => clearInterval(id);
  }, [cooldown]);

  async function onVerify() {
    const errors = validateOtp(code);
    setFieldError(errors.code);
    if (errors.code) return;
    clearError();
    await verifyOtp(container, code);

    const next = useAuthStore.getState();
    if (next.error) return;
    if (next.phase === "registering") {
      router.replace("/(register)/hospital");
    } else if (next.phase === "resolving") {
      const step = next.resolution?.step;
      if (step === "hospital") router.replace("/(auth)/hospital");
      else if (step === "branch") router.replace("/(auth)/branch");
      else if (step === "patient") router.replace("/(auth)/profile");
      else if (step === "complete") router.replace("/(tabs)");
      else router.replace("/(auth)/hospital");
    } else if (next.phase === "ready") {
      router.replace("/(tabs)");
    }
  }

  async function onResend() {
    clearError();
    setCode("");
    await requestOtp(container, mobile);
    setCooldown(useAuthStore.getState().resendAvailableInSeconds);
  }

  return (
    <Screen>
      <Title>{t("otp.title")}</Title>
      <Subtitle>{t("otp.subtitle", { mobile })}</Subtitle>

      <TextField
        label={t("otp.title")}
        value={code}
        onChangeText={(v) => {
          setCode(v.replace(/\D/g, ""));
          if (fieldError) setFieldError(undefined);
        }}
        keyboardType="number-pad"
        // Lets both platforms fill the code from the SMS automatically, which
        // removes the most common drop-off point in any OTP flow.
        autoComplete="sms-otp"
        textContentType="oneTimeCode"
        maxLength={6}
        error={fieldError}
        returnKeyType="done"
        onSubmitEditing={onVerify}
      />

      {error ? (
        <ErrorBanner messageKey={error.message} correlationId={error.correlationId} />
      ) : null}

      <Button label={t("otp.verify")} onPress={onVerify} busy={busy} />

      {cooldown > 0 ? (
        <Caption>{t("otp.resendIn", { seconds: cooldown })}</Caption>
      ) : (
        <Pressable onPress={onResend} accessibilityRole="button" hitSlop={8}>
          <Text style={{ ...typography.label, color: colors.primary }}>
            {t("otp.resend")}
          </Text>
        </Pressable>
      )}
    </Screen>
  );
}
