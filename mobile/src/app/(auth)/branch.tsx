import React, { useEffect } from "react";
import { useRouter } from "expo-router";
import { useAuthStore } from "../../state/authStore";
import { useContainer } from "../_layout";
import { activeBranches, findHospital } from "../../core/resolution";
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

/** Screen 4 — branch selection. Only active branches; default is highlighted, not chosen. */
export default function BranchScreen() {
  const router = useRouter();
  const container = useContainer();
  const { candidates, selection, resolution, busy, error, choose } = useAuthStore();

  const hospital = findHospital(candidates, selection.tenantId);
  const branches = hospital ? activeBranches(hospital) : [];

  useEffect(() => {
    if (resolution?.step === "complete") router.replace("/(tabs)");
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
          onPress={() => void choose(container, { branchId: b.branchId })}
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
