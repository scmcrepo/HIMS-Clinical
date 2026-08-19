import React, { useState, useEffect } from "react";
import { useRouter } from "expo-router";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useContainer } from "../_layout";
import { QueryKeys } from "../../core/cachePolicy";
import { PortalError } from "../../core/errors";
import {
  Button,
  ErrorBanner,
  Screen,
  TextField,
  Title,
  Loading,
  Caption,
} from "../../ui/components";
import { formatAge } from "../../core/format";

function isoToDisplay(iso: string) {
  if (!iso) return "";
  const parts = iso.split("-");
  if (parts.length === 3) return `${parts[2]}/${parts[1]}/${parts[0]}`;
  return iso;
}

function displayToIso(display: string) {
  if (!display) return "";
  const parts = display.split("/");
  if (parts.length === 3) return `${parts[2]}-${parts[1]}-${parts[0]}`;
  return display;
}

export default function EditProfileScreen() {
  const router = useRouter();
  const { api } = useContainer();
  const queryClient = useQueryClient();

  const profile = useQuery({
    queryKey: QueryKeys.profile,
    queryFn: () => api.getProfile(),
  });

  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [gender, setGender] = useState<"MALE" | "FEMALE" | "OTHER">("OTHER");
  const [dateOfBirth, setDateOfBirth] = useState("");
  const [mobile, setMobile] = useState("");
  const [email, setEmail] = useState("");
  const [bloodGroup, setBloodGroup] = useState("");
  const [address, setAddress] = useState("");
  
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<PortalError | null>(null);

  useEffect(() => {
    if (profile.data) {
      const parts = profile.data.fullName.split(" ");
      setFirstName(parts[0] || "");
      setLastName(parts.slice(1).join(" ") || "");
      setGender(profile.data.gender as any || "OTHER");
      setBloodGroup(profile.data.bloodGroup || "");
      setDateOfBirth(isoToDisplay(profile.data.dateOfBirth || ""));
      setMobile(profile.data.mobile || "");
      setEmail(profile.data.email || "");
      setAddress(profile.data.address || "");
    }
  }, [profile.data]);

  async function onSave() {
    setBusy(true);
    setError(null);
    try {
      await api.updateProfile({
        firstName,
        lastName,
        gender,
        dateOfBirth: displayToIso(dateOfBirth) || "1970-01-01",
        mobile,
        email: email || null,
        bloodGroup: bloodGroup || null,
        address: address || null,
      });
      await queryClient.invalidateQueries({ queryKey: QueryKeys.profile });
      router.back();
    } catch (err) {
      setError(err as PortalError);
    } finally {
      setBusy(false);
    }
  }

  if (profile.isLoading) return <Loading />;

  return (
    <Screen>
      <Title>Edit Profile</Title>

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
        label="Mobile Number"
        value={mobile}
        onChangeText={setMobile}
        keyboardType="phone-pad"
        maxLength={10}
      />

      <TextField
        label="Date of Birth (DD/MM/YYYY)"
        value={dateOfBirth}
        onChangeText={setDateOfBirth}
      />
      {dateOfBirth.length === 10 && (
        <Caption>Calculated Age: {formatAge(null, displayToIso(dateOfBirth))}</Caption>
      )}

      <TextField
        label="Blood Group"
        value={bloodGroup}
        onChangeText={setBloodGroup}
      />

      <TextField
        label="Email"
        value={email}
        onChangeText={setEmail}
        keyboardType="email-address"
        autoCapitalize="none"
      />

      {error ? (
        <ErrorBanner
          messageKey={error.message || "An error occurred"}
          correlationId={error.correlationId}
        />
      ) : null}

      <Button label="Save Changes" onPress={onSave} busy={busy} />
    </Screen>
  );
}
