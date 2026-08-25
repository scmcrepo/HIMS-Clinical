import React, { useState } from "react";
import {
  ActivityIndicator,
  Alert,
  Image,
  Pressable,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { useRouter } from "expo-router";
import * as ImagePicker from "expo-image-picker";
import { Ionicons } from "@expo/vector-icons";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useContainer } from "../_layout";
import { QueryKeys } from "../../core/cachePolicy";
import { PortalError } from "../../core/errors";
import {
  BackButton,
  Caption,
  Divider,
  ErrorBanner,
  Heading,
  Loading,
  Screen,
  SkeletonCard,
  Title,
} from "../../ui/components";
import { colors, radius, shadows, spacing, typography } from "../../ui/tokens";
import { formatPatientName, initials } from "../../core/format";

export default function EditProfileScreen() {
  const router = useRouter();
  const { api } = useContainer();
  const queryClient = useQueryClient();

  const profile = useQuery({
    queryKey: QueryKeys.profile,
    queryFn: () => api.getProfile(),
  });

  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<PortalError | null>(null);

  const me = profile.data;

  const uploadPhoto = async (base64Data: string) => {
    if (!me) return;
    setBusy(true);
    setError(null);
    try {
      await api.updateProfile({
        salutation: me.salutation || null,
        firstName: me.firstName || "",
        lastName: me.lastName || "",
        gender: (me.gender as any) || "OTHER",
        age: me.age ?? null,
        mobile: me.mobile || "",
        email: me.email || null,
        bloodGroup: me.bloodGroup || null,
        address: me.address || null,
        avatarBase64: base64Data,
      });
      await queryClient.invalidateQueries({ queryKey: QueryKeys.profile });
      Alert.alert("Success", "Profile photo updated successfully.");
    } catch (err) {
      setError(err as PortalError);
    } finally {
      setBusy(false);
    }
  };

  const handleTakePhoto = async () => {
    try {
      const { status } = await ImagePicker.requestCameraPermissionsAsync();
      if (status !== "granted") {
        Alert.alert(
          "Permission Required",
          "Please allow camera access to capture a profile photo."
        );
        return;
      }

      const result = await ImagePicker.launchCameraAsync({
        allowsEditing: true,
        aspect: [1, 1],
        quality: 0.7,
        base64: true,
      });

      if (!result.canceled && result.assets && result.assets.length > 0) {
        const asset = result.assets[0];
        if (asset && asset.base64) {
          const mime = asset.mimeType || "image/jpeg";
          const b64Uri = `data:${mime};base64,${asset.base64}`;
          await uploadPhoto(b64Uri);
        }
      }
    } catch (err) {
      console.error("Failed to take photo:", err);
    }
  };

  const handlePickFromGallery = async () => {
    try {
      const { status } = await ImagePicker.requestMediaLibraryPermissionsAsync();
      if (status !== "granted") {
        Alert.alert(
          "Permission Required",
          "Please allow photo access to choose a profile picture."
        );
        return;
      }

      const result = await ImagePicker.launchImageLibraryAsync({
        mediaTypes: ["images"],
        allowsEditing: true,
        aspect: [1, 1],
        quality: 0.7,
        base64: true,
      });

      if (!result.canceled && result.assets && result.assets.length > 0) {
        const asset = result.assets[0];
        if (asset && asset.base64) {
          const mime = asset.mimeType || "image/jpeg";
          const b64Uri = `data:${mime};base64,${asset.base64}`;
          await uploadPhoto(b64Uri);
        }
      }
    } catch (err) {
      console.error("Failed to pick image:", err);
    }
  };

  if (profile.isLoading) return <Loading />;

  const userInitials = initials(me?.fullName || "User");
  const avatarUri = me?.photoUrl;

  return (
    <Screen>
      <BackButton onPress={() => router.back()} />
      <Title>Change Profile Photo</Title>

      {/* Centered Avatar Card */}
      {profile.isLoading ? (
        <SkeletonCard lines={3} />
      ) : (
        <View style={s.card}>
          <View style={s.avatarContainer}>
            {avatarUri ? (
              <Image source={{ uri: avatarUri }} style={s.avatarImage} />
            ) : (
              <View style={s.avatarPlaceholder}>
                <Text style={s.avatarInitialText}>{userInitials}</Text>
              </View>
            )}
            {busy && (
              <View style={s.loadingOverlay}>
                <ActivityIndicator size="large" color="#ffffff" />
              </View>
            )}
          </View>

          <Text style={s.userName}>
            {formatPatientName(me?.fullName ?? "Patient")}
          </Text>
          {me?.numberSequenceSuffix ? (
            <Text style={s.userMeta}>Patient ID: {me.numberSequenceSuffix}</Text>
          ) : null}
        </View>
      )}

      {error ? (
        <ErrorBanner
          messageKey={error.message || "Failed to update profile photo"}
          correlationId={error.correlationId}
        />
      ) : null}

      {/* Action Buttons */}
      <View style={s.actionsContainer}>
        <Pressable
          onPress={handleTakePhoto}
          disabled={busy}
          style={({ pressed }) => [
            s.actionBtn,
            busy && s.actionBtnDisabled,
            pressed && !busy && { backgroundColor: colors.surfaceAlt },
          ]}
        >
          <View style={s.actionIconWrapper}>
            <Ionicons name="camera" size={22} color={colors.primary} />
          </View>
          <View style={s.actionTextGroup}>
            <Text style={s.actionTitle}>Take Photo</Text>
            <Text style={s.actionSubtext}>
              Use camera to capture a new profile picture
            </Text>
          </View>
          <Ionicons name="chevron-forward" size={16} color={colors.textMuted} />
        </Pressable>

        <Pressable
          onPress={handlePickFromGallery}
          disabled={busy}
          style={({ pressed }) => [
            s.actionBtn,
            busy && s.actionBtnDisabled,
            pressed && !busy && { backgroundColor: colors.surfaceAlt },
          ]}
        >
          <View style={s.actionIconWrapper}>
            <Ionicons name="images" size={22} color={colors.primary} />
          </View>
          <View style={s.actionTextGroup}>
            <Text style={s.actionTitle}>Choose from Gallery</Text>
            <Text style={s.actionSubtext}>
              Select an existing image from your photo library
            </Text>
          </View>
          <Ionicons name="chevron-forward" size={16} color={colors.textMuted} />
        </Pressable>
      </View>
    </Screen>
  );
}

const s = StyleSheet.create({
  card: {
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    padding: spacing.xl,
    alignItems: "center",
    marginVertical: spacing.sm,
    borderWidth: 1,
    borderColor: colors.border,
    ...shadows.md,
  },
  avatarContainer: {
    position: "relative",
    width: 100,
    height: 100,
    borderRadius: 50,
    overflow: "hidden",
    marginBottom: spacing.md,
  },
  avatarImage: {
    width: 100,
    height: 100,
    borderRadius: 50,
    borderWidth: 2,
    borderColor: colors.border,
  },
  avatarPlaceholder: {
    width: 100,
    height: 100,
    borderRadius: 50,
    backgroundColor: colors.primarySoft,
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 2,
    borderColor: colors.border,
  },
  avatarInitialText: {
    ...typography.heading,
    fontSize: 32,
    color: colors.primaryDark,
    fontWeight: "700",
  },
  loadingOverlay: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: "rgba(0,0,0,0.4)",
    alignItems: "center",
    justifyContent: "center",
  },
  userName: {
    ...typography.heading,
    fontSize: 18,
    color: colors.text,
    textAlign: "center",
  },
  userMeta: {
    ...typography.caption,
    fontSize: 13,
    color: colors.textMuted,
    marginTop: 2,
  },
  actionsContainer: {
    gap: spacing.sm,
  },
  actionBtn: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.md,
    paddingVertical: spacing.md,
    paddingHorizontal: spacing.lg,
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    minHeight: 64,
    ...shadows.sm,
  },
  actionBtnDisabled: {
    opacity: 0.6,
  },
  actionIconWrapper: {
    width: 44,
    height: 44,
    borderRadius: 12,
    backgroundColor: colors.primarySoft,
    alignItems: "center",
    justifyContent: "center",
  },
  actionTextGroup: {
    flex: 1,
  },
  actionTitle: {
    ...typography.label,
    fontSize: 15,
    fontWeight: "600",
    color: colors.text,
  },
  actionSubtext: {
    ...typography.caption,
    fontSize: 12,
    color: colors.textMuted,
    marginTop: 2,
  },
});
