import React, { useState } from "react";
import { View, StyleSheet } from "react-native";
import { useRouter } from "expo-router";
import { useQuery } from "@tanstack/react-query";
import { useContainer } from "../_layout";
import { QueryKeys } from "../../core/cachePolicy";
import { formatIsoDate, formatStatus } from "../../core/format";
import { t } from "../../i18n";
import {
  Badge,
  Body,
  Button,
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
import type { VisitSummary } from "../../core/contracts";

/**
 * Screen 11 — Visit history tab (PRD §7, WO-019 §4.7).
 *
 * Paginated list of encounter summaries. Each card navigates to the
 * 4-tab visit detail at `/visits/[encounterId]`.
 */

export default function VisitsHistoryScreen() {
  const router = useRouter();
  const { api } = useContainer();
  const [page, setPage] = useState(0);

  const query = useQuery({
    queryKey: QueryKeys.visits(page),
    queryFn: () => api.listVisits(page, 10),
  });

  const visits = query.data?.content ?? [];
  const totalPages = query.data?.totalPages ?? 1;

  return (
    <Screen>
      <Title>{t("visits.title")}</Title>

      {query.isLoading ? (
        <Loading />
      ) : query.isError ? (
        <ErrorBanner
          messageKey={(query.error as PortalError)?.message ?? "error.UNKNOWN"}
          correlationId={(query.error as PortalError)?.correlationId}
          onRetry={() => void query.refetch()}
        />
      ) : visits.length === 0 ? (
        <EmptyState messageKey="visits.empty" />
      ) : (
        visits.map((v: VisitSummary) => (
          <Card
            key={v.encounterId}
            accessibilityLabel={`${formatIsoDate(v.visitDate)} ${v.consultantName ?? ""}`}
            onPress={() => router.push(`/visits/${v.encounterId}` as never)}
          >
            <Heading>{v.consultantName ?? "—"}</Heading>
            <Body>
              {formatIsoDate(v.visitDate)} · {v.encounterType}
            </Body>
            <Badge label={formatStatus(v.status)} />
          </Card>
        ))
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <View style={styles.pagination}>
          <Button
            label="← Prev"
            variant="secondary"
            onPress={() => setPage((p) => Math.max(0, p - 1))}
            disabled={page === 0}
          />
          <Body>
            {page + 1} / {totalPages}
          </Body>
          <Button
            label="Next →"
            variant="secondary"
            onPress={() => setPage((p) => p + 1)}
            disabled={page >= totalPages - 1}
          />
        </View>
      )}
    </Screen>
  );
}

const styles = StyleSheet.create({
  pagination: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: spacing.md,
    paddingVertical: spacing.md,
  },
});
