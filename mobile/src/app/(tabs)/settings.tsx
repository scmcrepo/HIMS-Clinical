import React from "react";
import { Alert, StyleSheet, View } from "react-native";
import { router } from "expo-router";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useContainer } from "../_layout";
import { useAuthStore } from "../../state/authStore";
import { QueryKeys } from "../../core/cachePolicy";
import { formatAge, initials } from "../../core/format";
import { t } from "../../i18n";
import {
  Avatar,
  Body,
  Button,
  Caption,
  Card,
  ErrorBanner,
  Heading,
  Loading,
  Row,
  Screen,
  Title,
} from "../../ui/components";
import { spacing } from "../../ui/tokens";
import { PortalError } from "../../core/errors";

/**
 * Screen 16 — Profile & settings (PRD §7, WO-019 §4.7).
 *
 * Logout clears the token store, the in-memory query cache (clinical data),
 * and resets the auth store to the login screen.
 */

export default function SettingsScreen() {
  const { api, session } = useContainer();
  const queryClient = useQueryClient();
  const reset = useAuthStore((s) => s.reset);
  const token = session.getAccessToken();

  const profile = useQuery({
    queryKey: QueryKeys.profile,
    queryFn: () => api.getProfile(),
  });

  const handleLogout = () => {
    Alert.alert(t("settings.logout"), "", [
      { text: t("common.cancel"), style: "cancel" },
      {
        text: t("settings.logout"),
        style: "destructive",
        onPress: async () => {
          try {
            await api.logout();
          } catch {
            // Best-effort; the token is cleared locally regardless.
          }
          await session.logout();
          queryClient.clear();
          reset();
        },
      },
    ]);
  };

  const handleWithdrawConsent = () => {
    Alert.alert(
      t("settings.withdrawConsent"),
      t("settings.withdrawExplain"),
      [
        { text: t("common.cancel"), style: "cancel" },
        {
          text: t("settings.withdrawConsent"),
          style: "destructive",
          onPress: async () => {
            // Consent withdrawal endpoint would be called here (WO-017 §5)
            // For now, it behaves like logout.
            try {
              await api.logout();
            } catch {
              // Best-effort
            }
            await session.logout();
            queryClient.clear();
            reset();
          },
        },
      ],
    );
  };

  if (profile.isLoading) return <Loading />;

  if (profile.isError) {
    const err = profile.error as PortalError;
    return (
      <Screen>
        <Title>{t("settings.title")}</Title>
        <ErrorBanner
          messageKey={err.message}
          correlationId={err.correlationId}
          onRetry={() => void profile.refetch()}
        />
        <View style={styles.actions}>
          <Button
            label={t("settings.logout")}
            onPress={handleLogout}
            variant="secondary"
          />
          <Button
            label={t("settings.withdrawConsent")}
            onPress={handleWithdrawConsent}
            variant="danger"
          />
        </View>
      </Screen>
    );
  }

  const me = profile.data;

  return (
    <Screen>
      <Title>{t("settings.title")}</Title>

      {me ? (
        <Card>
          <View style={styles.profileRow}>
            <Avatar initials={initials(me.fullName)} photoUrl={me.photoUrl} token={token} />
            <View style={{ flex: 1 }}>
              <Heading>{me.fullName}</Heading>
              <Caption>
                {me.tenantName} · {me.branchName}
              </Caption>
            </View>
          </View>

          {me.gender ? <Row label="Gender" value={me.gender} /> : null}
          {me.age !== null ? (
            <Row label="Age" value={formatAge(me.age, me.dateOfBirth)} />
          ) : null}
          {me.bloodGroup ? (
            <Row label="Blood Group" value={me.bloodGroup} />
          ) : null}
          {me.numberSequenceSuffix ? (
            <Row label="Patient No." value={me.numberSequenceSuffix} />
          ) : null}
        </Card>
      ) : null}

      <View style={styles.actions}>
        <Button
          label="Edit Profile"
          onPress={() => router.push("/edit-profile")}
        />
        <Button
          label={t("settings.logout")}
          onPress={handleLogout}
          variant="secondary"
        />
        <Button
          label={t("settings.withdrawConsent")}
          onPress={handleWithdrawConsent}
          variant="danger"
        />
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  profileRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.md,
  },
  actions: {
    gap: spacing.md,
    marginTop: spacing.lg,
  },
});
