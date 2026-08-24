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
  ErrorBanner,
  Heading,
  Loading,
  Screen,
  Title,
} from "../../ui/components";
import { colors, radius, spacing, typography } from "../../ui/tokens";
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
      <View style={styles.card}>
        <View style={styles.avatarContainer}>
          {avatarUri ? (
            <Image source={{ uri: avatarUri }} style={styles.avatarImage} />
          ) : (
            <View style={styles.avatarPlaceholder}>
              <Text style={styles.avatarInitialText}>{userInitials}</Text>
            </View>
          )}
          {busy && (
            <View style={styles.loadingOverlay}>
              <ActivityIndicator size="large" color="#ffffff" />
            </View>
          )}
        </View>

        <Text style={styles.userName}>
          {formatPatientName(me?.fullName ?? "Patient")}
        </Text>
        {me?.numberSequenceSuffix ? (
          <Text style={styles.userMeta}>Patient ID: {me.numberSequenceSuffix}</Text>
        ) : null}
      </View>

      {error ? (
        <ErrorBanner
          messageKey={error.message || "Failed to update profile photo"}
          correlationId={error.correlationId}
        />
      ) : null}

      {/* Action Buttons */}
      <View style={styles.actionsContainer}>
        <Pressable
          onPress={handleTakePhoto}
          disabled={busy}
          style={[styles.actionBtn, busy && styles.actionBtnDisabled]}
        >
          <View style={styles.actionIconWrapper}>
            <Ionicons name="camera" size={24} color={colors.primary} />
          </View>
          <View style={styles.actionTextGroup}>
            <Text style={styles.actionTitle}>Take Photo</Text>
            <Text style={styles.actionSubtext}>
              Use camera to capture a new profile picture
            </Text>
          </View>
          <Ionicons name="chevron-forward" size={18} color={colors.textMuted} />
        </Pressable>

        <Pressable
          onPress={handlePickFromGallery}
          disabled={busy}
          style={[styles.actionBtn, busy && styles.actionBtnDisabled]}
        >
          <View style={styles.actionIconWrapper}>
            <Ionicons name="images" size={24} color={colors.primary} />
          </View>
          <View style={styles.actionTextGroup}>
            <Text style={styles.actionTitle}>Choose from Gallery</Text>
            <Text style={styles.actionSubtext}>
              Select an existing image from your photo library
            </Text>
          </View>
          <Ionicons name="chevron-forward" size={18} color={colors.textMuted} />
        </Pressable>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    padding: spacing.lg,
    alignItems: "center",
    marginVertical: spacing.md,
    borderWidth: 1,
    borderColor: colors.border,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.05,
    shadowRadius: 6,
    elevation: 2,
  },
  avatarContainer: {
    position: "relative",
    width: 110,
    height: 110,
    borderRadius: 55,
    overflow: "hidden",
    marginBottom: spacing.md,
  },
  avatarImage: {
    width: 110,
    height: 110,
    borderRadius: 55,
    borderWidth: 3,
    borderColor: colors.primary,
  },
  avatarPlaceholder: {
    width: 110,
    height: 110,
    borderRadius: 55,
    backgroundColor: colors.primarySoft,
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 3,
    borderColor: colors.primary,
  },
  avatarInitialText: {
    ...typography.heading,
    fontSize: 36,
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
    fontSize: 18,
    fontWeight: "700",
    color: colors.text,
    textAlign: "center",
  },
  userMeta: {
    fontSize: 13,
    color: colors.textMuted,
    marginTop: 2,
  },
  actionsContainer: {
    gap: spacing.md,
    marginTop: spacing.xs,
  },
  actionBtn: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.md,
    padding: spacing.md,
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.04,
    shadowRadius: 4,
    elevation: 1,
  },
  actionBtnDisabled: {
    opacity: 0.6,
  },
  actionIconWrapper: {
    width: 48,
    height: 48,
    borderRadius: 24,
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
    fontWeight: "700",
    color: colors.text,
  },
  actionSubtext: {
    ...typography.caption,
    fontSize: 12,
    color: colors.textMuted,
    marginTop: 2,
  },
});
