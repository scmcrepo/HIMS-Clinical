import React, { useCallback, useState } from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";
import { useLocalSearchParams, useRouter } from "expo-router";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import * as Crypto from "expo-crypto";
import { useContainer } from "../_layout";
import { QueryKeys } from "../../core/cachePolicy";
import { formatIsoDate, formatTimeRange } from "../../core/format";
import { t } from "../../i18n";
import {
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
import { colors, spacing, typography } from "../../ui/tokens";
import { PortalError } from "../../core/errors";

/**
 * Screen 8 — Booking confirmation (PRD §7, WO-019 §4.7).
 *
 * The idempotency key is generated once per mount. If the user taps
 * "Confirm" and the request times out, re-tapping sends the same key so
 * the backend creates at most one appointment. A new key is only created
 * if the user navigates away and comes back to the flow. WO-019 R2.
 */

export default function ConfirmScreen() {
  const { consultantId, slotId, date, fromTime, toTime } =
    useLocalSearchParams<{
      consultantId: string;
      slotId: string;
      date: string;
      fromTime: string;
      toTime: string;
    }>();
  const router = useRouter();
  const { api } = useContainer();
  const queryClient = useQueryClient();

  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<PortalError | null>(null);

  // One key per screen mount — survives retries, not new navigations.
  const [idempotencyKey] = useState(() => Crypto.randomUUID());

  const profile = useQuery({
    queryKey: QueryKeys.profile,
    queryFn: () => api.getProfile(),
  });

  // Fetch consultant details from the cached list
  const consultants = useQuery({
    queryKey: QueryKeys.consultants(),
    queryFn: () => api.listConsultants(),
    staleTime: 5 * 60_000,
  });

  const consultant = consultants.data?.find(
    (c) => c.consultantId === consultantId,
  );

  const handleConfirm = useCallback(async () => {
    if (busy) return; // double-tap guard (WO-019 R2)
    setBusy(true);
    setError(null);
    try {
      await api.bookAppointment(
        {
          providerId: consultantId,
          slotId,
          appointmentDate: date,
        },
        idempotencyKey,
      );
      // Invalidate appointments so the dashboard refreshes
      void queryClient.invalidateQueries({ queryKey: ["appointments"] });
      router.replace({
        pathname: "/book/success",
        params: {
          consultantName: consultant?.fullName ?? "",
          date,
          fromTime,
          toTime,
        },
      } as never);
    } catch (err) {
      setError(
        err instanceof PortalError
          ? err
          : new PortalError({ code: "UNKNOWN", message: "error.UNKNOWN" }),
      );
    } finally {
      setBusy(false);
    }
  }, [
    api,
    busy,
    consultantId,
    slotId,
    date,
    idempotencyKey,
    consultant,
    fromTime,
    toTime,
    queryClient,
    router,
  ]);

  if (profile.isLoading || consultants.isLoading) return <Loading />;

  return (
    <Screen>
      {/* Back */}
      <Pressable onPress={() => router.back()} hitSlop={8}>
        <Text style={styles.back}>{t("common.back")}</Text>
      </Pressable>

      <Title>{t("booking.confirmTitle")}</Title>

      <Card>
        <Row
          label={t("booking.doctor")}
          value={consultant?.fullName ?? "—"}
        />
        {consultant?.departmentName ? (
          <Caption>{consultant.departmentName}</Caption>
        ) : null}
        <Row label={t("booking.date")} value={formatIsoDate(date)} />
        <Row
          label={t("booking.time")}
          value={formatTimeRange(fromTime, toTime)}
        />
        <Row
          label={t("booking.patient")}
          value={profile.data?.fullName ?? "—"}
        />
        {profile.data?.branchName ? (
          <Row label={t("booking.branch")} value={profile.data.branchName} />
        ) : null}
      </Card>

      {error ? (
        <ErrorBanner
          messageKey={error.message}
          correlationId={error.correlationId}
          onRetry={handleConfirm}
        />
      ) : null}

      <Button
        label={t("booking.confirm")}
        onPress={handleConfirm}
        busy={busy}
        disabled={busy}
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  back: {
    ...typography.label,
    color: colors.primary,
    paddingVertical: spacing.xs,
  },
});
