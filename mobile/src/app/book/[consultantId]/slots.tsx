import React, { useState, useMemo } from "react";
import { Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import { useLocalSearchParams, useRouter } from "expo-router";
import { useQuery } from "@tanstack/react-query";
import { useContainer } from "../../_layout";
import { QueryKeys } from "../../../core/cachePolicy";
import {
  bookableDates,
  isSlotSelectable,
  slotPressure,
} from "../../../core/booking";
import { formatIsoDate, formatTime } from "../../../core/format";
import { t } from "../../../i18n";
import {
  Body,
  Caption,
  EmptyState,
  ErrorBanner,
  Loading,
  Screen,
  Title,
} from "../../../ui/components";
import { colors, radius, spacing, typography } from "../../../ui/tokens";
import { PortalError } from "../../../core/errors";
import type { SlotAvailability } from "../../../core/contracts";

/**
 * Screen 7 — Slot availability (PRD §6c, WO-019 §4.7).
 *
 * Full slots are shown but disabled (PRD §6c): hiding them makes a doctor
 * with a full morning look like one who does not work mornings.
 *
 * Dates are limited to 14 days ahead (BOOKING_WINDOW_DAYS).
 */

export default function SlotsScreen() {
  const { consultantId } = useLocalSearchParams<{ consultantId: string }>();
  const router = useRouter();
  const { api } = useContainer();

  const dates = useMemo(() => bookableDates(new Date()), []);
  const [selectedDate, setSelectedDate] = useState(dates[0]!);

  const query = useQuery({
    queryKey: QueryKeys.availability(consultantId, selectedDate),
    queryFn: () => api.getAvailability(consultantId, selectedDate),
    enabled: !!consultantId && !!selectedDate,
  });

  const now = new Date();

  const handleSlotPress = (slot: SlotAvailability) => {
    router.push({
      pathname: "/book/confirm",
      params: {
        consultantId,
        slotId: slot.slotId,
        date: selectedDate,
        fromTime: slot.fromTime,
        toTime: slot.toTime,
      },
    } as never);
  };

  return (
    <Screen>
      {/* Back */}
      <Pressable onPress={() => router.back()} hitSlop={8}>
        <Text style={styles.back}>{t("common.back")}</Text>
      </Pressable>

      <Title>{t("slots.title")}</Title>

      {/* Date picker — horizontal scroll */}
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.dateStrip}
      >
        {dates.map((d) => {
          const active = d === selectedDate;
          return (
            <Pressable
              key={d}
              onPress={() => setSelectedDate(d)}
              style={[styles.dateChip, active && styles.dateChipActive]}
            >
              <Text
                style={[styles.dateText, active && styles.dateTextActive]}
              >
                {formatIsoDate(d)}
              </Text>
            </Pressable>
          );
        })}
      </ScrollView>

      {/* Slots grid */}
      {query.isLoading ? (
        <Loading />
      ) : query.isError ? (
        <ErrorBanner
          messageKey={
            (query.error as PortalError)?.message ?? "error.UNKNOWN"
          }
          correlationId={(query.error as PortalError)?.correlationId}
          onRetry={() => void query.refetch()}
        />
      ) : !query.data?.length ? (
        <EmptyState messageKey="slots.noneForDate" />
      ) : (
        <View style={styles.slotGrid}>
          {query.data.map((slot: SlotAvailability) => {
            const selectable = isSlotSelectable(slot, selectedDate, now);
            const pressure = slotPressure(slot);
            return (
              <Pressable
                key={slot.slotId}
                disabled={!selectable}
                onPress={() => handleSlotPress(slot)}
                style={[
                  styles.slotCard,
                  pressure === "open" && styles.slotOpen,
                  pressure === "filling" && styles.slotFilling,
                  pressure === "full" && styles.slotFull,
                  !selectable && styles.slotDisabled,
                ]}
              >
                <Text
                  style={[
                    styles.slotTime,
                    !selectable && styles.slotTimeDisabled,
                  ]}
                >
                  {formatTime(slot.fromTime)}
                </Text>
                <Text
                  style={[
                    styles.slotMeta,
                    !selectable && styles.slotTimeDisabled,
                  ]}
                >
                  {slot.availableCount <= 0
                    ? t("slots.full")
                    : t("slots.available", { count: slot.availableCount })}
                </Text>
              </Pressable>
            );
          })}
        </View>
      )}

      {/* Legend */}
      <View style={styles.legend}>
        <View style={styles.legendItem}>
          <View style={[styles.legendDot, { backgroundColor: colors.successSoft }]} />
          <Caption>Available</Caption>
        </View>
        <View style={styles.legendItem}>
          <View style={[styles.legendDot, { backgroundColor: colors.warningSoft }]} />
          <Caption>Filling up</Caption>
        </View>
        <View style={styles.legendItem}>
          <View style={[styles.legendDot, { backgroundColor: colors.surfaceAlt }]} />
          <Caption>Full</Caption>
        </View>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  back: {
    ...typography.label,
    color: colors.primary,
    paddingVertical: spacing.xs,
  },
  dateStrip: {
    gap: spacing.sm,
    paddingVertical: spacing.sm,
  },
  dateChip: {
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    borderRadius: radius.pill,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
  },
  dateChipActive: {
    backgroundColor: colors.primary,
    borderColor: colors.primary,
  },
  dateText: {
    ...typography.label,
    color: colors.text,
  },
  dateTextActive: {
    color: colors.surface,
  },
  slotGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: spacing.sm,
  },
  slotCard: {
    width: "30%",
    paddingVertical: spacing.md,
    paddingHorizontal: spacing.sm,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    alignItems: "center",
    gap: 2,
  },
  slotOpen: {
    backgroundColor: colors.successSoft,
    borderColor: colors.success,
  },
  slotFilling: {
    backgroundColor: colors.warningSoft,
    borderColor: colors.warning,
  },
  slotFull: {
    backgroundColor: colors.surfaceAlt,
    borderColor: colors.border,
  },
  slotDisabled: {
    opacity: 0.55,
  },
  slotTime: {
    ...typography.label,
    color: colors.text,
  },
  slotTimeDisabled: {
    color: colors.textMuted,
  },
  slotMeta: {
    ...typography.caption,
    color: colors.textMuted,
  },
  legend: {
    flexDirection: "row",
    gap: spacing.lg,
    justifyContent: "center",
    paddingVertical: spacing.md,
  },
  legendItem: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.xs,
  },
  legendDot: {
    width: 12,
    height: 12,
    borderRadius: 6,
  },
});
