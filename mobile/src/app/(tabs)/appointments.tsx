import React, { useState, useCallback } from "react";
import { Alert, Pressable, StyleSheet, Text, View } from "react-native";
import { useRouter } from "expo-router";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useContainer } from "../_layout";
import { QueryKeys } from "../../core/cachePolicy";
import { canCancel, canReschedule } from "../../core/booking";
import { formatIsoDate, formatStatus, formatTimeRange } from "../../core/format";
import { t } from "../../i18n";
import {
  Badge,
  Body,
  Button,
  Caption,
  Card,
  EmptyState,
  ErrorBanner,
  Heading,
  Loading,
  Screen,
  Title,
} from "../../ui/components";
import { colors, radius, spacing, typography } from "../../ui/tokens";
import { PortalError } from "../../core/errors";
import type { Appointment } from "../../core/contracts";

/**
 * Screen 10 — My appointments (PRD §7, WO-019 §4.7).
 *
 * Two-segment toggle between "Upcoming" and "Past". Each card shows
 * cancel/reschedule buttons when the domain rules permit.
 */

type Scope = "upcoming" | "past";

export default function AppointmentsScreen() {
  const router = useRouter();
  const { api } = useContainer();
  const queryClient = useQueryClient();
  const [scope, setScope] = useState<Scope>("upcoming");
  const [page, setPage] = useState(0);

  const query = useQuery({
    queryKey: [...QueryKeys.appointments(scope), page],
    queryFn: () => api.listAppointments(scope, page, 20),
  });

  const cancelMutation = useMutation({
    mutationFn: (appointmentId: string) => api.cancelAppointment(appointmentId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["appointments"] });
    },
  });

  const handleCancel = useCallback(
    (appointment: Appointment) => {
      const check = canCancel(appointment, new Date());
      if (!check.allowed) {
        Alert.alert(t("common.cancel"), t(check.reason!));
        return;
      }
      Alert.alert(t("appointments.cancelConfirm"), "", [
        { text: t("common.goBack"), style: "cancel" },
        {
          text: t("appointments.confirmCancel"),
          style: "destructive",
          onPress: () => cancelMutation.mutate(appointment.appointmentId),
        },
      ]);
    },
    [cancelMutation],
  );

  const switchScope = (newScope: Scope) => {
    setScope(newScope);
    setPage(0);
  };

  const appointments = query.data?.content ?? [];
  const totalPages = query.data?.totalPages ?? 1;

  return (
    <Screen>
      <Title>{t("appointments.title")}</Title>

      {/* Segment toggle */}
      <View style={styles.segmentRow}>
        {(["upcoming", "past"] as const).map((s) => (
          <Pressable
            key={s}
            style={[styles.segment, scope === s && styles.segmentActive]}
            onPress={() => switchScope(s)}
          >
            <Text
              style={[
                styles.segmentText,
                scope === s && styles.segmentTextActive,
              ]}
            >
              {t(`appointments.${s}`)}
            </Text>
          </Pressable>
        ))}
      </View>

      {/* List */}
      {query.isLoading ? (
        <Loading />
      ) : query.isError ? (
        <ErrorBanner
          messageKey={(query.error as PortalError)?.message ?? "error.UNKNOWN"}
          correlationId={(query.error as PortalError)?.correlationId}
          onRetry={() => void query.refetch()}
        />
      ) : appointments.length === 0 ? (
        <EmptyState
          messageKey={
            scope === "upcoming"
              ? "appointments.noUpcoming"
              : "appointments.noPast"
          }
        />
      ) : (
        appointments.map((a: Appointment) => {
          const now = new Date();
          const cancelOk = canCancel(a, now);
          const rescheduleOk = canReschedule(a, now);
          return (
            <Card key={a.appointmentId}>
              <Heading>{a.consultantName}</Heading>
              <Body>
                {formatIsoDate(a.appointmentDate)} ·{" "}
                {formatTimeRange(a.fromTime, a.toTime)}
              </Body>
              {a.departmentName ? (
                <Caption>{a.departmentName}</Caption>
              ) : null}
              <Badge
                label={formatStatus(a.status)}
                tone={
                  a.status === "CANCELLED"
                    ? "danger"
                    : a.status === "BOOKED" || a.status === "RESCHEDULED"
                      ? "success"
                      : "neutral"
                }
              />
              {scope === "upcoming" &&
                a.status !== "CANCELLED" &&
                a.status !== "COMPLETED" && (
                  <View style={styles.actions}>
                    {rescheduleOk.allowed && (
                      <Pressable
                        onPress={() =>
                          router.push({
                            pathname: `/book/${a.consultantId}/slots`,
                            params: { rescheduleAppointmentId: a.appointmentId },
                          } as never)
                        }
                        style={styles.rescheduleBtn}
                      >
                        <Text style={styles.rescheduleText}>
                          {t("appointments.reschedule")}
                        </Text>
                      </Pressable>
                    )}
                    {cancelOk.allowed && (
                      <Pressable
                        onPress={() => handleCancel(a)}
                        disabled={cancelMutation.isPending}
                        style={styles.cancelBtn}
                      >
                        <Text style={styles.cancelText}>
                          {t("appointments.cancel")}
                        </Text>
                      </Pressable>
                    )}
                  </View>
                )}
            </Card>
          );
        })
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <View style={styles.pagination}>
          <Button
            label="← Prev"
            variant="secondary"
            onPress={() => setPage((p) => Math.max(0, p - 1))}
            disabled={page === 0}
          />
          <Body>
            {page + 1} / {totalPages}
          </Body>
          <Button
            label="Next →"
            variant="secondary"
            onPress={() => setPage((p) => p + 1)}
            disabled={page >= totalPages - 1}
          />
        </View>
      )}
    </Screen>
  );
}

const styles = StyleSheet.create({
  segmentRow: {
    flexDirection: "row",
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    overflow: "hidden",
    backgroundColor: colors.surface,
  },
  segment: {
    flex: 1,
    paddingVertical: spacing.sm,
    alignItems: "center",
  },
  segmentActive: {
    backgroundColor: colors.primary,
  },
  segmentText: {
    ...typography.label,
    color: colors.textMuted,
  },
  segmentTextActive: {
    color: colors.surface,
  },
  actions: {
    flexDirection: "row",
    gap: spacing.sm,
    marginTop: spacing.sm,
  },
  rescheduleBtn: {
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.xs,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.primary,
    backgroundColor: colors.primarySoft,
  },
  rescheduleText: {
    ...typography.label,
    color: colors.primaryDark,
    fontWeight: "700",
  },
  cancelBtn: {
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.xs,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.danger,
    backgroundColor: "#FEF2F2",
  },
  cancelText: {
    ...typography.label,
    color: colors.danger,
    fontWeight: "700",
  },
  pagination: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: spacing.md,
    paddingVertical: spacing.md,
  },
});
