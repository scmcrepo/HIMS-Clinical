import React, { useCallback, useEffect, useRef, useState } from "react";
import {
  ActivityIndicator,
  Animated,
  Modal,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
  Image,
  type TextInputProps,
  type ViewStyle,
} from "react-native";
import Constants from "expo-constants";
import { SafeAreaView } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";
import { t } from "../i18n";
import { colors, MIN_TOUCH_TARGET, radius, spacing, typography, shadows, animation } from "./tokens";

/**
 * Primitives.
 *
 * Sized against the patient population rather than a design system default:
 * 16pt body minimum, 48dp touch targets, and error text that sits beside the
 * field it belongs to rather than in a toast. A large share of users will be
 * older, on a small screen, in daylight, possibly unwell.
 */

/* =========================================================================
 * SCREEN — root container with optional pull-to-refresh
 * ======================================================================= */

export function Screen({
  children,
  scroll = true,
  onRefresh,
  refreshing = false,
}: {
  children: React.ReactNode;
  scroll?: boolean;
  onRefresh?: () => void;
  refreshing?: boolean;
}) {
  return (
    <SafeAreaView style={styles.screen} edges={["top", "bottom"]}>
      {scroll ? (
        <ScrollView
          contentContainerStyle={styles.scrollContent}
          keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false}
          refreshControl={
            onRefresh ? (
              <RefreshControl
                refreshing={refreshing}
                onRefresh={onRefresh}
                tintColor={colors.primary}
                colors={[colors.primary]}
              />
            ) : undefined
          }
        >
          {children}
        </ScrollView>
      ) : (
        <View style={styles.scrollContent}>{children}</View>
      )}
    </SafeAreaView>
  );
}

/* =========================================================================
 * TYPOGRAPHY
 * ======================================================================= */

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

/* =========================================================================
 * SECTION HEADER — uppercase micro-label ("UPCOMING APPOINTMENTS")
 * ======================================================================= */

export function SectionHeader({ children }: { children: React.ReactNode }) {
  return <Text style={styles.sectionHeader}>{children}</Text>;
}

/* =========================================================================
 * DIVIDER — subtle horizontal rule
 * ======================================================================= */

export function Divider({ label }: { label?: string } = {}) {
  if (label) {
    return (
      <View style={styles.dividerRow}>
        <View style={styles.dividerLine} />
        <Text style={styles.dividerLabel}>{label}</Text>
        <View style={styles.dividerLine} />
      </View>
    );
  }
  return <View style={styles.dividerSingle} />;
}

/* =========================================================================
 * BUTTON — with press animation + optional haptic
 * ======================================================================= */

export function Button({
  label,
  onPress,
  disabled,
  busy,
  variant = "primary",
  icon,
}: {
  label: string;
  onPress: () => void;
  disabled?: boolean;
  busy?: boolean;
  variant?: "primary" | "secondary" | "danger";
  icon?: React.ReactNode;
}) {
  const inactive = disabled || busy;
  const scaleAnim = useRef(new Animated.Value(1)).current;

  const handlePressIn = useCallback(() => {
    if (inactive) return;
    Animated.spring(scaleAnim, {
      toValue: animation.pressScale,
      useNativeDriver: true,
      speed: 50,
      bounciness: 4,
    }).start();
  }, [inactive, scaleAnim]);

  const handlePressOut = useCallback(() => {
    Animated.spring(scaleAnim, {
      toValue: 1,
      useNativeDriver: true,
      speed: 50,
      bounciness: 4,
    }).start();
  }, [scaleAnim]);

  return (
    <Animated.View style={{ transform: [{ scale: scaleAnim }] }}>
      <Pressable
        accessibilityRole="button"
        accessibilityState={{ disabled: !!inactive, busy: !!busy }}
        accessibilityLabel={label}
        onPress={onPress}
        onPressIn={handlePressIn}
        onPressOut={handlePressOut}
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
          <View style={styles.buttonContent}>
            {icon}
            <Text
              style={[
                styles.buttonLabel,
                variant === "secondary" && styles.buttonLabelSecondary,
              ]}
            >
              {label}
            </Text>
          </View>
        )}
      </Pressable>
    </Animated.View>
  );
}

/* =========================================================================
 * TEXT FIELD
 * ======================================================================= */

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

/* =========================================================================
 * OTP INPUT — 6-digit PIN input with individual boxes & underlines
 * ======================================================================= */

export function OtpInput({
  value,
  onChangeText,
  length = 6,
  error,
  onSubmit,
}: {
  value: string;
  onChangeText: (text: string) => void;
  length?: number;
  error?: string | undefined;
  onSubmit?: () => void;
}) {
  const inputRef = useRef<TextInput>(null);
  const [focused, setFocused] = useState(false);

  const digits = Array.from({ length }, (_, i) => value[i] || "");

  const handlePress = () => {
    inputRef.current?.focus();
  };

  const handleChangeText = (text: string) => {
    const cleaned = text.replace(/\D/g, "").slice(0, length);
    onChangeText(cleaned);
    if (cleaned.length === length && onSubmit) {
      onSubmit();
    }
  };

  return (
    <View style={styles.otpGroup}>
      <Pressable
        onPress={handlePress}
        style={styles.otpRow}
        accessibilityLabel="OTP Code Input"
        accessibilityRole="keyboardkey"
      >
        {digits.map((digit, idx) => {
          const isCurrent = focused && (value.length === idx || (value.length === length && idx === length - 1));
          const isFilled = digit.length > 0;
          return (
            <View
              key={idx}
              style={[
                styles.otpBox,
                isFilled && styles.otpBoxFilled,
                isCurrent && styles.otpBoxFocused,
                !!error && styles.otpBoxError,
              ]}
            >
              <Text
                style={[
                  styles.otpDigit,
                  isFilled && styles.otpDigitFilled,
                  isCurrent && styles.otpDigitFocused,
                ]}
              >
                {digit}
              </Text>
            </View>
          );
        })}
      </Pressable>

      <TextInput
        ref={inputRef}
        value={value}
        onChangeText={handleChangeText}
        keyboardType="number-pad"
        autoComplete="sms-otp"
        textContentType="oneTimeCode"
        maxLength={length}
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
        style={styles.otpHiddenInput}
        caretHidden
      />

      {error ? (
        <Text style={styles.fieldError} accessibilityLiveRegion="polite">
          {t(error)}
        </Text>
      ) : null}
    </View>
  );
}


/* =========================================================================
 * SEARCH INPUT — search field with icon and clear button
 * ======================================================================= */

export function SearchInput({
  value,
  onChangeText,
  placeholder,
  ...props
}: TextInputProps & { value: string; onChangeText: (text: string) => void; placeholder?: string }) {
  return (
    <View style={styles.searchInputContainer}>
      <Ionicons name="search-outline" size={18} color={colors.textMuted} style={styles.searchIcon} />
      <TextInput
        style={styles.searchInputField}
        placeholder={placeholder}
        placeholderTextColor={colors.textMuted}
        value={value}
        onChangeText={onChangeText}
        autoCapitalize="none"
        autoCorrect={false}
        returnKeyType="search"
        {...props}
      />
      {value.length > 0 ? (
        <Pressable onPress={() => onChangeText("")} hitSlop={8} style={styles.searchClearBtn}>
          <Ionicons name="close-circle" size={18} color={colors.textMuted} />
        </Pressable>
      ) : null}
    </View>
  );
}

/* =========================================================================
 * CARD — with press animation
 * ======================================================================= */

export function Card({
  children,
  onPress,
  selected,
  accessibilityLabel,
  style,
}: {
  children: React.ReactNode;
  onPress?: () => void;
  selected?: boolean;
  accessibilityLabel?: string;
  style?: ViewStyle;
}) {
  const scaleAnim = useRef(new Animated.Value(1)).current;

  const handlePressIn = useCallback(() => {
    Animated.spring(scaleAnim, {
      toValue: animation.cardPressScale,
      useNativeDriver: true,
      speed: 50,
      bounciness: 4,
    }).start();
  }, [scaleAnim]);

  const handlePressOut = useCallback(() => {
    Animated.spring(scaleAnim, {
      toValue: 1,
      useNativeDriver: true,
      speed: 50,
      bounciness: 4,
    }).start();
  }, [scaleAnim]);

  const content = <View style={styles.cardInner}>{children}</View>;

  if (!onPress) {
    return <View style={[styles.card, selected && styles.cardSelected, style]}>{content}</View>;
  }

  return (
    <Animated.View style={{ transform: [{ scale: scaleAnim }] }}>
      <Pressable
        accessibilityRole="button"
        {...(accessibilityLabel ? { accessibilityLabel } : {})}
        accessibilityState={{ selected: !!selected }}
        onPress={onPress}
        onPressIn={handlePressIn}
        onPressOut={handlePressOut}
        style={({ pressed }) => [
          styles.card,
          selected && styles.cardSelected,
          pressed && styles.cardPressed,
          style,
        ]}
      >
        {content}
      </Pressable>
    </Animated.View>
  );
}

/* =========================================================================
 * ERROR BANNER — with icon and improved layout
 * ======================================================================= */

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
  const displayText = messageKey && messageKey.startsWith("error.") ? t(messageKey) : (messageKey || t("error.UNKNOWN"));
  return (
    <View style={styles.errorBanner} accessibilityLiveRegion="assertive">
      <View style={styles.errorContent}>
        <View style={styles.errorIconContainer}>
          <Ionicons name="alert-circle" size={20} color={colors.danger} />
        </View>
        <View style={styles.errorTextGroup}>
          <Text style={styles.errorText}>{displayText}</Text>
          {correlationId ? (
            <Text style={styles.errorRef} selectable>
              {t("error.reference", { correlationId })}
            </Text>
          ) : null}
        </View>
      </View>
      {onRetry ? (
        <Pressable
          onPress={onRetry}
          accessibilityRole="button"
          hitSlop={8}
          style={styles.errorRetryBtn}
        >
          <Ionicons name="refresh-outline" size={14} color={colors.danger} />
          <Text style={styles.errorRetry}>{t("common.retry")}</Text>
        </Pressable>
      ) : null}
    </View>
  );
}

/* =========================================================================
 * BADGE — status indicator
 * ======================================================================= */

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

/* =========================================================================
 * AVATAR
 * ======================================================================= */

export function Avatar({
  initials: text,
  photoUrl,
  token,
  size = 48,
}: {
  initials: string;
  photoUrl?: string | null;
  token?: string | null;
  size?: number;
}) {
  if (photoUrl) {
    const isDataUri = photoUrl.startsWith("data:");
    const baseUrl = Constants.expoConfig?.extra?.apiBaseUrl ?? "";
    const uri = isDataUri || photoUrl.startsWith("http") ? photoUrl : `${baseUrl}${photoUrl}`;
    return (
      <Image
        source={{
          uri,
          headers: token && !isDataUri ? { Authorization: `Bearer ${token}` } : undefined,
        }}
        style={[styles.avatar, { width: size, height: size, borderRadius: size / 2 }]}
        accessibilityElementsHidden
        importantForAccessibility="no"
      />
    );
  }
  return (
    <View
      style={[styles.avatar, { width: size, height: size, borderRadius: size / 2 }]}
      accessibilityElementsHidden
      importantForAccessibility="no"
    >
      <Text style={[styles.avatarText, size > 48 ? { fontSize: size * 0.35 } : null]}>
        {text}
      </Text>
    </View>
  );
}

/* =========================================================================
 * EMPTY STATE — with icon, message, description, and optional CTA
 * ======================================================================= */

export function EmptyState({
  messageKey,
  description,
  icon,
  actionLabel,
  onAction,
}: {
  messageKey: string;
  description?: string;
  icon?: keyof typeof Ionicons.glyphMap;
  actionLabel?: string;
  onAction?: () => void;
}) {
  return (
    <View style={styles.empty}>
      <View style={styles.emptyIconContainer}>
        <Ionicons
          name={icon ?? "document-text-outline"}
          size={36}
          color={colors.textMuted}
        />
      </View>
      <Text style={styles.emptyTitle}>{t(messageKey)}</Text>
      {description ? (
        <Text style={styles.emptyDescription}>{description}</Text>
      ) : null}
      {actionLabel && onAction ? (
        <Pressable onPress={onAction} style={styles.emptyAction}>
          <Text style={styles.emptyActionText}>{actionLabel}</Text>
        </Pressable>
      ) : null}
    </View>
  );
}

/* =========================================================================
 * LOADING — full-screen spinner (kept as legacy fallback)
 * ======================================================================= */

export function Loading() {
  return (
    <View style={styles.loading}>
      <ActivityIndicator size="large" color={colors.primary} />
    </View>
  );
}

/* =========================================================================
 * SKELETON CARD — animated pulsing skeleton loader
 * ======================================================================= */

export function SkeletonCard({ lines = 3 }: { lines?: number }) {
  const opacity = useRef(new Animated.Value(0.3)).current;

  useEffect(() => {
    const loop = Animated.loop(
      Animated.sequence([
        Animated.timing(opacity, { toValue: 0.7, duration: 800, useNativeDriver: true }),
        Animated.timing(opacity, { toValue: 0.3, duration: 800, useNativeDriver: true }),
      ]),
    );
    loop.start();
    return () => loop.stop();
  }, [opacity]);

  return (
    <View style={styles.skeletonCard}>
      {Array.from({ length: lines }).map((_, i) => (
        <Animated.View
          key={i}
          style={[
            styles.skeletonLine,
            { opacity },
            i === 0 && styles.skeletonLineTitle,
            i === lines - 1 && styles.skeletonLineShort,
          ]}
        />
      ))}
    </View>
  );
}

/* =========================================================================
 * SKELETON ROW — single-line skeleton for inline items
 * ======================================================================= */

export function SkeletonRow() {
  const opacity = useRef(new Animated.Value(0.3)).current;

  useEffect(() => {
    const loop = Animated.loop(
      Animated.sequence([
        Animated.timing(opacity, { toValue: 0.7, duration: 800, useNativeDriver: true }),
        Animated.timing(opacity, { toValue: 0.3, duration: 800, useNativeDriver: true }),
      ]),
    );
    loop.start();
    return () => loop.stop();
  }, [opacity]);

  return (
    <View style={styles.skeletonRowContainer}>
      <Animated.View style={[styles.skeletonCircle, { opacity }]} />
      <View style={styles.skeletonRowLines}>
        <Animated.View style={[styles.skeletonLine, styles.skeletonLineTitle, { opacity }]} />
        <Animated.View style={[styles.skeletonLine, styles.skeletonLineShort, { opacity }]} />
      </View>
    </View>
  );
}

/* =========================================================================
 * ROW — key-value display
 * ======================================================================= */

export function Row({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.row}>
      <Text style={styles.rowLabel}>{label}</Text>
      <Text style={styles.rowValue}>{value}</Text>
    </View>
  );
}

/* =========================================================================
 * BACK BUTTON — left-aligned (moved from right per user preference)
 * ======================================================================= */

export function BackButton({ onPress, label = "Back" }: { onPress: () => void; label?: string }) {
  return (
    <View style={styles.backButtonWrapper}>
      <Pressable onPress={onPress} hitSlop={8} style={styles.backButton}>
        <Ionicons name="chevron-back" size={18} color={colors.text} />
        <Text style={styles.backButtonText}>{label}</Text>
      </Pressable>
    </View>
  );
}

/* =========================================================================
 * ACTION BUTTON — small inline action (reschedule, cancel, view, download)
 * ======================================================================= */

export function ActionButton({
  label,
  onPress,
  variant = "default",
  icon,
  disabled,
  busy,
}: {
  label: string;
  onPress: () => void;
  variant?: "default" | "danger" | "primary";
  icon?: keyof typeof Ionicons.glyphMap;
  disabled?: boolean;
  busy?: boolean;
}) {
  const inactive = disabled || busy;
  return (
    <Pressable
      onPress={onPress}
      disabled={inactive}
      accessibilityRole="button"
      accessibilityLabel={label}
      style={({ pressed }) => [
        styles.actionButton,
        variant === "danger" && styles.actionButtonDanger,
        variant === "primary" && styles.actionButtonPrimary,
        inactive && styles.actionButtonDisabled,
        pressed && !inactive && { opacity: 0.8 },
      ]}
    >
      {busy ? (
        <ActivityIndicator
          size="small"
          color={variant === "primary" ? colors.surface : variant === "danger" ? colors.danger : colors.primary}
        />
      ) : (
        <>
          {icon ? (
            <Ionicons
              name={icon}
              size={14}
              color={
                variant === "primary"
                  ? colors.surface
                  : variant === "danger"
                    ? colors.danger
                    : colors.primary
              }
            />
          ) : null}
          <Text
            style={[
              styles.actionButtonText,
              variant === "danger" && styles.actionButtonTextDanger,
              variant === "primary" && styles.actionButtonTextPrimary,
            ]}
          >
            {label}
          </Text>
        </>
      )}
    </Pressable>
  );
}

/* =========================================================================
 * CONFIRMATION SHEET — bottom sheet for destructive actions
 * ======================================================================= */

export function ConfirmationSheet({
  visible,
  title,
  message,
  confirmLabel,
  cancelLabel = "Go back",
  onConfirm,
  onCancel,
  destructive = false,
  busy = false,
}: {
  visible: boolean;
  title: string;
  message?: string;
  confirmLabel: string;
  cancelLabel?: string;
  onConfirm: () => void;
  onCancel: () => void;
  destructive?: boolean;
  busy?: boolean;
}) {
  const slideAnim = useRef(new Animated.Value(300)).current;
  const backdropAnim = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    if (visible) {
      Animated.parallel([
        Animated.spring(slideAnim, {
          toValue: 0,
          useNativeDriver: true,
          damping: animation.spring.damping,
          stiffness: animation.spring.stiffness,
          mass: animation.spring.mass,
        }),
        Animated.timing(backdropAnim, {
          toValue: 1,
          duration: animation.normal,
          useNativeDriver: true,
        }),
      ]).start();
    } else {
      Animated.parallel([
        Animated.timing(slideAnim, {
          toValue: 300,
          duration: animation.fast,
          useNativeDriver: true,
        }),
        Animated.timing(backdropAnim, {
          toValue: 0,
          duration: animation.fast,
          useNativeDriver: true,
        }),
      ]).start();
    }
  }, [visible, slideAnim, backdropAnim]);

  if (!visible) return null;

  return (
    <Modal visible={visible} transparent animationType="none" onRequestClose={onCancel}>
      <Animated.View style={[styles.sheetBackdrop, { opacity: backdropAnim }]}>
        <Pressable style={styles.sheetBackdropPressable} onPress={onCancel} />
      </Animated.View>
      <Animated.View
        style={[styles.sheetContainer, { transform: [{ translateY: slideAnim }] }]}
      >
        <View style={styles.sheetHandle} />
        <Text style={styles.sheetTitle}>{title}</Text>
        {message ? <Text style={styles.sheetMessage}>{message}</Text> : null}
        <View style={styles.sheetActions}>
          <Pressable onPress={onCancel} style={styles.sheetSecondaryBtn}>
            <Text style={styles.sheetSecondaryBtnText}>{cancelLabel}</Text>
          </Pressable>
          <Pressable
            onPress={onConfirm}
            disabled={busy}
            style={[
              styles.sheetPrimaryBtn,
              destructive && styles.sheetDestructiveBtn,
            ]}
          >
            {busy ? (
              <ActivityIndicator color={colors.surface} size="small" />
            ) : (
              <Text style={styles.sheetPrimaryBtnText}>{confirmLabel}</Text>
            )}
          </Pressable>
        </View>
      </Animated.View>
    </Modal>
  );
}

/* =========================================================================
 * STYLES
 * ======================================================================= */

const styles = StyleSheet.create({
  /* Screen */
  screen: { flex: 1, backgroundColor: colors.surfaceAlt },
  scrollContent: { padding: spacing.lg, gap: spacing.md, flexGrow: 1 },

  /* Typography */
  title: { ...typography.display, color: colors.text },
  subtitle: { ...typography.body, color: colors.textMuted },
  heading: { ...typography.heading, color: colors.text, marginTop: spacing.xs },
  body: { ...typography.body, color: colors.text },
  caption: { ...typography.caption, color: colors.textMuted },

  /* Section Header */
  sectionHeader: {
    ...typography.caption,
    fontSize: 11,
    fontWeight: "700",
    color: colors.textMuted,
    textTransform: "uppercase",
    letterSpacing: 1.2,
    marginTop: spacing.md,
    marginBottom: spacing.xs,
  },

  /* Divider */
  dividerSingle: {
    height: 1,
    backgroundColor: colors.border,
    marginVertical: spacing.sm,
  },
  dividerRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.sm,
    marginVertical: spacing.sm,
  },
  dividerLine: {
    flex: 1,
    height: 1,
    backgroundColor: colors.border,
  },
  dividerLabel: {
    ...typography.caption,
    fontSize: 11,
    fontWeight: "600",
    color: colors.textMuted,
    textTransform: "uppercase",
    letterSpacing: 0.8,
  },

  /* Button */
  button: {
    minHeight: MIN_TOUCH_TARGET,
    borderRadius: radius.md,
    backgroundColor: colors.primary,
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: spacing.xl,
    ...shadows.md,
  },
  buttonSecondary: {
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.borderDark,
    ...shadows.sm,
  },
  buttonDanger: { backgroundColor: colors.danger },
  buttonDisabled: { opacity: 0.45 },
  buttonPressed: { opacity: 0.9 },
  buttonContent: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.sm,
  },
  buttonLabel: { ...typography.label, fontSize: 15, fontWeight: "700", color: colors.surface },
  buttonLabelSecondary: { color: colors.primary },

  /* TextField */
  fieldGroup: { gap: spacing.xs },
  fieldLabel: { ...typography.label, color: colors.text, fontSize: 13, fontWeight: "700" },
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

  /* SearchInput */
  searchInputContainer: {
    flexDirection: "row",
    alignItems: "center",
    minHeight: MIN_TOUCH_TARGET,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    backgroundColor: colors.surface,
    paddingHorizontal: spacing.md,
    ...shadows.sm,
  },
  searchIcon: { marginRight: spacing.sm },
  searchInputField: {
    flex: 1,
    ...typography.body,
    color: colors.text,
    paddingVertical: spacing.sm,
  },
  searchClearBtn: { padding: spacing.xs },

  /* Card */
  card: {
    borderRadius: radius.lg,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    ...shadows.md,
  },
  cardSelected: { borderColor: colors.primary, borderWidth: 2 },
  cardPressed: { opacity: 0.95 },
  cardInner: { padding: spacing.lg, gap: spacing.xs },

  /* Error Banner */
  errorBanner: {
    backgroundColor: colors.dangerSoft,
    borderRadius: radius.md,
    padding: spacing.md,
    gap: spacing.sm,
    borderWidth: 1,
    borderColor: "#FECACA",
  },
  errorContent: {
    flexDirection: "row",
    gap: spacing.sm,
    alignItems: "flex-start",
  },
  errorIconContainer: {
    marginTop: 2,
  },
  errorTextGroup: {
    flex: 1,
    gap: 2,
  },
  errorText: { ...typography.body, fontSize: 14, color: colors.danger, fontWeight: "500" },
  errorRef: { ...typography.caption, color: colors.textMuted, fontSize: 11 },
  errorRetryBtn: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    alignSelf: "flex-start",
    paddingVertical: spacing.xs,
    paddingHorizontal: spacing.sm,
    borderRadius: radius.sm,
    backgroundColor: "rgba(220, 38, 38, 0.08)",
    marginTop: spacing.xs,
  },
  errorRetry: { ...typography.label, color: colors.danger, fontSize: 13 },

  /* Badge */
  badge: {
    alignSelf: "flex-start",
    paddingHorizontal: spacing.sm,
    paddingVertical: 3,
    borderRadius: radius.pill,
    backgroundColor: colors.surfaceAlt,
  },
  badge_neutral: { backgroundColor: colors.surfaceAlt },
  badge_success: { backgroundColor: colors.successSoft },
  badge_warning: { backgroundColor: colors.warningSoft },
  badge_danger: { backgroundColor: colors.dangerSoft },
  badgeText: { ...typography.caption, fontSize: 12, fontWeight: "600" },
  badgeText_neutral: { color: colors.textMuted },
  badgeText_success: { color: colors.success },
  badgeText_warning: { color: colors.warning },
  badgeText_danger: { color: colors.danger },

  /* Avatar */
  avatar: {
    width: 48,
    height: 48,
    borderRadius: radius.pill,
    backgroundColor: colors.primarySoft,
    alignItems: "center",
    justifyContent: "center",
  },
  avatarText: { ...typography.label, color: colors.primaryDark },

  /* Empty State */
  empty: {
    paddingVertical: spacing.xxl,
    paddingHorizontal: spacing.xl,
    alignItems: "center",
    gap: spacing.sm,
  },
  emptyIconContainer: {
    width: 64,
    height: 64,
    borderRadius: 32,
    backgroundColor: colors.primarySoft,
    alignItems: "center",
    justifyContent: "center",
    marginBottom: spacing.sm,
  },
  emptyTitle: {
    ...typography.heading,
    fontSize: 16,
    color: colors.text,
    textAlign: "center",
  },
  emptyDescription: {
    ...typography.body,
    fontSize: 14,
    color: colors.textMuted,
    textAlign: "center",
    lineHeight: 20,
  },
  emptyAction: {
    marginTop: spacing.md,
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.xl,
    borderRadius: radius.md,
    backgroundColor: colors.primary,
  },
  emptyActionText: {
    ...typography.label,
    color: colors.surface,
    fontWeight: "700",
  },

  /* Loading */
  loading: { flex: 1, padding: spacing.xxl, alignItems: "center", justifyContent: "center" },

  /* Skeleton */
  skeletonCard: {
    borderRadius: radius.lg,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.lg,
    gap: spacing.sm,
    ...shadows.sm,
  },
  skeletonLine: {
    height: 14,
    borderRadius: radius.sm,
    backgroundColor: colors.primarySoft,
    width: "100%",
  },
  skeletonLineTitle: {
    width: "60%",
    height: 16,
  },
  skeletonLineShort: {
    width: "40%",
  },
  skeletonRowContainer: {
    flexDirection: "row",
    gap: spacing.md,
    alignItems: "center",
    paddingVertical: spacing.sm,
  },
  skeletonCircle: {
    width: 48,
    height: 48,
    borderRadius: 24,
    backgroundColor: colors.primarySoft,
  },
  skeletonRowLines: {
    flex: 1,
    gap: spacing.sm,
  },

  /* Row */
  row: {
    flexDirection: "row",
    justifyContent: "space-between",
    paddingVertical: spacing.sm,
    gap: spacing.md,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.border,
  },
  rowLabel: { ...typography.body, fontSize: 14, color: colors.textMuted },
  rowValue: { ...typography.body, fontSize: 14, color: colors.text, fontWeight: "500", flexShrink: 1, textAlign: "right" },

  /* Back Button — left-aligned */
  backButtonWrapper: {
    width: "100%",
    flexDirection: "row",
    justifyContent: "flex-start",
    marginBottom: spacing.sm,
  },
  backButton: {
    flexDirection: "row",
    alignItems: "center",
    paddingVertical: spacing.xs,
    paddingHorizontal: spacing.sm,
    borderRadius: radius.md,
    gap: 4,
    minHeight: MIN_TOUCH_TARGET,
  },
  backButtonText: {
    ...typography.label,
    color: colors.text,
  },

  /* Action Button */
  actionButton: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 6,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
    minHeight: 40,
    ...shadows.sm,
  },
  actionButtonDanger: {
    borderColor: "#FECACA",
    backgroundColor: "#FEF2F2",
  },
  actionButtonPrimary: {
    borderColor: colors.primary,
    backgroundColor: colors.primary,
  },
  actionButtonDisabled: { opacity: 0.5 },
  actionButtonText: {
    ...typography.label,
    fontSize: 13,
    fontWeight: "700",
    color: colors.primary,
  },
  actionButtonTextDanger: { color: colors.danger },
  actionButtonTextPrimary: { color: colors.surface },

  /* Confirmation Sheet */
  sheetBackdrop: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: "rgba(9, 9, 11, 0.5)",
  },
  sheetBackdropPressable: {
    flex: 1,
  },
  sheetContainer: {
    position: "absolute",
    bottom: 0,
    left: 0,
    right: 0,
    backgroundColor: colors.surface,
    borderTopLeftRadius: spacing.xl,
    borderTopRightRadius: spacing.xl,
    paddingHorizontal: spacing.xl,
    paddingBottom: spacing.xxl + 16,
    paddingTop: spacing.md,
    ...shadows.lg,
  },
  sheetHandle: {
    width: 36,
    height: 4,
    borderRadius: 2,
    backgroundColor: colors.border,
    alignSelf: "center",
    marginBottom: spacing.lg,
  },
  sheetTitle: {
    ...typography.heading,
    fontSize: 18,
    color: colors.text,
    marginBottom: spacing.sm,
  },
  sheetMessage: {
    ...typography.body,
    fontSize: 14,
    color: colors.textMuted,
    lineHeight: 20,
    marginBottom: spacing.lg,
  },
  sheetActions: {
    gap: spacing.sm,
    marginTop: spacing.sm,
  },
  sheetSecondaryBtn: {
    minHeight: MIN_TOUCH_TARGET,
    alignItems: "center",
    justifyContent: "center",
    borderRadius: radius.md,
    backgroundColor: colors.primarySoft,
  },
  sheetSecondaryBtnText: {
    ...typography.label,
    fontWeight: "700",
    color: colors.text,
  },
  sheetPrimaryBtn: {
    minHeight: MIN_TOUCH_TARGET,
    alignItems: "center",
    justifyContent: "center",
    borderRadius: radius.md,
    backgroundColor: colors.primary,
  },
  sheetDestructiveBtn: {
    backgroundColor: colors.danger,
  },
  sheetPrimaryBtnText: {
    ...typography.label,
    fontWeight: "700",
    color: colors.surface,
  },

  /* OTP Input */
  otpGroup: {
    marginVertical: spacing.md,
  },
  otpRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    gap: spacing.xs,
  },
  otpBox: {
    flex: 1,
    height: 56,
    borderRadius: radius.md,
    backgroundColor: colors.surface,
    borderWidth: 1.5,
    borderColor: colors.border,
    alignItems: "center",
    justifyContent: "center",
    ...shadows.sm,
  },
  otpBoxFilled: {
    borderColor: colors.text,
    backgroundColor: colors.surface,
  },
  otpBoxFocused: {
    borderColor: colors.primary,
    borderWidth: 2,
    backgroundColor: colors.surfaceAlt,
  },
  otpBoxError: {
    borderColor: colors.danger,
    backgroundColor: colors.surface,
  },
  otpDigit: {
    ...typography.heading,
    fontSize: 22,
    fontWeight: "700",
    color: colors.textMuted,
  },
  otpDigitFilled: {
    color: colors.text,
  },
  otpDigitFocused: {
    color: colors.primaryDark,
  },
  otpHiddenInput: {
    position: "absolute",
    width: 1,
    height: 1,
    opacity: 0,
  },
});
