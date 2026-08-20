import React, { useState, useEffect } from "react";
import {
  Alert,
  Image,
  Modal,
  Pressable,
  ScrollView,
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
  Button,
  Caption,
  ErrorBanner,
  Loading,
  Screen,
  TextField,
  Title,
} from "../../ui/components";
import { colors, radius, spacing, typography } from "../../ui/tokens";
import { initials } from "../../core/format";

const SALUTATIONS = [
  { value: "", label: "— None —" },
  { value: "Mr", label: "Mr" },
  { value: "Mrs", label: "Mrs" },
  { value: "Ms", label: "Ms" },
  { value: "Dr", label: "Dr" },
  { value: "Baby", label: "Baby" },
  { value: "Master", label: "Master" },
];

export default function EditProfileScreen() {
  const router = useRouter();
  const { api } = useContainer();
  const queryClient = useQueryClient();

  const profile = useQuery({
    queryKey: QueryKeys.profile,
    queryFn: () => api.getProfile(),
  });

  const [salutation, setSalutation] = useState<string>("");
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [gender, setGender] = useState<"MALE" | "FEMALE" | "OTHER">("OTHER");
  const [age, setAge] = useState("");
  const [mobile, setMobile] = useState("");
  const [email, setEmail] = useState("");
  const [bloodGroup, setBloodGroup] = useState("");
  const [address, setAddress] = useState("");
  const [avatarUri, setAvatarUri] = useState<string | null>(null);
  const [avatarBase64, setAvatarBase64] = useState<string | null>(null);
  const [showPhotoModal, setShowPhotoModal] = useState(false);

  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<PortalError | null>(null);

  useEffect(() => {
    if (profile.data) {
      const p = profile.data;
      
      let rawFirst = (p.firstName || "").trim();
      let rawLast = (p.lastName || "").trim();
      let sal = (p.salutation || "").trim();

      // If salutation is not directly in p, try to detect from full name
      if (!sal && p.fullName) {
        const match = p.fullName.match(/^(Mr|Mrs|Ms|Dr|Baby|Master)\b/i);
        if (match && match[0]) {
          sal = match[0];
        }
      }
      
      // Strip salutation prefixes from first and last names
      rawFirst = rawFirst.replace(/^(Mr|Mrs|Ms|Dr|Baby|Master)\.?\s+/i, "").trim();
      rawLast = rawLast.replace(/^(Mr|Mrs|Ms|Dr|Baby|Master)\.?\s+/i, "").trim();

      if ((!rawFirst && !rawLast) || rawFirst.toLowerCase() === "ms" || rawFirst.toLowerCase() === "mr" || rawFirst.toLowerCase() === "mrs") {
        const withoutSalutation = (p.fullName || "")
          .replace(/^(Mr|Mrs|Ms|Dr|Baby|Master)\.?\s+/i, "")
          .replace(/^(Mr|Mrs|Ms|Dr|Baby|Master)\.?\s+/i, "")
          .trim();
        const parts = withoutSalutation.split(/\s+/);
        rawFirst = parts[0] || "";
        rawLast = parts.slice(1).join(" ") || "";
      }

      setSalutation(sal);
      setFirstName(rawFirst);
      setLastName(rawLast);
      setGender((p.gender as any) || "OTHER");
      setBloodGroup(p.bloodGroup || "");
      setAge(p.age !== null && p.age !== undefined ? String(p.age) : "");
      setMobile(p.mobile || "");
      setEmail(p.email || "");
      setAddress(p.address || "");
      if (p.photoUrl) {
        setAvatarUri(p.photoUrl);
      }
    }
  }, [profile.data]);

  const handleSalutationSelect = (val: string) => {
    setSalutation(val);
    if (val === "Mr" || val === "Master") {
      setGender("MALE");
    } else if (val === "Ms" || val === "Mrs") {
      setGender("FEMALE");
    }
  };

  const handleTakePhoto = async () => {
    setShowPhotoModal(false);
    try {
      const { status } = await ImagePicker.requestCameraPermissionsAsync();
      if (status !== "granted") {
        Alert.alert("Permission Required", "Please allow camera access to capture a profile photo.");
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
        if (asset) {
          setAvatarUri(asset.uri);
          if (asset.base64) {
            const mime = asset.mimeType || "image/jpeg";
            setAvatarBase64(`data:${mime};base64,${asset.base64}`);
          }
        }
      }
    } catch (err) {
      console.error("Failed to take photo:", err);
    }
  };

  const handlePickFromGallery = async () => {
    setShowPhotoModal(false);
    try {
      const { status } = await ImagePicker.requestMediaLibraryPermissionsAsync();
      if (status !== "granted") {
        Alert.alert("Permission Required", "Please allow photo access to choose a profile picture.");
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
        if (asset) {
          setAvatarUri(asset.uri);
          if (asset.base64) {
            const mime = asset.mimeType || "image/jpeg";
            setAvatarBase64(`data:${mime};base64,${asset.base64}`);
          }
        }
      }
    } catch (err) {
      console.error("Failed to pick image:", err);
    }
  };

  async function onSave() {
    setBusy(true);
    setError(null);
    try {
      const parsedAge = age ? parseInt(age, 10) : null;
      await api.updateProfile({
        salutation: salutation || null,
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        gender,
        age: parsedAge,
        mobile: mobile.trim(),
        email: email.trim() || null,
        bloodGroup: bloodGroup.trim() || null,
        address: address.trim() || null,
        avatarBase64: avatarBase64 || null,
      });
      await queryClient.invalidateQueries({ queryKey: QueryKeys.profile });
      Alert.alert("Success", "Profile updated successfully.", [
        { text: "OK", onPress: () => router.back() },
      ]);
    } catch (err) {
      setError(err as PortalError);
    } finally {
      setBusy(false);
    }
  }

  if (profile.isLoading) return <Loading />;

  const userInitials = initials(`${firstName} ${lastName}`.trim() || "User");

  return (
    <Screen>
      <BackButton onPress={() => router.back()} />
      <Title>Edit Profile</Title>

      {/* Avatar Section */}
      <View style={styles.avatarSection}>
        <Pressable onPress={() => setShowPhotoModal(true)} style={styles.avatarContainer}>
          {avatarUri ? (
            <Image source={{ uri: avatarUri }} style={styles.avatarImage} />
          ) : (
            <View style={styles.avatarPlaceholder}>
              <Text style={styles.avatarInitialText}>{userInitials}</Text>
            </View>
          )}
          <View style={styles.cameraIconBadge}>
            <Ionicons name="camera" size={14} color="#ffffff" />
          </View>
        </Pressable>
        <Pressable onPress={() => setShowPhotoModal(true)} style={styles.changePhotoBtn}>
          <Text style={styles.changePhotoText}>Change Profile Photo</Text>
        </Pressable>
      </View>

      {/* Salutation Picker */}
      <View style={styles.salutationContainer}>
        <Caption>Salutation</Caption>
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={styles.salutationRow}
        >
          {SALUTATIONS.map((item) => {
            const isSelected = salutation === item.value;
            return (
              <Pressable
                key={item.value || "none"}
                onPress={() => handleSalutationSelect(item.value)}
                style={[
                  styles.salutationOption,
                  isSelected && styles.salutationOptionSelected,
                ]}
              >
                <Text
                  style={[
                    styles.salutationText,
                    isSelected && styles.salutationTextSelected,
                  ]}
                >
                  {item.label}
                </Text>
              </Pressable>
            );
          })}
        </ScrollView>
      </View>

      <TextField
        label="First Name"
        value={firstName}
        onChangeText={setFirstName}
      />

      <TextField
        label="Last Name"
        value={lastName}
        onChangeText={setLastName}
      />

      <TextField
        label="Age (in years)"
        value={age}
        onChangeText={setAge}
        keyboardType="number-pad"
        maxLength={3}
      />

      <TextField
        label="Mobile Number"
        value={mobile}
        onChangeText={setMobile}
        keyboardType="phone-pad"
        maxLength={10}
      />

      {/* Gender Picker with Icons */}
      <View style={styles.genderContainer}>
        <Caption>Gender</Caption>
        <View style={styles.genderRow}>
          {(
            [
              { key: "MALE", label: "Male", icon: "male" },
              { key: "FEMALE", label: "Female", icon: "female" },
              { key: "OTHER", label: "Other", icon: "person" },
            ] as const
          ).map((item) => {
            const isSelected = gender === item.key;
            return (
              <Pressable
                key={item.key}
                onPress={() => setGender(item.key)}
                style={[
                  styles.genderOption,
                  isSelected && styles.genderOptionSelected,
                ]}
              >
                <Ionicons
                  name={item.icon as any}
                  size={16}
                  color={isSelected ? colors.primaryDark : colors.textMuted}
                />
                <Text
                  style={[
                    styles.genderText,
                    isSelected && styles.genderTextSelected,
                  ]}
                >
                  {item.label}
                </Text>
              </Pressable>
            );
          })}
        </View>
      </View>

      <TextField
        label="Blood Group"
        value={bloodGroup}
        onChangeText={setBloodGroup}
        placeholder="e.g. O+, A+, B+"
      />

      <TextField
        label="Email"
        value={email}
        onChangeText={setEmail}
        keyboardType="email-address"
        autoCapitalize="none"
      />

      <TextField
        label="Address"
        value={address}
        onChangeText={setAddress}
        multiline
      />

      {error ? (
        <ErrorBanner
          messageKey={error.message || "An error occurred"}
          correlationId={error.correlationId}
        />
      ) : null}

      <Button label="Save Changes" onPress={onSave} busy={busy} />

      {/* Photo Selection Modal with Vector Icons */}
      <Modal
        visible={showPhotoModal}
        transparent
        animationType="fade"
        onRequestClose={() => setShowPhotoModal(false)}
      >
        <Pressable
          style={styles.modalOverlay}
          onPress={() => setShowPhotoModal(false)}
        >
          <View
            style={styles.modalSheet}
            onStartShouldSetResponder={() => true}
          >
            <View style={styles.modalHeader}>
              <Text style={styles.modalTitle}>Change Profile Photo</Text>
              <Text style={styles.modalSubtitle}>
                Select an option to update your photo
              </Text>
            </View>

            <View style={styles.modalOptionsContainer}>
              <Pressable
                onPress={handleTakePhoto}
                style={styles.modalOptionButton}
              >
                <View style={styles.modalIconWrapper}>
                  <Ionicons name="camera" size={22} color={colors.primary} />
                </View>
                <View style={{ flex: 1 }}>
                  <Text style={styles.modalOptionText}>Take Photo</Text>
                  <Text style={styles.modalOptionSubtext}>
                    Use camera to capture a new photo
                  </Text>
                </View>
                <Ionicons name="chevron-forward" size={18} color={colors.textMuted} />
              </Pressable>

              <Pressable
                onPress={handlePickFromGallery}
                style={styles.modalOptionButton}
              >
                <View style={styles.modalIconWrapper}>
                  <Ionicons name="images" size={22} color={colors.primary} />
                </View>
                <View style={{ flex: 1 }}>
                  <Text style={styles.modalOptionText}>Choose from Gallery</Text>
                  <Text style={styles.modalOptionSubtext}>
                    Pick an existing photo from device
                  </Text>
                </View>
                <Ionicons name="chevron-forward" size={18} color={colors.textMuted} />
              </Pressable>
            </View>

            <Pressable
              onPress={() => setShowPhotoModal(false)}
              style={styles.modalCancelBtn}
            >
              <Text style={styles.modalCancelText}>Cancel</Text>
            </Pressable>
          </View>
        </Pressable>
      </Modal>
    </Screen>
  );
}

const styles = StyleSheet.create({
  avatarSection: {
    alignItems: "center",
    marginVertical: spacing.md,
    gap: spacing.xs,
  },
  avatarContainer: {
    position: "relative",
    width: 90,
    height: 90,
  },
  avatarImage: {
    width: 90,
    height: 90,
    borderRadius: 45,
    borderWidth: 2,
    borderColor: colors.primary,
  },
  avatarPlaceholder: {
    width: 90,
    height: 90,
    borderRadius: 45,
    backgroundColor: colors.primarySoft,
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 2,
    borderColor: colors.border,
  },
  avatarInitialText: {
    ...typography.heading,
    fontSize: 28,
    color: colors.primaryDark,
    fontWeight: "700",
  },
  cameraIconBadge: {
    position: "absolute",
    right: 0,
    bottom: 0,
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: colors.primary,
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 2,
    borderColor: colors.surface,
  },
  changePhotoBtn: {
    paddingVertical: 4,
    paddingHorizontal: 8,
  },
  changePhotoText: {
    ...typography.label,
    fontSize: 12,
    color: colors.primary,
    fontWeight: "600",
  },
  salutationContainer: {
    gap: spacing.xs,
  },
  salutationRow: {
    flexDirection: "row",
    gap: spacing.xs,
    paddingVertical: 2,
  },
  salutationOption: {
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: radius.pill,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
    alignItems: "center",
    justifyContent: "center",
  },
  salutationOptionSelected: {
    borderColor: colors.primary,
    backgroundColor: colors.primarySoft,
  },
  salutationText: {
    ...typography.body,
    fontSize: 13,
    color: colors.textMuted,
    fontWeight: "600",
  },
  salutationTextSelected: {
    color: colors.primaryDark,
    fontWeight: "700",
  },
  genderContainer: {
    gap: spacing.xs,
  },
  genderRow: {
    flexDirection: "row",
    gap: spacing.sm,
  },
  genderOption: {
    flex: 1,
    flexDirection: "row",
    gap: 6,
    paddingVertical: 10,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
    alignItems: "center",
    justifyContent: "center",
  },
  genderOptionSelected: {
    borderColor: colors.primary,
    backgroundColor: colors.primarySoft,
  },
  genderText: {
    ...typography.body,
    fontSize: 13,
    color: colors.textMuted,
    fontWeight: "600",
  },
  genderTextSelected: {
    color: colors.primaryDark,
    fontWeight: "700",
  },

  // Modal styles
  modalOverlay: {
    flex: 1,
    backgroundColor: "rgba(0,0,0,0.5)",
    justifyContent: "flex-end",
  },
  modalSheet: {
    backgroundColor: colors.surface,
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    padding: spacing.lg,
    paddingBottom: spacing.xxl,
    gap: spacing.md,
  },
  modalHeader: {
    alignItems: "center",
    gap: 4,
    paddingBottom: spacing.xs,
  },
  modalTitle: {
    ...typography.heading,
    fontSize: 17,
    fontWeight: "700",
    color: colors.text,
  },
  modalSubtitle: {
    ...typography.caption,
    fontSize: 12,
    color: colors.textMuted,
  },
  modalOptionsContainer: {
    gap: spacing.sm,
    marginVertical: spacing.xs,
  },
  modalOptionButton: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.md,
    padding: spacing.md,
    backgroundColor: colors.surfaceAlt,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.border,
  },
  modalIconWrapper: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: colors.primarySoft,
    alignItems: "center",
    justifyContent: "center",
  },
  modalOptionText: {
    ...typography.body,
    fontWeight: "700",
    fontSize: 14,
    color: colors.text,
  },
  modalOptionSubtext: {
    ...typography.caption,
    fontSize: 11,
    color: colors.textMuted,
    marginTop: 1,
  },
  modalCancelBtn: {
    marginTop: spacing.xs,
    paddingVertical: 12,
    borderRadius: radius.md,
    backgroundColor: colors.surfaceAlt,
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 1,
    borderColor: colors.border,
  },
  modalCancelText: {
    ...typography.label,
    color: colors.textMuted,
    fontWeight: "700",
    fontSize: 14,
  },
});
