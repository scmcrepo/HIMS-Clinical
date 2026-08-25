import React, { useState, useCallback } from "react";
import { View, StyleSheet, Text } from "react-native";
import { useRouter } from "expo-router";
import { useQuery } from "@tanstack/react-query";
import { Ionicons } from "@expo/vector-icons";
import { useContainer } from "../_layout";
import { QueryKeys } from "../../core/cachePolicy";
import { formatIsoDate, formatStatus } from "../../core/format";
import { t } from "../../i18n";
import {
  Badge,
  Body,
  Button,
  Card,
  Caption,
  EmptyState,
  ErrorBanner,
  Heading,
  Screen,
  SkeletonCard,
  Title,
} from "../../ui/components";
import { colors, spacing, typography } from "../../ui/tokens";
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

  const handleRefresh = useCallback(() => {
    void query.refetch();
  }, [query]);

  const visits = query.data?.content ?? [];
  const totalPages = query.data?.totalPages ?? 1;

  return (
    <Screen onRefresh={handleRefresh} refreshing={query.isRefetching && !query.isLoading}>
      <Title>{t("visits.title")}</Title>

      {query.isLoading ? (
        <>
          <SkeletonCard lines={3} />
          <SkeletonCard lines={3} />
          <SkeletonCard lines={3} />
        </>
      ) : query.isError ? (
        <ErrorBanner
          messageKey={(query.error as PortalError)?.message ?? "error.UNKNOWN"}
          correlationId={(query.error as PortalError)?.correlationId}
          onRetry={() => void query.refetch()}
        />
      ) : visits.length === 0 ? (
        <EmptyState
          messageKey="visits.empty"
          icon="clipboard-outline"
          description="Your visit records will appear here after your appointments."
        />
      ) : (
        visits.map((v: VisitSummary) => (
          <Card
            key={v.encounterId}
            accessibilityLabel={`${formatIsoDate(v.visitDate)} ${v.consultantName ?? ""}`}
            onPress={() => router.push(`/visits/${v.encounterId}` as never)}
          >
            <View style={s.visitHeader}>
              <View style={{ flex: 1 }}>
                <Heading>{v.consultantName ?? "—"}</Heading>
                <View style={s.detailRow}>
                  <View style={s.detailItem}>
                    <Ionicons name="calendar-outline" size={13} color={colors.textMuted} />
                    <Text style={s.detailText}>{formatIsoDate(v.visitDate)}</Text>
                  </View>
                  <View style={s.typeBadge}>
                    <Text style={s.typeText}>{v.encounterType}</Text>
                  </View>
                </View>
              </View>
              <View style={s.rightGroup}>
                <Badge label={formatStatus(v.status)} />
                <Ionicons name="chevron-forward" size={16} color={colors.textMuted} />
              </View>
            </View>
          </Card>
        ))
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <View style={s.pagination}>
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

const s = StyleSheet.create({
  visitHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "flex-start",
    gap: spacing.sm,
  },
  detailRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.md,
    marginTop: spacing.xs,
  },
  detailItem: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
  },
  detailText: {
    ...typography.caption,
    fontSize: 13,
    color: colors.textMuted,
    fontWeight: "500",
  },
  typeBadge: {
    paddingHorizontal: 6,
    paddingVertical: 1,
    borderRadius: 4,
    backgroundColor: colors.primarySoft,
  },
  typeText: {
    ...typography.caption,
    fontSize: 11,
    fontWeight: "700",
    color: colors.text,
  },
  rightGroup: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.sm,
  },
  pagination: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: spacing.md,
    paddingVertical: spacing.md,
  },
});
