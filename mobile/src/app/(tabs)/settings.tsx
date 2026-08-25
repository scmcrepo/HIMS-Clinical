import React, { useState } from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";
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
  Caption,
  Card,
  ConfirmationSheet,
  Divider,
  ErrorBanner,
  Heading,
  Loading,
  Row,
  Screen,
  SectionHeader,
  SkeletonCard,
  Title,
} from "../../ui/components";
import { colors, radius, shadows, spacing, typography } from "../../ui/tokens";
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

  /* Sheet states */
  const [logoutVisible, setLogoutVisible] = useState(false);
  const [withdrawVisible, setWithdrawVisible] = useState(false);
  const [busy, setBusy] = useState(false);

  const handleLogout = async () => {
    setBusy(true);
    try {
      await api.logout();
    } catch {
      // Best-effort; the token is cleared locally regardless.
    }
    await session.logout();
    queryClient.clear();
    reset();
    setBusy(false);
    setLogoutVisible(false);
  };

  const handleWithdrawConsent = async () => {
    setBusy(true);
    try {
      await api.logout();
    } catch {
      // Best-effort
    }
    await session.logout();
    queryClient.clear();
    reset();
    setBusy(false);
    setWithdrawVisible(false);
  };

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
        <View style={s.actions}>
          <SettingsMenuItem
            icon="log-out-outline"
            label={t("settings.logout")}
            onPress={() => setLogoutVisible(true)}
          />
          <SettingsMenuItem
            icon="shield-outline"
            label={t("settings.withdrawConsent")}
            onPress={() => setWithdrawVisible(true)}
            destructive
          />
        </View>
      </Screen>
    );
  }

  const me = profile.data;

  return (
    <Screen>
      <Title>{t("settings.title")}</Title>

      {/* Profile Card */}
      {profile.isLoading ? (
        <SkeletonCard lines={4} />
      ) : me ? (
        <Card>
          <View style={s.profileRow}>
            <Avatar initials={initials(me.fullName)} photoUrl={me.photoUrl} token={token} size={56} />
            <View style={{ flex: 1, justifyContent: "center" }}>
              <Heading>{formatPatientName(me.fullName)}</Heading>
              <View style={s.locationRow}>
                <Ionicons name="location-outline" size={12} color={colors.textMuted} />
                <Caption>
                  {me.tenantName} · {me.branchName}
                </Caption>
              </View>
            </View>
            <Pressable
              onPress={() => router.push("/edit-profile")}
              style={s.editIconBtn}
              hitSlop={12}
              accessibilityLabel="Change Profile Photo"
            >
              <Ionicons name="camera-outline" size={20} color={colors.primary} />
            </Pressable>
          </View>

          <Divider />

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

      {/* Account Section */}
      <SectionHeader>ACCOUNT</SectionHeader>
      <View style={s.menuGroup}>
        <SettingsMenuItem
          icon="log-out-outline"
          label={t("settings.logout")}
          onPress={() => setLogoutVisible(true)}
        />
      </View>

      {/* Access Section */}
      <SectionHeader>ACCESS</SectionHeader>
      <View style={s.menuGroup}>
        <SettingsMenuItem
          icon="shield-outline"
          label={t("settings.withdrawConsent")}
          subtitle="Remove your portal access and local data"
          onPress={() => setWithdrawVisible(true)}
          destructive
        />
      </View>

      {/* Logout Sheet */}
      <ConfirmationSheet
        visible={logoutVisible}
        title="Log out?"
        message="You'll need to verify your mobile number to log back in."
        confirmLabel="Log Out"
        cancelLabel="Stay Logged In"
        onConfirm={() => void handleLogout()}
        onCancel={() => setLogoutVisible(false)}
        destructive
        busy={busy}
      />

      {/* Withdraw Consent Sheet */}
      <ConfirmationSheet
        visible={withdrawVisible}
        title={t("settings.withdrawConsent")}
        message={t("settings.withdrawExplain")}
        confirmLabel="Withdraw Access"
        cancelLabel="Keep Access"
        onConfirm={() => void handleWithdrawConsent()}
        onCancel={() => setWithdrawVisible(false)}
        destructive
        busy={busy}
      />
    </Screen>
  );
}

/* =========================================================================
 * Settings Menu Item — iOS-style row with icon, label, optional subtitle
 * ======================================================================= */

function SettingsMenuItem({
  icon,
  label,
  subtitle,
  onPress,
  destructive,
}: {
  icon: keyof typeof Ionicons.glyphMap;
  label: string;
  subtitle?: string;
  onPress: () => void;
  destructive?: boolean;
}) {
  return (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => [
        s.menuItem,
        pressed && { backgroundColor: colors.surfaceAlt },
      ]}
    >
      <View style={[s.menuIconContainer, destructive && s.menuIconContainerDanger]}>
        <Ionicons
          name={icon}
          size={18}
          color={destructive ? colors.danger : colors.primary}
        />
      </View>
      <View style={{ flex: 1 }}>
        <Text style={[s.menuLabel, destructive && { color: colors.danger }]}>{label}</Text>
        {subtitle ? <Text style={s.menuSubtitle}>{subtitle}</Text> : null}
      </View>
      <Ionicons name="chevron-forward" size={16} color={colors.textMuted} />
    </Pressable>
  );
}

const s = StyleSheet.create({
  profileRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.md,
  },
  locationRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    marginTop: 2,
  },
  editIconBtn: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: colors.primarySoft,
    alignItems: "center",
    justifyContent: "center",
  },
  actions: {
    gap: spacing.md,
    marginTop: spacing.lg,
  },

  /* Menu Group */
  menuGroup: {
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    overflow: "hidden",
    ...shadows.sm,
  },
  menuItem: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.md,
    paddingVertical: spacing.md,
    paddingHorizontal: spacing.lg,
    minHeight: 56,
  },
  menuIconContainer: {
    width: 32,
    height: 32,
    borderRadius: 8,
    backgroundColor: colors.primarySoft,
    alignItems: "center",
    justifyContent: "center",
  },
  menuIconContainerDanger: {
    backgroundColor: colors.dangerSoft,
  },
  menuLabel: {
    ...typography.label,
    fontSize: 15,
    fontWeight: "600",
    color: colors.text,
  },
  menuSubtitle: {
    ...typography.caption,
    fontSize: 12,
    color: colors.textMuted,
    marginTop: 1,
  },
});
