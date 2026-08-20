import React from "react";
import { View } from "react-native";
import { useRouter } from "expo-router";
import { useQuery } from "@tanstack/react-query";
import { useContainer } from "../_layout";
import { QueryKeys } from "../../core/cachePolicy";
import { upcomingAppointments } from "../../core/booking";
import {
  formatIsoDate,
  formatPatientName,
  formatStatus,
  formatTimeRange,
  initials,
} from "../../core/format";
import { t } from "../../i18n";
import {
  Avatar,
  Badge,
  Body,
  Button,
  Caption,
  Card,
  EmptyState,
  ErrorBanner,
  Heading,
  Loading,
  Screen,
  Title,
} from "../../ui/components";
import { spacing } from "../../ui/tokens";
import { PortalError } from "../../core/errors";

/** Screen 5 — dashboard (PRD §3.2 step 5). */
export default function DashboardScreen() {
  const router = useRouter();
  const { api } = useContainer();

  const profile = useQuery({
    queryKey: QueryKeys.profile,
    queryFn: () => api.getProfile(),
  });

  const appointments = useQuery({
    queryKey: QueryKeys.appointments("upcoming"),
    queryFn: () => api.listAppointments("upcoming"),
  });

  const visits = useQuery({
    queryKey: QueryKeys.visits(0),
    // PRD §3.2 step 5 shows the last five; the endpoint pages at ten.
    queryFn: () => api.listVisits(0, 5),
  });

  if (profile.isLoading) return <Loading />;

  if (profile.isError) {
    const err = profile.error as PortalError;
    return (
      <Screen>
        <ErrorBanner
          messageKey={err.message}
          correlationId={err.correlationId}
          onRetry={() => void profile.refetch()}
        />
      </Screen>
    );
  }

  const me = profile.data;
  const upcoming = upcomingAppointments(
    appointments.data?.content ?? [],
    new Date(),
  );

  return (
    <Screen>
      {me ? (
        <View style={{ flexDirection: "row", gap: spacing.md, alignItems: "center" }}>
          <Avatar initials={initials(me.fullName)} photoUrl={me.photoUrl} />
          <View style={{ flex: 1 }}>
            <Title>{t("dashboard.greeting", { name: formatPatientName(me.fullName) })}</Title>
            <Caption>
              {me.tenantName} · {me.branchName}
            </Caption>
          </View>
        </View>
      ) : null}

      <Button
        label={t("dashboard.bookAppointment")}
        onPress={() => router.push("/book/consultants")}
      />

      <Heading>{t("dashboard.upcoming")}</Heading>
      {appointments.isLoading ? (
        <Loading />
      ) : upcoming.length === 0 ? (
        <EmptyState messageKey="dashboard.noUpcoming" />
      ) : (
        upcoming.map((a) => (
          <Card key={a.appointmentId}>
            <Heading>{a.consultantName}</Heading>
            <Body>
              {formatIsoDate(a.appointmentDate)} ·{" "}
              {formatTimeRange(a.fromTime, a.toTime)}
            </Body>
            {a.departmentName ? <Caption>{a.departmentName}</Caption> : null}
            <Badge
              label={formatStatus(a.status)}
              tone={a.status === "CANCELLED" ? "danger" : "success"}
            />
          </Card>
        ))
      )}

      <Heading>{t("dashboard.recentVisits")}</Heading>
      {visits.isLoading ? (
        <Loading />
      ) : (visits.data?.content ?? []).length === 0 ? (
        <EmptyState messageKey="visits.empty" />
      ) : (
        (visits.data?.content ?? []).map((v) => (
          <Card
            key={v.encounterId}
            accessibilityLabel={`${formatIsoDate(v.visitDate)} ${v.consultantName ?? ""}`}
            onPress={() => router.push(`/visits/${v.encounterId}`)}
          >
            <Heading>{v.consultantName ?? "—"}</Heading>
            <Body>
              {formatIsoDate(v.visitDate)} · {v.encounterType}
            </Body>
            <Badge label={formatStatus(v.status)} />
          </Card>
        ))
      )}
    </Screen>
  );
}
