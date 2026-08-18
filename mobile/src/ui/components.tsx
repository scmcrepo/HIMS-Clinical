import React from "react";
import {
  ActivityIndicator,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
  type TextInputProps,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { t } from "../i18n";
import { colors, MIN_TOUCH_TARGET, radius, spacing, typography } from "./tokens";

/**
 * Primitives.
 *
 * Sized against the patient population rather than a design system default:
 * 16pt body minimum, 48dp touch targets, and error text that sits beside the
 * field it belongs to rather than in a toast. A large share of users will be
 * older, on a small screen, in daylight, possibly unwell.
 */

export function Screen({
  children,
  scroll = true,
}: {
  children: React.ReactNode;
  scroll?: boolean;
}) {
  return (
    <SafeAreaView style={styles.screen} edges={["top", "bottom"]}>
      {scroll ? (
        <ScrollView
          contentContainerStyle={styles.scrollContent}
          keyboardShouldPersistTaps="handled"
        >
          {children}
        </ScrollView>
      ) : (
        <View style={styles.scrollContent}>{children}</View>
      )}
    </SafeAreaView>
  );
}

export function Title({ children }: { children: React.ReactNode }) {
  return <Text style={styles.title}>{children}</Text>;
}

export function Subtitle({ children }: { children: React.ReactNode }) {
  return <Text style={styles.subtitle}>{children}</Text>;
}

export function Heading({ children }: { children: React.ReactNode }) {
  return <Text style={styles.heading}>{children}</Text>;
}

export function Body({ children }: { children: React.ReactNode }) {
  return <Text style={styles.body}>{children}</Text>;
}

export function Caption({ children }: { children: React.ReactNode }) {
  return <Text style={styles.caption}>{children}</Text>;
}

export function Button({
  label,
  onPress,
  disabled,
  busy,
  variant = "primary",
}: {
  label: string;
  onPress: () => void;
  disabled?: boolean;
  busy?: boolean;
  variant?: "primary" | "secondary" | "danger";
}) {
  const inactive = disabled || busy;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled: !!inactive, busy: !!busy }}
      accessibilityLabel={label}
      onPress={onPress}
      disabled={inactive}
      style={({ pressed }) => [
        styles.button,
        variant === "secondary" && styles.buttonSecondary,
        variant === "danger" && styles.buttonDanger,
        inactive && styles.buttonDisabled,
        pressed && !inactive && styles.buttonPressed,
      ]}
    >
      {busy ? (
        <ActivityIndicator
          color={variant === "secondary" ? colors.primary : colors.surface}
        />
      ) : (
        <Text
          style={[
            styles.buttonLabel,
            variant === "secondary" && styles.buttonLabelSecondary,
          ]}
        >
          {label}
        </Text>
      )}
    </Pressable>
  );
}

export function TextField({
  label,
  error,
  ...props
}: TextInputProps & { label: string; error?: string | undefined }) {
  return (
    <View style={styles.fieldGroup}>
      <Text style={styles.fieldLabel}>{label}</Text>
      <TextInput
        accessibilityLabel={label}
        placeholderTextColor={colors.textMuted}
        style={[styles.input, error ? styles.inputError : null]}
        {...props}
      />
      {error ? (
        // Beside the field, not in a toast: a toast is gone before someone
        // re-reading the form finds out which field it meant.
        <Text style={styles.fieldError} accessibilityLiveRegion="polite">
          {t(error)}
        </Text>
      ) : null}
    </View>
  );
}

export function Card({
  children,
  onPress,
  selected,
  accessibilityLabel,
}: {
  children: React.ReactNode;
  onPress?: () => void;
  selected?: boolean;
  accessibilityLabel?: string;
}) {
  const content = <View style={styles.cardInner}>{children}</View>;
  if (!onPress) {
    return <View style={[styles.card, selected && styles.cardSelected]}>{content}</View>;
  }
  return (
    <Pressable
      accessibilityRole="button"
      {...(accessibilityLabel ? { accessibilityLabel } : {})}
      accessibilityState={{ selected: !!selected }}
      onPress={onPress}
      style={({ pressed }) => [
        styles.card,
        selected && styles.cardSelected,
        pressed && styles.cardPressed,
      ]}
    >
      {content}
    </Pressable>
  );
}

/**
 * Errors are shown with their correlation id. It looks like clutter until a
 * patient calls the hospital and support can paste it into Loki instead of
 * guessing which of four hundred requests that minute was theirs.
 */
export function ErrorBanner({
  messageKey,
  correlationId,
  onRetry,
}: {
  messageKey: string;
  correlationId?: string | null;
  onRetry?: () => void;
}) {
  return (
    <View style={styles.errorBanner} accessibilityLiveRegion="assertive">
      <Text style={styles.errorText}>{t(messageKey)}</Text>
      {correlationId ? (
        <Text style={styles.errorRef} selectable>
          {t("error.reference", { correlationId })}
        </Text>
      ) : null}
      {onRetry ? (
        <Pressable onPress={onRetry} accessibilityRole="button" hitSlop={8}>
          <Text style={styles.errorRetry}>{t("common.retry")}</Text>
        </Pressable>
      ) : null}
    </View>
  );
}

export function Badge({
  label,
  tone = "neutral",
}: {
  label: string;
  tone?: "neutral" | "success" | "warning" | "danger";
}) {
  return (
    <View style={[styles.badge, styles[`badge_${tone}`]]}>
      <Text style={[styles.badgeText, styles[`badgeText_${tone}`]]}>{label}</Text>
    </View>
  );
}

export function Avatar({ initials: text }: { initials: string }) {
  return (
    <View style={styles.avatar} accessibilityElementsHidden importantForAccessibility="no">
      <Text style={styles.avatarText}>{text}</Text>
    </View>
  );
}

export function EmptyState({ messageKey }: { messageKey: string }) {
  return (
    <View style={styles.empty}>
      <Text style={styles.emptyText}>{t(messageKey)}</Text>
    </View>
  );
}

export function Loading() {
  return (
    <View style={styles.loading}>
      <ActivityIndicator size="large" color={colors.primary} />
    </View>
  );
}

export function Row({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.row}>
      <Text style={styles.rowLabel}>{label}</Text>
      <Text style={styles.rowValue}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.surfaceAlt },
  scrollContent: { padding: spacing.lg, gap: spacing.md, flexGrow: 1 },
  title: { ...typography.display, color: colors.text },
  subtitle: { ...typography.body, color: colors.textMuted },
  heading: { ...typography.heading, color: colors.text, marginTop: spacing.md },
  body: { ...typography.body, color: colors.text },
  caption: { ...typography.caption, color: colors.textMuted },

  button: {
    minHeight: MIN_TOUCH_TARGET,
    borderRadius: radius.md,
    backgroundColor: colors.primary,
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: spacing.lg,
  },
  buttonSecondary: {
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
  },
  buttonDanger: { backgroundColor: colors.danger },
  buttonDisabled: { opacity: 0.45 },
  buttonPressed: { opacity: 0.85 },
  buttonLabel: { ...typography.label, fontSize: 16, color: colors.surface },
  buttonLabelSecondary: { color: colors.primary },

  fieldGroup: { gap: spacing.xs },
  fieldLabel: { ...typography.label, color: colors.text },
  input: {
    minHeight: MIN_TOUCH_TARGET,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    backgroundColor: colors.surface,
    paddingHorizontal: spacing.md,
    ...typography.body,
    color: colors.text,
  },
  inputError: { borderColor: colors.danger },
  fieldError: { ...typography.caption, color: colors.danger },

  card: {
    borderRadius: radius.lg,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
  },
  cardSelected: { borderColor: colors.primary, borderWidth: 2 },
  cardPressed: { opacity: 0.9 },
  cardInner: { padding: spacing.lg, gap: spacing.xs },

  errorBanner: {
    backgroundColor: colors.dangerSoft,
    borderRadius: radius.md,
    padding: spacing.md,
    gap: spacing.xs,
  },
  errorText: { ...typography.body, color: colors.danger },
  errorRef: { ...typography.caption, color: colors.textMuted },
  errorRetry: { ...typography.label, color: colors.danger },

  badge: {
    alignSelf: "flex-start",
    paddingHorizontal: spacing.sm,
    paddingVertical: 2,
    borderRadius: radius.pill,
    backgroundColor: colors.surfaceAlt,
  },
  badge_neutral: { backgroundColor: colors.surfaceAlt },
  badge_success: { backgroundColor: colors.successSoft },
  badge_warning: { backgroundColor: colors.warningSoft },
  badge_danger: { backgroundColor: colors.dangerSoft },
  badgeText: { ...typography.caption, fontWeight: "600" },
  badgeText_neutral: { color: colors.textMuted },
  badgeText_success: { color: colors.success },
  badgeText_warning: { color: colors.warning },
  badgeText_danger: { color: colors.danger },

  avatar: {
    width: 48,
    height: 48,
    borderRadius: radius.pill,
    backgroundColor: colors.primarySoft,
    alignItems: "center",
    justifyContent: "center",
  },
  avatarText: { ...typography.label, color: colors.primaryDark },

  empty: { padding: spacing.xl, alignItems: "center" },
  emptyText: { ...typography.body, color: colors.textMuted, textAlign: "center" },
  loading: { padding: spacing.xxl, alignItems: "center" },

  row: {
    flexDirection: "row",
    justifyContent: "space-between",
    paddingVertical: spacing.sm,
    gap: spacing.md,
  },
  rowLabel: { ...typography.body, color: colors.textMuted },
  rowValue: { ...typography.body, color: colors.text, flexShrink: 1, textAlign: "right" },
});
