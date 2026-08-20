import React from "react";
import { Alert, Pressable, StyleSheet, View } from "react-native";
import { router } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useContainer } from "../_layout";
import { useAuthStore } from "../../state/authStore";
import { QueryKeys } from "../../core/cachePolicy";
import { formatAge, formatPatientName, initials } from "../../core/format";
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
import { colors, radius, spacing } from "../../ui/tokens";
import { PortalError } from "../../core/errors";

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
            <Avatar initials={initials(me.fullName)} photoUrl={me.photoUrl} token={token} size={54} />
            <View style={{ flex: 1, justifyContent: "center" }}>
              <Heading>{formatPatientName(me.fullName)}</Heading>
              <Caption>
                {me.tenantName} · {me.branchName}
              </Caption>
            </View>
            <Pressable
              onPress={() => router.push("/edit-profile")}
              style={styles.editIconBtn}
              hitSlop={12}
              accessibilityLabel="Edit Profile"
            >
              <Ionicons name="create-outline" size={22} color={colors.primary} />
            </Pressable>
          </View>

          {me.gender ? <Row label="Gender" value={me.gender} /> : null}
          <Row label="Age" value={formatAge(me.age, me.dateOfBirth)} />
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
  editIconBtn: {
    padding: spacing.xs,
    alignItems: "center",
    justifyContent: "center",
  },
  actions: {
    gap: spacing.md,
    marginTop: spacing.lg,
  },
});
