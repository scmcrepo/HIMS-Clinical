import React, { useState, useCallback, useRef, useEffect } from "react";
import { Animated, Pressable, StyleSheet, Text, View } from "react-native";
import { useRouter } from "expo-router";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Ionicons } from "@expo/vector-icons";
import { useContainer } from "../_layout";
import { QueryKeys } from "../../core/cachePolicy";
import { canCancel, canReschedule } from "../../core/booking";
import { formatIsoDate, formatStatus, formatTimeRange } from "../../core/format";
import { t } from "../../i18n";
import {
  ActionButton,
  Badge,
  Body,
  Button,
  Caption,
  Card,
  ConfirmationSheet,
  EmptyState,
  ErrorBanner,
  Heading,
  Screen,
  SkeletonCard,
  Title,
} from "../../ui/components";
import { colors, radius, spacing, typography, animation } from "../../ui/tokens";
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

  /* Animated segment indicator */
  const indicatorAnim = useRef(new Animated.Value(0)).current;

  const [containerWidth, setContainerWidth] = useState(0);

  useEffect(() => {
    Animated.spring(indicatorAnim, {
      toValue: scope === "upcoming" ? 0 : 1,
      useNativeDriver: true,
      speed: 20,
      bounciness: 4,
    }).start();
  }, [scope, indicatorAnim]);

  /* Cancel confirmation sheet */
  const [cancelTarget, setCancelTarget] = useState<Appointment | null>(null);

  const query = useQuery({
    queryKey: [...QueryKeys.appointments(scope), page],
    queryFn: () => api.listAppointments(scope, page, 20),
  });

  const cancelMutation = useMutation({
    mutationFn: (appointmentId: string) => api.cancelAppointment(appointmentId),
    onSuccess: () => {
      setCancelTarget(null);
      void queryClient.invalidateQueries({ queryKey: ["appointments"] });
    },
  });

  const handleCancel = useCallback(
    (appointment: Appointment) => {
      const check = canCancel(appointment, new Date());
      if (!check.allowed) {
        setCancelTarget(null);
        return;
      }
      setCancelTarget(appointment);
    },
    [],
  );

  const handleRefresh = useCallback(() => {
    void query.refetch();
  }, [query]);

  const switchScope = (newScope: Scope) => {
    setScope(newScope);
    setPage(0);
  };

  const appointments = query.data?.content ?? [];
  const totalPages = query.data?.totalPages ?? 1;

  const halfWidth = containerWidth > 0 ? (containerWidth - 6) / 2 : 0;

  return (
    <Screen onRefresh={handleRefresh} refreshing={query.isRefetching && !query.isLoading}>
      <Title>{t("appointments.title")}</Title>

      {/* Animated Segment Toggle */}
      <View
        style={s.segmentRow}
        onLayout={(e) => setContainerWidth(e.nativeEvent.layout.width)}
      >
        {halfWidth > 0 && (
          <Animated.View
            style={[
              s.segmentIndicator,
              {
                width: halfWidth,
                transform: [
                  {
                    translateX: indicatorAnim.interpolate({
                      inputRange: [0, 1],
                      outputRange: [0, halfWidth],
                    }),
                  },
                ],
              },
            ]}
          />
        )}
        {(["upcoming", "past"] as const).map((seg) => (
          <Pressable
            key={seg}
            style={s.segment}
            onPress={() => switchScope(seg)}
          >
            <Text
              style={[
                s.segmentText,
                scope === seg && s.segmentTextActive,
              ]}
            >
              {t(`appointments.${seg}`)}
            </Text>
          </Pressable>
        ))}
      </View>

      {/* List */}
      {query.isLoading ? (
        <>
          <SkeletonCard lines={4} />
          <SkeletonCard lines={4} />
          <SkeletonCard lines={3} />
        </>
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
          icon={scope === "upcoming" ? "calendar-outline" : "time-outline"}
          description={
            scope === "upcoming"
              ? "You don't have any upcoming appointments."
              : "Your past appointments will appear here."
          }
          actionLabel={scope === "upcoming" ? "Book Appointment" : undefined}
          onAction={scope === "upcoming" ? () => router.push("/book/consultants") : undefined}
        />
      ) : (
        appointments.map((a: Appointment) => {
          const now = new Date();
          const cancelOk = canCancel(a, now);
          const rescheduleOk = canReschedule(a, now);
          return (
            <Card key={a.appointmentId}>
              <View style={s.appointmentHeader}>
                <View style={{ flex: 1 }}>
                  <Heading>{a.consultantName}</Heading>
                  {a.departmentName ? (
                    <Caption>{a.departmentName}</Caption>
                  ) : null}
                </View>
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
              </View>

              <View style={s.appointmentDetails}>
                <View style={s.detailItem}>
                  <Ionicons name="calendar-outline" size={14} color={colors.textMuted} />
                  <Text style={s.detailText}>{formatIsoDate(a.appointmentDate)}</Text>
                </View>
                <View style={s.detailItem}>
                  <Ionicons name="time-outline" size={14} color={colors.textMuted} />
                  <Text style={s.detailText}>{formatTimeRange(a.fromTime, a.toTime)}</Text>
                </View>
              </View>

              {scope === "upcoming" &&
                a.status !== "CANCELLED" &&
                a.status !== "COMPLETED" && (
                  <View style={s.actions}>
                    {rescheduleOk.allowed && (
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
                    )}
                    {cancelOk.allowed && (
                      <ActionButton
                        label={t("appointments.cancel")}
                        icon="close-circle-outline"
                        variant="danger"
                        onPress={() => handleCancel(a)}
                        disabled={cancelMutation.isPending}
                      />
                    )}
                  </View>
                )}
            </Card>
          );
        })
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <View style={s.pagination}>
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

      {/* Cancel Confirmation Sheet */}
      <ConfirmationSheet
        visible={!!cancelTarget}
        title="Cancel appointment?"
        message={
          cancelTarget
            ? `${formatIsoDate(cancelTarget.appointmentDate)} · ${formatTimeRange(cancelTarget.fromTime, cancelTarget.toTime)}\n${cancelTarget.consultantName}\n\nAre you sure you want to cancel this appointment?`
            : ""
        }
        confirmLabel="Cancel Appointment"
        cancelLabel="Keep Appointment"
        onConfirm={() => {
          if (cancelTarget) cancelMutation.mutate(cancelTarget.appointmentId);
        }}
        onCancel={() => setCancelTarget(null)}
        destructive
        busy={cancelMutation.isPending}
      />
    </Screen>
  );
}

const s = StyleSheet.create({
  /* Animated Segment Toggle */
  segmentRow: {
    flexDirection: "row",
    borderRadius: radius.lg,
    backgroundColor: colors.primarySoft,
    padding: 3,
    position: "relative",
  },
  segmentIndicator: {
    position: "absolute",
    top: 3,
    bottom: 3,
    left: 3,
    borderRadius: radius.md,
    backgroundColor: colors.primary,
  },
  segment: {
    flex: 1,
    paddingVertical: spacing.sm + 2,
    alignItems: "center",
    zIndex: 1,
  },
  segmentText: {
    ...typography.label,
    fontWeight: "600",
    color: colors.textMuted,
  },
  segmentTextActive: {
    color: colors.surface,
    fontWeight: "700",
  },

  /* Appointment Card */
  appointmentHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "flex-start",
    gap: spacing.sm,
  },
  appointmentDetails: {
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
  actions: {
    flexDirection: "row",
    gap: spacing.sm,
    marginTop: spacing.md,
  },

  /* Pagination */
  pagination: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: spacing.md,
    paddingVertical: spacing.md,
  },
});
