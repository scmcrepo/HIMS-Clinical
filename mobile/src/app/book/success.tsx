import React from "react";
import { StyleSheet, View } from "react-native";
import { useLocalSearchParams, useRouter } from "expo-router";
import { formatIsoDate, formatTimeRange } from "../../core/format";
import { t } from "../../i18n";
import { Body, Button, Card, Heading, Row, Screen, Title } from "../../ui/components";
import { colors, spacing } from "../../ui/tokens";

/**
 * Screen 9 — Booking success (PRD §7, WO-019 §4.7).
 *
 * This is a replace destination, not a push: the patient should not
 * navigate back to the confirm screen and accidentally re-submit.
 */

export default function SuccessScreen() {
  const { consultantName, date, fromTime, toTime } =
    useLocalSearchParams<{
      consultantName: string;
      date: string;
      fromTime: string;
      toTime: string;
    }>();
  const router = useRouter();

  return (
    <Screen>
      <View style={styles.center}>
        <View style={styles.checkCircle}>
          <Heading>✓</Heading>
        </View>
      </View>

      <Title>{t("booking.successTitle")}</Title>
      <Body>{t("booking.successBody")}</Body>

      <Card>
        <Row label={t("booking.doctor")} value={consultantName ?? "—"} />
        <Row label={t("booking.date")} value={date ? formatIsoDate(date) : "—"} />
        <Row
          label={t("booking.time")}
          value={fromTime && toTime ? formatTimeRange(fromTime, toTime) : "—"}
        />
      </Card>

      <Button
        label={t("dashboard.bookAppointment")}
        onPress={() => router.replace("/(tabs)" as never)}
        variant="secondary"
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  center: {
    alignItems: "center",
    paddingVertical: spacing.xl,
  },
  checkCircle: {
    width: 72,
    height: 72,
    borderRadius: 36,
    backgroundColor: colors.successSoft,
    alignItems: "center",
    justifyContent: "center",
  },
});
