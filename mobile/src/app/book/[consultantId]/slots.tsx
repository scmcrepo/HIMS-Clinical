import React, { useState, useMemo, useCallback } from "react";
import { Alert, Modal, Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import { useLocalSearchParams, useRouter } from "expo-router";
import { useQuery } from "@tanstack/react-query";
import { Ionicons } from "@expo/vector-icons";
import { useContainer } from "../../_layout";
import { QueryKeys } from "../../../core/cachePolicy";
import {
  bookableDates,
  hasSlotStarted,
  isSlotSelectable,
  slotPressure,
  toIsoDate,
} from "../../../core/booking";
import { formatTimeRange } from "../../../core/format";
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

/** Helper to format quick date pills with day mentions (e.g. Today, Tomorrow, Fri) */
function getDatePillMeta(dateIso: string, todayIso: string, tomorrowIso: string) {
  const parts = dateIso.split("-").map(Number);
  const year = parts[0] ?? 2026;
  const monthIndex = (parts[1] ?? 1) - 1;
  const day = parts[2] ?? 1;

  const d = new Date(year, monthIndex, day);

  let dayMention = "";
  if (dateIso === todayIso) {
    dayMention = "Today";
  } else if (dateIso === tomorrowIso) {
    dayMention = "Tomorrow";
  } else {
    dayMention = d.toLocaleDateString("en-US", { weekday: "short" });
  }

  const monthShort = d.toLocaleDateString("en-US", { month: "short" });

  return {
    dayMention,
    dayNum: day,
    monthShort,
    formatted: `${dayMention}, ${day} ${monthShort}`,
  };
}

export default function SlotsScreen() {
  const { consultantId, rescheduleAppointmentId } = useLocalSearchParams<{ consultantId: string; rescheduleAppointmentId?: string }>();
  const router = useRouter();
  const { api } = useContainer();

  const now = useMemo(() => new Date(), []);
  const dates = useMemo(() => bookableDates(now), [now]);
  const [selectedDate, setSelectedDate] = useState(dates[0]!);
  const [isPickerVisible, setPickerVisible] = useState(false);
  const [calendarViewDate, setCalendarViewDate] = useState(() => new Date());

  const todayIso = useMemo(() => toIsoDate(now), [now]);
  const tomorrowIso = useMemo(() => {
    const tmr = new Date(now);
    tmr.setDate(tmr.getDate() + 1);
    return toIsoDate(tmr);
  }, [now]);

  // First 3 quick days (Current & Next 2 Days)
  const quickDates = useMemo(() => dates.slice(0, 3), [dates]);
  const isCustomDateSelected = !quickDates.includes(selectedDate);

  const query = useQuery({
    queryKey: QueryKeys.availability(consultantId, selectedDate),
    queryFn: () => api.getAvailability(consultantId, selectedDate),
    enabled: !!consultantId && !!selectedDate,
    refetchInterval: 4000,
    staleTime: 0,
    refetchOnWindowFocus: true,
  });

  // Filter out slots whose time has already passed when selectedDate is TODAY
  const displaySlots = useMemo(() => {
    if (!query.data) return [];
    const isToday = selectedDate === todayIso;
    if (!isToday) return query.data;
    return query.data.filter((slot) => !hasSlotStarted(slot, now));
  }, [query.data, selectedDate, todayIso, now]);

  const openPicker = () => {
    const parts = selectedDate.split("-").map(Number);
    if (parts.length === 3 && parts[0] && parts[1]) {
      setCalendarViewDate(new Date(parts[0], parts[1] - 1, 1));
    } else {
      setCalendarViewDate(new Date());
    }
    setPickerVisible(true);
  };

  const upcomingQuery = useQuery({
    queryKey: QueryKeys.appointments("upcoming"),
    queryFn: () => api.listAppointments("upcoming"),
  });

  const isSlotBookedByPatient = useCallback(
    (slot: SlotAvailability) => {
      const list = upcomingQuery.data?.content ?? [];
      return list.some(
        (a) =>
          a.appointmentDate === selectedDate &&
          a.status !== "CANCELLED" &&
          (!rescheduleAppointmentId || a.appointmentId !== rescheduleAppointmentId) &&
          ((a.slotId && a.slotId === slot.slotId) ||
            (a.consultantId === consultantId && a.fromTime === slot.fromTime))
      );
    },
    [upcomingQuery.data, selectedDate, rescheduleAppointmentId, consultantId]
  );

  const handleSlotPress = (slot: SlotAvailability) => {
    if (isSlotBookedByPatient(slot)) {
      Alert.alert(
        "Already Booked",
        "You have already booked an appointment for this time slot on this date."
      );
      return;
    }
    router.push({
      pathname: "/book/confirm",
      params: {
        consultantId,
        slotId: slot.slotId,
        date: selectedDate,
        fromTime: slot.fromTime,
        toTime: slot.toTime,
        ...(rescheduleAppointmentId ? { rescheduleAppointmentId } : {}),
      },
    } as never);
  };

  // Calendar calculations
  const calYear = calendarViewDate.getFullYear();
  const calMonth = calendarViewDate.getMonth();
  const firstDayOfWeek = new Date(calYear, calMonth, 1).getDay();
  const daysInMonth = new Date(calYear, calMonth + 1, 0).getDate();

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

      {/* Redesigned Date Selection Section */}
      <View style={styles.dateSelectionSection}>
        <View style={styles.dateHeaderRow}>
          <Text style={styles.dateSectionLabel}>Choose Date</Text>
          <Pressable
            onPress={openPicker}
            style={[
              styles.calendarTriggerBtn,
              isCustomDateSelected && styles.calendarTriggerBtnActive,
            ]}
            hitSlop={6}
          >
            <Ionicons
              name="calendar-outline"
              size={15}
              color={isCustomDateSelected ? colors.surface : colors.primary}
            />
            <Text
              style={[
                styles.calendarTriggerText,
                isCustomDateSelected && styles.calendarTriggerTextActive,
              ]}
            >
              {isCustomDateSelected
                ? getDatePillMeta(selectedDate, todayIso, tomorrowIso).formatted
                : "Calendar"}
            </Text>
            <Ionicons
              name="chevron-down"
              size={13}
              color={isCustomDateSelected ? colors.surface : colors.primary}
            />
          </Pressable>
        </View>

        {/* Quick Date Pills (Current and Next 2 Days + Selected Date) */}
        <View style={styles.quickPillsRow}>
          {quickDates.map((dIso) => {
            const isSelected = dIso === selectedDate;
            const meta = getDatePillMeta(dIso, todayIso, tomorrowIso);
            return (
              <Pressable
                key={dIso}
                onPress={() => setSelectedDate(dIso)}
                style={[
                  styles.quickDatePill,
                  isSelected && styles.quickDatePillActive,
                ]}
              >
                <Text
                  style={[
                    styles.pillDayMention,
                    isSelected && styles.pillTextActive,
                  ]}
                >
                  {meta.dayMention}
                </Text>
                <Text
                  style={[
                    styles.pillDateNum,
                    isSelected && styles.pillTextActive,
                  ]}
                >
                  {meta.dayNum} {meta.monthShort}
                </Text>
              </Pressable>
            );
          })}

          {/* Custom Selected Date Pill (If user picked a date outside top 3 from Calendar) */}
          {isCustomDateSelected && (
            <Pressable
              onPress={() => setSelectedDate(selectedDate)}
              style={[styles.quickDatePill, styles.quickDatePillActive]}
            >
              <Text style={[styles.pillDayMention, styles.pillTextActive]}>
                {getDatePillMeta(selectedDate, todayIso, tomorrowIso).dayMention}
              </Text>
              <Text style={[styles.pillDateNum, styles.pillTextActive]}>
                {getDatePillMeta(selectedDate, todayIso, tomorrowIso).dayNum}{" "}
                {getDatePillMeta(selectedDate, todayIso, tomorrowIso).monthShort}
              </Text>
            </Pressable>
          )}
        </View>
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
      ) : !displaySlots.length ? (
        <EmptyState messageKey="slots.noneForDate" />
      ) : (
        <View style={styles.slotGrid}>
          {displaySlots.map((slot: SlotAvailability) => {
            const isAlreadyBooked = isSlotBookedByPatient(slot);
            const baseSelectable = isSlotSelectable(slot, selectedDate, now);
            const selectable = baseSelectable && !isAlreadyBooked;
            const pressure = slotPressure(slot);
            const isOpen = pressure === "open" && selectable;
            const isFilling = pressure === "filling" && selectable;
            const isFull = (pressure === "full" || !baseSelectable) && !isAlreadyBooked;

            return (
              <Pressable
                key={slot.slotId}
                disabled={!selectable}
                onPress={() => handleSlotPress(slot)}
                style={[
                  styles.slotCard,
                  isOpen && styles.slotOpenCard,
                  isFilling && styles.slotFillingCard,
                  isFull && styles.slotFullCard,
                  isAlreadyBooked && {
                    backgroundColor: "#F1F5F9",
                    borderColor: "#94A3B8",
                  },
                ]}
              >
                {/* Top Status Pill */}
                <View
                  style={[
                    styles.slotStatusBadge,
                    isOpen && styles.slotOpenBadge,
                    isFilling && styles.slotFillingBadge,
                    isFull && styles.slotFullBadge,
                    isAlreadyBooked && {
                      backgroundColor: "#E2E8F0",
                      borderColor: "#CBD5E1",
                    },
                  ]}
                >
                  <View
                    style={[
                      styles.slotStatusDot,
                      isOpen && styles.slotOpenDot,
                      isFilling && styles.slotFillingDot,
                      isFull && styles.slotFullDot,
                      isAlreadyBooked && {
                        backgroundColor: "#64748B",
                      },
                    ]}
                  />
                  <Text
                    style={[
                      styles.slotStatusText,
                      isOpen && styles.slotOpenStatusText,
                      isFilling && styles.slotFillingStatusText,
                      isFull && styles.slotFullStatusText,
                      isAlreadyBooked && {
                        color: "#334155",
                      },
                    ]}
                  >
                    {isAlreadyBooked
                      ? "BOOKED"
                      : isFull
                      ? "FULL"
                      : isFilling
                      ? "Fast Filling"
                      : "Available"}
                  </Text>
                </View>

                {/* Time Range */}
                <Text
                  style={[
                    styles.slotTime,
                    isOpen && styles.slotOpenTime,
                    isFilling && styles.slotFillingTime,
                    isFull && styles.slotFullTime,
                    isAlreadyBooked && {
                      color: "#475569",
                    },
                  ]}
                  numberOfLines={1}
                  adjustsFontSizeToFit
                >
                  {formatTimeRange(slot.fromTime, slot.toTime)}
                </Text>

                {/* Available Count */}
                <Text
                  style={[
                    styles.slotMeta,
                    isOpen && styles.slotOpenMeta,
                    isFilling && styles.slotFillingMeta,
                    isFull && styles.slotFullMeta,
                    isAlreadyBooked && {
                      color: "#64748B",
                      fontWeight: "700",
                    },
                  ]}
                >
                  {isAlreadyBooked
                    ? "Already Booked"
                    : slot.availableCount <= 0
                    ? t("slots.full")
                    : t("slots.available", { count: slot.availableCount })}
                </Text>
              </Pressable>
            );
          })}
        </View>
      )}

      {/* Modern Legend Bar */}
      <View style={styles.legendContainer}>
        <View style={[styles.legendPill, styles.legendPillOpen]}>
          <View style={[styles.legendDot, { backgroundColor: "#10B981" }]} />
          <Text style={[styles.legendText, { color: "#065F46" }]}>Available</Text>
        </View>
        <View style={[styles.legendPill, styles.legendPillFilling]}>
          <View style={[styles.legendDot, { backgroundColor: "#F59E0B" }]} />
          <Text style={[styles.legendText, { color: "#78350F" }]}>Filling Up</Text>
        </View>
        <View style={[styles.legendPill, styles.legendPillFull]}>
          <View style={[styles.legendDot, { backgroundColor: "#EF4444" }]} />
          <Text style={[styles.legendText, { color: "#991B1B" }]}>Full</Text>
        </View>
      </View>

      {/* Modern Date Picker Modal */}
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
                <Ionicons name="calendar" size={20} color={colors.primary} />
                <Text style={styles.modalTitle}>Select Appointment Date</Text>
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

            {/* Modal Bottom Actions */}
            <View style={styles.modalFooter}>
              <Pressable
                onPress={() => {
                  if (dates[0]) setSelectedDate(dates[0]);
                  setPickerVisible(false);
                }}
                style={styles.todayShortcutBtn}
              >
                <Ionicons name="today-outline" size={14} color={colors.primary} />
                <Text style={styles.todayShortcutText}>Jump to Today</Text>
              </Pressable>
            </View>
          </Pressable>
        </Pressable>
      </Modal>
    </Screen>
  );
}

const styles = StyleSheet.create({
  dateSelectionSection: {
    marginVertical: spacing.sm,
    gap: spacing.xs,
  },
  dateHeaderRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginBottom: spacing.xs,
  },
  dateSectionLabel: {
    ...typography.label,
    fontSize: 14,
    fontWeight: "700",
    color: colors.text,
  },
  calendarTriggerBtn: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    backgroundColor: colors.surfaceAlt,
    borderWidth: 1,
    borderColor: colors.border,
    paddingHorizontal: spacing.md,
    paddingVertical: 6,
    borderRadius: radius.pill,
  },
  calendarTriggerBtnActive: {
    backgroundColor: colors.primary,
    borderColor: colors.primary,
  },
  calendarTriggerText: {
    ...typography.caption,
    fontSize: 12,
    fontWeight: "700",
    color: colors.primary,
  },
  calendarTriggerTextActive: {
    color: colors.surface,
  },

  /* Quick Date Pills */
  quickPillsRow: {
    flexDirection: "row",
    gap: spacing.sm,
    paddingVertical: 4,
  },
  quickDatePill: {
    flex: 1,
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.xs,
    borderRadius: radius.md,
    backgroundColor: colors.surfaceAlt,
    borderWidth: 1,
    borderColor: colors.border,
    alignItems: "center",
    justifyContent: "center",
    gap: 2,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.04,
    shadowRadius: 2,
    elevation: 1,
  },
  quickDatePillActive: {
    backgroundColor: colors.primary,
    borderColor: colors.primary,
    shadowColor: colors.primary,
    shadowOffset: { width: 0, height: 3 },
    shadowOpacity: 0.25,
    shadowRadius: 6,
    elevation: 3,
  },
  pillDayMention: {
    ...typography.caption,
    fontSize: 11,
    fontWeight: "700",
    color: colors.textMuted,
    textTransform: "uppercase",
    letterSpacing: 0.5,
  },
  pillDateNum: {
    ...typography.label,
    fontSize: 13,
    fontWeight: "800",
    color: colors.text,
  },
  pillTextActive: {
    color: colors.surface,
  },

  /* Slot Cards */
  slotGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    justifyContent: "space-between",
    gap: spacing.md,
    marginVertical: spacing.xs,
  },
  slotCard: {
    width: "48%",
    paddingVertical: spacing.md,
    paddingHorizontal: spacing.sm,
    borderRadius: radius.lg,
    borderWidth: 1.5,
    borderColor: colors.border,
    alignItems: "center",
    gap: 6,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.04,
    shadowRadius: 4,
    elevation: 2,
  },
  slotOpenCard: {
    backgroundColor: "#F0FDF4",
    borderColor: "#A7F3D0",
  },
  slotFillingCard: {
    backgroundColor: "#FFFBEB",
    borderColor: "#FDE68A",
  },
  slotFullCard: {
    backgroundColor: "#FEF2F2",
    borderColor: "#FCA5A5",
    shadowColor: "#EF4444",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.08,
    shadowRadius: 4,
    elevation: 2,
  },

  /* Slot Status Badge */
  slotStatusBadge: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: radius.pill,
  },
  slotOpenBadge: {
    backgroundColor: "#DCFCE7",
  },
  slotFillingBadge: {
    backgroundColor: "#FEF3C7",
  },
  slotFullBadge: {
    backgroundColor: "#FEE2E2",
  },
  slotStatusDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
  },
  slotOpenDot: {
    backgroundColor: "#10B981",
  },
  slotFillingDot: {
    backgroundColor: "#F59E0B",
  },
  slotFullDot: {
    backgroundColor: "#EF4444",
  },
  slotStatusText: {
    fontSize: 10,
    fontWeight: "700",
    textTransform: "uppercase",
    letterSpacing: 0.4,
  },
  slotOpenStatusText: {
    color: "#047857",
  },
  slotFillingStatusText: {
    color: "#B45309",
  },
  slotFullStatusText: {
    color: "#B91C1C",
  },

  /* Slot Time & Meta */
  slotTime: {
    ...typography.label,
    fontSize: 14,
    fontWeight: "800",
    textAlign: "center",
  },
  slotOpenTime: {
    color: "#065F46",
  },
  slotFillingTime: {
    color: "#78350F",
  },
  slotFullTime: {
    color: "#991B1B",
  },
  slotMeta: {
    ...typography.caption,
    fontSize: 12,
    fontWeight: "600",
  },
  slotOpenMeta: {
    color: "#047857",
  },
  slotFillingMeta: {
    color: "#B45309",
  },
  slotFullMeta: {
    color: "#DC2626",
  },

  /* Legend Container */
  legendContainer: {
    flexDirection: "row",
    gap: spacing.sm,
    justifyContent: "center",
    paddingVertical: spacing.md,
    marginTop: spacing.xs,
  },
  legendPill: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    paddingHorizontal: spacing.md,
    paddingVertical: 6,
    borderRadius: radius.pill,
    borderWidth: 1,
  },
  legendPillOpen: {
    backgroundColor: "#ECFDF5",
    borderColor: "#A7F3D0",
  },
  legendPillFilling: {
    backgroundColor: "#FFFBEB",
    borderColor: "#FDE68A",
  },
  legendPillFull: {
    backgroundColor: "#FEF2F2",
    borderColor: "#FCA5A5",
  },
  legendDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  legendText: {
    ...typography.caption,
    fontSize: 12,
    fontWeight: "700",
  },

  /* Calendar Modal */
  modalBackdrop: {
    flex: 1,
    backgroundColor: "rgba(15, 23, 42, 0.5)",
    justifyContent: "center",
    alignItems: "center",
    padding: spacing.lg,
  },
  calendarModalCard: {
    width: "100%",
    maxWidth: 350,
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    padding: spacing.lg,
    borderWidth: 1,
    borderColor: colors.border,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 8 },
    shadowOpacity: 0.2,
    shadowRadius: 16,
    elevation: 8,
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
    fontSize: 16,
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
  modalFooter: {
    marginTop: spacing.md,
    paddingTop: spacing.xs,
    borderTopWidth: 1,
    borderTopColor: colors.border,
    alignItems: "center",
  },
  todayShortcutBtn: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    paddingVertical: spacing.xs,
    paddingHorizontal: spacing.md,
    borderRadius: radius.pill,
    backgroundColor: colors.primarySoft,
  },
  todayShortcutText: {
    ...typography.caption,
    fontSize: 12,
    fontWeight: "700",
    color: colors.primary,
  },
});
