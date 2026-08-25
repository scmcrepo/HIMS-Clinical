import React, { useEffect } from "react";
import { View } from "react-native";
import { useRouter } from "expo-router";
import { useAuthStore, patientsForSelectedHospital } from "../../state/authStore";
import { useContainer } from "../_layout";
import { formatAge, initials } from "../../core/format";
import { t } from "../../i18n";
import {
  Avatar,
  Body,
  Caption,
  Card,
  ErrorBanner,
  Heading,
  Loading,
  Screen,
  Title,
} from "../../ui/components";
import { spacing } from "../../ui/tokens";

/**
 * Screen 3 — "who is visiting today?".
 *
 * One mobile number legitimately maps to several patients: a parent's number on
 * their children's records. WO-017 R5 notes the corollary — this screen shows a
 * minor's records to the number holder, matching how the front desk already
 * works, and needs a process rather than code once that child turns 18.
 */
export default function ProfileScreen() {
  const router = useRouter();
  const container = useContainer();
  const { candidates, selection, resolution, busy, error, choose } = useAuthStore();
  const patients = patientsForSelectedHospital(candidates, selection.tenantId, selection.branchId);

  useEffect(() => {
    const step = resolution?.step;
    if (step === "branch") router.replace("/(auth)/branch");
    else if (step === "hospital") router.replace("/(auth)/hospital");
  }, [resolution?.step, router]);

  if (busy) return <Loading />;

  return (
    <Screen>
      <Title>{t("profile.title")}</Title>

      {error ? (
        <ErrorBanner messageKey={error.message} correlationId={error.correlationId} />
      ) : null}

      {patients.map((p) => (
        <Card
          key={p.patientId}
          accessibilityLabel={p.fullName}
          onPress={() => {
            void choose(container, { patientId: p.patientId });
          }}
        >
          <View style={{ flexDirection: "row", gap: spacing.md, alignItems: "center" }}>
            <Avatar initials={initials(p.fullName)} />
            <View style={{ flex: 1, gap: 2 }}>
              <Heading>{p.fullName}</Heading>
              <Body>
                {formatAge(p.age)} · {p.gender}
              </Body>
              {p.numberSequenceSuffix ? (
                <Caption>{p.numberSequenceSuffix}</Caption>
              ) : null}
            </View>
          </View>
        </Card>
      ))}
    </Screen>
  );
}
