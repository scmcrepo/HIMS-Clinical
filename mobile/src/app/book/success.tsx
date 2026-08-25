import React, { useEffect, useRef } from "react";
import { Animated, StyleSheet, View } from "react-native";
import { useLocalSearchParams, useRouter } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
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
  const { consultantName, date, fromTime, toTime, isReschedule } =
    useLocalSearchParams<{
      consultantName: string;
      date: string;
      fromTime: string;
      toTime: string;
      isReschedule?: string;
    }>();
  const router = useRouter();

  /* Animated checkmark — spring scale-in */
  const scaleAnim = useRef(new Animated.Value(0)).current;
  const opacityAnim = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    Animated.parallel([
      Animated.spring(scaleAnim, {
        toValue: 1,
        useNativeDriver: true,
        damping: 12,
        stiffness: 150,
        mass: 0.8,
      }),
      Animated.timing(opacityAnim, {
        toValue: 1,
        duration: 400,
        useNativeDriver: true,
      }),
    ]).start();
  }, [scaleAnim, opacityAnim]);

  return (
    <Screen>
      <View style={styles.center}>
        <Animated.View
          style={[
            styles.checkCircle,
            {
              transform: [{ scale: scaleAnim }],
              opacity: opacityAnim,
            },
          ]}
        >
          <Ionicons name="checkmark-sharp" size={36} color={colors.success} />
        </Animated.View>
      </View>

      <View style={styles.textCenter}>
        <Title>
          {isReschedule === "true" ? "Appointment Rescheduled!" : t("booking.successTitle")}
        </Title>
        <Body>
          {isReschedule === "true"
            ? "Your appointment has been successfully rescheduled to the new date and time."
            : t("booking.successBody")}
        </Body>
      </View>

      <Card>
        <Row label={t("booking.doctor")} value={consultantName ?? "—"} />
        <Row label={t("booking.date")} value={date ? formatIsoDate(date) : "—"} />
        <Row
          label={t("booking.time")}
          value={fromTime && toTime ? formatTimeRange(fromTime, toTime) : "—"}
        />
      </Card>

      <Button
        label={t("booking.goHome")}
        onPress={() => router.replace("/(tabs)" as never)}
        variant="primary"
        icon={<Ionicons name="home-outline" size={18} color={colors.surface} />}
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
    width: 80,
    height: 80,
    borderRadius: 40,
    backgroundColor: colors.successSoft,
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 3,
    borderColor: colors.success,
  },
  textCenter: {
    alignItems: "center",
    gap: spacing.xs,
  },
});
