import React, { useCallback } from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";
import { useRouter } from "expo-router";
import { useQuery } from "@tanstack/react-query";
import { Ionicons } from "@expo/vector-icons";
import { useContainer } from "../_layout";
import { QueryKeys } from "../../core/cachePolicy";
import { canReschedule, upcomingAppointments } from "../../core/booking";
import {
  formatIsoDate,
  formatPatientName,
  formatStatus,
  formatTimeRange,
  initials,
} from "../../core/format";
import { t } from "../../i18n";
import {
  ActionButton,
  Avatar,
  Badge,
  Body,
  Button,
  Caption,
  Card,
  Divider,
  EmptyState,
  ErrorBanner,
  Heading,
  Screen,
  SectionHeader,
  SkeletonCard,
  Title,
} from "../../ui/components";
import { colors, radius, shadows, spacing, typography } from "../../ui/tokens";
import { PortalError } from "../../core/errors";

/** Screen 5 — dashboard (PRD §3.2 step 5). */
export default function DashboardScreen() {
  const router = useRouter();
  const { api } = useContainer();

  const profile = useQuery({
    queryKey: QueryKeys.profile,
    queryFn: () => api.getProfile(),
  });

  const appointments = useQuery({
    queryKey: QueryKeys.appointments("upcoming"),
    queryFn: () => api.listAppointments("upcoming"),
  });

  const visits = useQuery({
    queryKey: QueryKeys.visits(0),
    // PRD §3.2 step 5 shows the last five; the endpoint pages at ten.
    queryFn: () => api.listVisits(0, 5),
  });

  const handleRefresh = useCallback(() => {
    void profile.refetch();
    void appointments.refetch();
    void visits.refetch();
  }, [profile, appointments, visits]);

  const isRefreshing =
    (profile.isRefetching || appointments.isRefetching || visits.isRefetching) &&
    !profile.isLoading;

  if (profile.isError) {
    const err = profile.error as PortalError;
    return (
      <Screen>
        <ErrorBanner
          messageKey={err.message}
          correlationId={err.correlationId}
          onRetry={() => void profile.refetch()}
        />
      </Screen>
    );
  }

  const me = profile.data;
  const upcoming = upcomingAppointments(
    appointments.data?.content ?? [],
    new Date(),
  );

  return (
    <Screen onRefresh={handleRefresh} refreshing={isRefreshing}>
      {/* Greeting Header */}
      {profile.isLoading ? (
        <View style={s.greetingRow}>
          <View style={s.greetingAvatarSkeleton} />
          <View style={{ flex: 1, gap: 6 }}>
            <View style={s.greetingNameSkeleton} />
            <View style={s.greetingSubSkeleton} />
          </View>
        </View>
      ) : me ? (
        <View style={s.greetingRow}>
          <Avatar initials={initials(me.fullName)} photoUrl={me.photoUrl} size={52} />
          <View style={{ flex: 1 }}>
            <Title>{t("dashboard.greeting", { name: formatPatientName(me.fullName) })}</Title>
            <View style={s.branchBadge}>
              <Ionicons name="location-outline" size={12} color={colors.textMuted} />
              <Caption>
                {me.tenantName} · {me.branchName}
              </Caption>
            </View>
          </View>
        </View>
      ) : null}

      {/* Book Appointment CTA */}
      <Button
        label={t("dashboard.bookAppointment")}
        onPress={() => router.push("/book/consultants")}
        icon={<Ionicons name="add-circle-outline" size={18} color={colors.surface} />}
      />

      <Divider />

      {/* Upcoming Appointments */}
      <SectionHeader>UPCOMING APPOINTMENTS</SectionHeader>
      {appointments.isLoading ? (
        <>
          <SkeletonCard lines={4} />
          <SkeletonCard lines={3} />
        </>
      ) : upcoming.length === 0 ? (
        <EmptyState
          messageKey="dashboard.noUpcoming"
          icon="calendar-outline"
          description="You don't have any appointments scheduled."
          actionLabel="Book Appointment"
          onAction={() => router.push("/book/consultants")}
        />
      ) : (
        upcoming.map((a) => {
          const now = new Date();
          const rescheduleOk = canReschedule(a, now);
          return (
            <Card key={a.appointmentId} style={s.upcomingCard}>
              <View style={s.upcomingHeader}>
                <View style={{ flex: 1 }}>
                  <Heading>{a.consultantName}</Heading>
                  {a.departmentName ? <Caption>{a.departmentName}</Caption> : null}
                </View>
                <Badge label={formatStatus(a.status)} />
              </View>
              <View style={s.upcomingDetails}>
                <View style={s.detailItem}>
                  <Ionicons name="calendar-outline" size={14} color={colors.textMuted} />
                  <Text style={s.detailText}>{formatIsoDate(a.appointmentDate)}</Text>
                </View>
                <View style={s.detailItem}>
                  <Ionicons name="time-outline" size={14} color={colors.textMuted} />
                  <Text style={s.detailText}>{formatTimeRange(a.fromTime, a.toTime)}</Text>
                </View>
              </View>
              {(a.status === "BOOKED" || a.status === "RESCHEDULED") && rescheduleOk.allowed && (
                <View style={s.upcomingActions}>
                  <ActionButton
                    label={t("appointments.reschedule")}
                    icon="swap-horizontal-outline"
                    onPress={() =>
                      router.push({
                        pathname: `/book/${a.consultantId}/slots`,
                        params: { rescheduleAppointmentId: a.appointmentId },
                      } as never)
                    }
                  />
                </View>
              )}
            </Card>
          );
        })
      )}

      <Divider />

      {/* Recent Visits */}
      <SectionHeader>RECENT VISITS</SectionHeader>
      {visits.isLoading ? (
        <>
          <SkeletonCard lines={3} />
          <SkeletonCard lines={3} />
        </>
      ) : (visits.data?.content ?? []).length === 0 ? (
        <EmptyState
          messageKey="visits.empty"
          icon="clipboard-outline"
          description="Your visit records will appear here after your first appointment."
        />
      ) : (
        (visits.data?.content ?? []).map((v) => (
          <Card
            key={v.encounterId}
            accessibilityLabel={`${formatIsoDate(v.visitDate)} ${v.consultantName ?? ""}`}
            onPress={() => router.push(`/visits/${v.encounterId}`)}
          >
            <View style={s.visitHeader}>
              <View style={{ flex: 1 }}>
                <Heading>{v.consultantName ?? "—"}</Heading>
                <View style={s.detailItem}>
                  <Ionicons name="calendar-outline" size={13} color={colors.textMuted} />
                  <Text style={s.detailText}>
                    {formatIsoDate(v.visitDate)} · {v.encounterType}
                  </Text>
                </View>
              </View>
              <Badge label={formatStatus(v.status)} />
            </View>
          </Card>
        ))
      )}
    </Screen>
  );
}

const s = StyleSheet.create({
  greetingRow: {
    flexDirection: "row",
    gap: spacing.md,
    alignItems: "center",
  },
  greetingAvatarSkeleton: {
    width: 52,
    height: 52,
    borderRadius: 26,
    backgroundColor: colors.primarySoft,
  },
  greetingNameSkeleton: {
    width: 160,
    height: 18,
    borderRadius: radius.sm,
    backgroundColor: colors.primarySoft,
  },
  greetingSubSkeleton: {
    width: 120,
    height: 14,
    borderRadius: radius.sm,
    backgroundColor: colors.primarySoft,
  },
  branchBadge: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    marginTop: 2,
  },
  upcomingCard: {
    borderLeftWidth: 3,
    borderLeftColor: colors.primary,
  },
  upcomingHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "flex-start",
    gap: spacing.sm,
  },
  upcomingDetails: {
    flexDirection: "row",
    gap: spacing.lg,
    marginTop: spacing.sm,
  },
  detailItem: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
  },
  detailText: {
    ...typography.caption,
    fontSize: 13,
    color: colors.textMuted,
    fontWeight: "500",
  },
  upcomingActions: {
    flexDirection: "row",
    gap: spacing.sm,
    marginTop: spacing.sm,
  },
  visitHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "flex-start",
    gap: spacing.sm,
  },
});
