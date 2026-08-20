import React, { useEffect } from "react";
import { useRouter } from "expo-router";
import { useAuthStore } from "../../state/authStore";
import { useContainer } from "../_layout";
import { t } from "../../i18n";
import {
  Body,
  Caption,
  Card,
  ErrorBanner,
  Heading,
  Loading,
  Screen,
  Subtitle,
  Title,
} from "../../ui/components";

/** Screen 2 — hospital selection. Never reached when only one hospital matched. */
export default function HospitalScreen() {
  const router = useRouter();
  const container = useContainer();
  const { candidates, resolution, busy, error, choose } = useAuthStore();

  // The machine may have auto-skipped straight past this screen; if so, follow
  // it rather than rendering a picker with one option.
  useEffect(() => {
    const step = resolution?.step;
    if (step === "branch") router.replace("/(auth)/branch");
    else if (step === "patient") router.replace("/(auth)/profile");
    else if (step === "complete") router.replace("/(tabs)");
  }, [resolution?.step, router]);

  if (busy) return <Loading />;

  return (
    <Screen>
      <Title>{t("hospital.title")}</Title>
      <Subtitle>{t("hospital.subtitle")}</Subtitle>

      {error ? (
        <ErrorBanner messageKey={error.message} correlationId={error.correlationId} />
      ) : null}

      {candidates.map((h) => (
        <Card
          key={h.tenantId}
          accessibilityLabel={h.tenantName}
          onPress={() => {
            void choose(container, { tenantId: h.tenantId });
            router.push("/(auth)/branch");
          }}
        >
          <Heading>{h.tenantName}</Heading>
          {h.address ? <Body>{h.address}</Body> : null}
          {h.contactNumber ? <Caption>{h.contactNumber}</Caption> : null}
        </Card>
      ))}
    </Screen>
  );
}
