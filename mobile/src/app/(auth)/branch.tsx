import React, { useEffect } from "react";
import { useRouter } from "expo-router";
import { useAuthStore } from "../../state/authStore";
import { useContainer } from "../_layout";
import { branchesForHospital, findHospital } from "../../core/resolution";
import { t } from "../../i18n";
import {
  Badge,
  Body,
  Caption,
  Card,
  ErrorBanner,
  Heading,
  Loading,
  Screen,
  Title,
} from "../../ui/components";

/** Screen — branch selection. Shows only registered branches for this mobile. */
export default function BranchScreen() {
  const router = useRouter();
  const container = useContainer();
  const { candidates, selection, resolution, busy, error, choose } = useAuthStore();

  const hospital = findHospital(candidates, selection.tenantId);
  const branches = hospital ? branchesForHospital(hospital) : [];

  useEffect(() => {
    if (resolution?.step === "patient") router.replace("/(auth)/profile");
    else if (resolution?.step === "complete") router.replace("/(tabs)");
    else if (resolution?.step === "hospital") router.replace("/(auth)/hospital");
  }, [resolution?.step, router]);

  if (busy) return <Loading />;

  return (
    <Screen>
      <Title>{t("branch.title")}</Title>

      {error ? (
        <ErrorBanner messageKey={error.message} correlationId={error.correlationId} />
      ) : null}

      {branches.map((b) => (
        <Card
          key={b.branchId}
          accessibilityLabel={b.name}
          onPress={() => {
            void choose(container, { branchId: b.branchId });
            router.push("/(auth)/profile");
          }}
        >
          <Heading>{b.name}</Heading>
          {b.address ? <Body>{b.address}</Body> : null}
          {b.contactNumber ? <Caption>{b.contactNumber}</Caption> : null}
          {b.isDefault ? <Badge label="Main branch" tone="success" /> : null}
        </Card>
      ))}
    </Screen>
  );
}
