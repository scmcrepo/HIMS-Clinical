import React, { useState, useMemo } from "react";
import { Modal, Pressable, StyleSheet, Text, View } from "react-native";
import { useLocalSearchParams, useRouter } from "expo-router";
import { useQuery } from "@tanstack/react-query";
import { Ionicons } from "@expo/vector-icons";
import { useContainer } from "../../_layout";
import { QueryKeys } from "../../../core/cachePolicy";
import {
  bookableDates,
  isSlotSelectable,
  slotPressure,
  toIsoDate,
} from "../../../core/booking";
import { formatIsoDate, formatTimeRange } from "../../../core/format";
import { t } from "../../../i18n";
import {
  BackButton,
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

const MONTH_NAMES = [
  "January", "February", "March", "April", "May", "June",
  "July", "August", "September", "October", "November", "December",
];
const WEEKDAY_NAMES = ["SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"];

/**
 * Screen 7 — Slot availability (PRD §6c, WO-019 §4.7).
 *
 * Full slots are shown but disabled (PRD §6c): hiding them makes a doctor
 * with a full morning look like one who does not work mornings.
 *
 * Dates are limited to 30 days ahead (BOOKING_WINDOW_DAYS).
 */

export default function SlotsScreen() {
  const { consultantId } = useLocalSearchParams<{ consultantId: string }>();
  const router = useRouter();
  const { api } = useContainer();

  const now = useMemo(() => new Date(), []);
  const dates = useMemo(() => bookableDates(now), [now]);
  const [selectedDate, setSelectedDate] = useState(dates[0]!);
  const [isPickerVisible, setPickerVisible] = useState(false);
  const [calendarViewDate, setCalendarViewDate] = useState(() => new Date());

  const currentIndex = dates.indexOf(selectedDate);

  const query = useQuery({
    queryKey: QueryKeys.availability(consultantId, selectedDate),
    queryFn: () => api.getAvailability(consultantId, selectedDate),
    enabled: !!consultantId && !!selectedDate,
  });

  const handlePrevDate = () => {
    if (currentIndex > 0) {
      setSelectedDate(dates[currentIndex - 1]!);
    }
  };

  const handleNextDate = () => {
    if (currentIndex < dates.length - 1 && currentIndex !== -1) {
      setSelectedDate(dates[currentIndex + 1]!);
    }
  };

  const openPicker = () => {
    const parts = selectedDate.split("-").map(Number);
    if (parts.length === 3 && parts[0] && parts[1]) {
      setCalendarViewDate(new Date(parts[0], parts[1] - 1, 1));
    } else {
      setCalendarViewDate(new Date());
    }
    setPickerVisible(true);
  };

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

  // Calendar calculations
  const calYear = calendarViewDate.getFullYear();
  const calMonth = calendarViewDate.getMonth();
  const firstDayOfWeek = new Date(calYear, calMonth, 1).getDay();
  const daysInMonth = new Date(calYear, calMonth + 1, 0).getDate();
  const todayIso = toIsoDate(now);

  const canGoPrevMonth =
    calYear > now.getFullYear() ||
    (calYear === now.getFullYear() && calMonth > now.getMonth());

  const maxDateParts = (dates[dates.length - 1] ?? "").split("-").map(Number);
  const maxYear = maxDateParts[0] ?? now.getFullYear();
  const maxMonth = (maxDateParts[1] ?? 1) - 1;
  const canGoNextMonth =
    calYear < maxYear || (calYear === maxYear && calMonth < maxMonth);

  return (
    <Screen>
      {/* Back */}
      <BackButton onPress={() => router.back()} label={t("common.back")} />

      <Title>{t("slots.title")}</Title>

      {/* Date Navigator: [ ← ]  [ 📅 20 Aug 2026 ▾ ]  [ → ] */}
      <View style={styles.dateNavCard}>
        <Pressable
          onPress={handlePrevDate}
          disabled={currentIndex <= 0}
          style={[
            styles.navArrowBtn,
            currentIndex <= 0 && styles.navArrowBtnDisabled,
          ]}
          hitSlop={8}
          accessibilityLabel="Previous date"
        >
          <Ionicons
            name="chevron-back"
            size={20}
            color={currentIndex <= 0 ? colors.border : colors.primary}
          />
        </Pressable>

        <Pressable
          onPress={openPicker}
          style={styles.datePickerTrigger}
          accessibilityLabel="Open date picker"
        >
          <View style={styles.calendarIconBadge}>
            <Ionicons name="calendar" size={16} color={colors.primary} />
          </View>
          <Text style={styles.selectedDateText}>
            {formatIsoDate(selectedDate)}
          </Text>
          <Ionicons name="chevron-down" size={14} color={colors.textMuted} />
        </Pressable>

        <Pressable
          onPress={handleNextDate}
          disabled={currentIndex >= dates.length - 1 || currentIndex === -1}
          style={[
            styles.navArrowBtn,
            (currentIndex >= dates.length - 1 || currentIndex === -1) &&
              styles.navArrowBtnDisabled,
          ]}
          hitSlop={8}
          accessibilityLabel="Next date"
        >
          <Ionicons
            name="chevron-forward"
            size={20}
            color={
              currentIndex >= dates.length - 1 || currentIndex === -1
                ? colors.border
                : colors.primary
            }
          />
        </Pressable>
      </View>

      {/* Slots grid — 2 columns */}
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
                  numberOfLines={1}
                  adjustsFontSizeToFit
                >
                  {formatTimeRange(slot.fromTime, slot.toTime)}
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

      {/* Date Picker Modal */}
      <Modal
        visible={isPickerVisible}
        transparent
        animationType="fade"
        onRequestClose={() => setPickerVisible(false)}
      >
        <Pressable
          style={styles.modalBackdrop}
          onPress={() => setPickerVisible(false)}
        >
          <Pressable
            style={styles.calendarModalCard}
            onPress={(e) => e.stopPropagation()}
          >
            {/* Modal Header */}
            <View style={styles.modalHeader}>
              <View style={styles.modalHeaderTitleGroup}>
                <Ionicons name="calendar-outline" size={18} color={colors.primary} />
                <Text style={styles.modalTitle}>Choose Date</Text>
              </View>
              <Pressable
                onPress={() => setPickerVisible(false)}
                hitSlop={8}
                style={styles.closeIconBtn}
              >
                <Ionicons name="close" size={18} color={colors.textMuted} />
              </Pressable>
            </View>

            {/* Month / Year header */}
            <View style={styles.monthHeaderRow}>
              <Pressable
                onPress={() =>
                  setCalendarViewDate(new Date(calYear, calMonth - 1, 1))
                }
                disabled={!canGoPrevMonth}
                style={[
                  styles.monthNavBtn,
                  !canGoPrevMonth && styles.monthNavBtnDisabled,
                ]}
                hitSlop={8}
              >
                <Ionicons
                  name="chevron-back"
                  size={18}
                  color={!canGoPrevMonth ? colors.border : colors.text}
                />
              </Pressable>

              <Text style={styles.monthTitleText}>
                {MONTH_NAMES[calMonth]} {calYear}
              </Text>

              <Pressable
                onPress={() =>
                  setCalendarViewDate(new Date(calYear, calMonth + 1, 1))
                }
                disabled={!canGoNextMonth}
                style={[
                  styles.monthNavBtn,
                  !canGoNextMonth && styles.monthNavBtnDisabled,
                ]}
                hitSlop={8}
              >
                <Ionicons
                  name="chevron-forward"
                  size={18}
                  color={!canGoNextMonth ? colors.border : colors.text}
                />
              </Pressable>
            </View>

            {/* Weekday headers */}
            <View style={styles.weekdaysRow}>
              {WEEKDAY_NAMES.map((w) => (
                <Text key={w} style={styles.weekdayLabel}>
                  {w}
                </Text>
              ))}
            </View>

            {/* Days grid */}
            <View style={styles.daysGrid}>
              {Array.from({ length: firstDayOfWeek }).map((_, idx) => (
                <View key={`empty-${idx}`} style={styles.dayCell} />
              ))}

              {Array.from({ length: daysInMonth }).map((_, idx) => {
                const dayNum = idx + 1;
                const iso = `${calYear}-${String(calMonth + 1).padStart(2, "0")}-${String(dayNum).padStart(2, "0")}`;
                const isBookable = dates.includes(iso);
                const isSelected = iso === selectedDate;
                const isToday = iso === todayIso;

                return (
                  <Pressable
                    key={iso}
                    disabled={!isBookable}
                    onPress={() => {
                      setSelectedDate(iso);
                      setPickerVisible(false);
                    }}
                    style={[
                      styles.dayCell,
                      isBookable && styles.dayCellBookable,
                      isSelected && styles.dayCellSelected,
                      isToday && !isSelected && styles.dayCellToday,
                    ]}
                  >
                    <Text
                      style={[
                        styles.dayNumberText,
                        !isBookable && styles.dayTextDisabled,
                        isBookable && styles.dayTextBookable,
                        isSelected && styles.dayTextSelected,
                        isToday && !isSelected && styles.dayTextToday,
                      ]}
                    >
                      {dayNum}
                    </Text>
                  </Pressable>
                );
              })}
            </View>
          </Pressable>
        </Pressable>
      </Modal>
    </Screen>
  );
}

const styles = StyleSheet.create({
  dateNavCard: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    paddingHorizontal: spacing.sm,
    paddingVertical: 6,
    marginVertical: spacing.sm,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 3,
    elevation: 1,
  },
  navArrowBtn: {
    width: 38,
    height: 38,
    borderRadius: radius.md,
    backgroundColor: colors.surfaceAlt,
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 1,
    borderColor: colors.border,
  },
  navArrowBtnDisabled: {
    opacity: 0.4,
    backgroundColor: colors.surfaceAlt,
    borderColor: colors.border,
  },
  datePickerTrigger: {
    flex: 1,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    paddingVertical: spacing.xs,
    paddingHorizontal: spacing.sm,
    gap: 8,
  },
  calendarIconBadge: {
    width: 30,
    height: 30,
    borderRadius: radius.sm,
    backgroundColor: colors.primarySoft,
    alignItems: "center",
    justifyContent: "center",
  },
  selectedDateText: {
    ...typography.heading,
    fontSize: 16,
    color: colors.text,
    fontWeight: "700",
  },
  slotGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    justifyContent: "space-between",
    gap: spacing.md,
  },
  slotCard: {
    width: "48%",
    paddingVertical: spacing.md,
    paddingHorizontal: spacing.sm,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    alignItems: "center",
    gap: 4,
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
    fontSize: 13,
    fontWeight: "700",
    color: colors.text,
    textAlign: "center",
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
  modalBackdrop: {
    flex: 1,
    backgroundColor: "rgba(15, 23, 42, 0.45)",
    justifyContent: "center",
    alignItems: "center",
    padding: spacing.lg,
  },
  calendarModalCard: {
    width: "100%",
    maxWidth: 340,
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    padding: spacing.lg,
    borderWidth: 1,
    borderColor: colors.border,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 6 },
    shadowOpacity: 0.15,
    shadowRadius: 12,
    elevation: 6,
  },
  modalHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: spacing.md,
  },
  modalHeaderTitleGroup: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
  },
  modalTitle: {
    ...typography.heading,
    fontSize: 17,
    color: colors.text,
    fontWeight: "700",
  },
  closeIconBtn: {
    width: 30,
    height: 30,
    borderRadius: 15,
    backgroundColor: colors.surfaceAlt,
    alignItems: "center",
    justifyContent: "center",
  },
  monthHeaderRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: spacing.md,
    backgroundColor: colors.surfaceAlt,
    borderRadius: radius.md,
    padding: 4,
  },
  monthNavBtn: {
    width: 32,
    height: 32,
    borderRadius: radius.sm,
    alignItems: "center",
    justifyContent: "center",
  },
  monthNavBtnDisabled: {
    opacity: 0.25,
  },
  monthTitleText: {
    ...typography.label,
    fontSize: 14,
    fontWeight: "700",
    color: colors.text,
  },
  weekdaysRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    marginBottom: spacing.xs,
    paddingBottom: spacing.xs,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  weekdayLabel: {
    flex: 1,
    textAlign: "center",
    fontSize: 11,
    fontWeight: "700",
    color: colors.textMuted,
  },
  daysGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    justifyContent: "flex-start",
  },
  dayCell: {
    width: "14.28%",
    height: 38,
    alignItems: "center",
    justifyContent: "center",
    marginVertical: 2,
    borderRadius: 19,
  },
  dayCellBookable: {
    backgroundColor: "transparent",
  },
  dayCellSelected: {
    backgroundColor: colors.primary,
    shadowColor: colors.primary,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.3,
    shadowRadius: 4,
    elevation: 2,
  },
  dayCellToday: {
    borderWidth: 1.5,
    borderColor: colors.primary,
  },
  dayNumberText: {
    fontSize: 13,
  },
  dayTextDisabled: {
    color: "#CBD5E1",
  },
  dayTextBookable: {
    color: colors.text,
    fontWeight: "600",
  },
  dayTextSelected: {
    color: colors.surface,
    fontWeight: "700",
  },
  dayTextToday: {
    color: colors.primary,
    fontWeight: "700",
  },
});
