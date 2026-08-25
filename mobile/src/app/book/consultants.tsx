import React, { useState, useMemo, useCallback } from "react";
import { StyleSheet, View, Text } from "react-native";
import { useRouter } from "expo-router";
import { useQuery } from "@tanstack/react-query";
import { Ionicons } from "@expo/vector-icons";
import { useContainer } from "../_layout";
import { QueryKeys } from "../../core/cachePolicy";
import { t } from "../../i18n";
import {
  Avatar,
  Body,
  Caption,
  Card,
  EmptyState,
  ErrorBanner,
  Heading,
  Screen,
  SearchInput,
  SkeletonRow,
  Title,
  BackButton,
} from "../../ui/components";
import { colors, radius, spacing, typography } from "../../ui/tokens";
import { PortalError } from "../../core/errors";
import { initials } from "../../core/format";
import type { Consultant } from "../../core/contracts";

/**
 * Screen 6 — Consultant list (PRD §7, WO-019 §4.7).
 *
 * Search filters are client-side for instant response when the list is
 * small (typical hospital: 20-80 consultants). The server-side `q` param
 * is also sent so the fetch itself is filtered on branches with 200+.
 */

export default function ConsultantsScreen() {
  const router = useRouter();
  const { api } = useContainer();
  const [search, setSearch] = useState("");

  const query = useQuery({
    queryKey: QueryKeys.consultants(search),
    queryFn: () => api.listConsultants(search ? { q: search } : undefined),
  });

  const handleRefresh = useCallback(() => {
    void query.refetch();
  }, [query]);

  const filtered = useMemo(() => {
    if (!query.data) return [];
    if (!search.trim()) return query.data;
    const lower = search.toLowerCase();
    return query.data.filter(
      (c: Consultant) =>
        c.fullName.toLowerCase().includes(lower) ||
        (c.departmentName?.toLowerCase().includes(lower) ?? false) ||
        (c.specialisation?.toLowerCase().includes(lower) ?? false),
    );
  }, [query.data, search]);

  return (
    <Screen onRefresh={handleRefresh} refreshing={query.isRefetching && !query.isLoading}>
      {/* Back button */}
      <BackButton onPress={() => router.back()} label={t("common.back")} />

      <Title>{t("consultants.title")}</Title>

      {/* Search */}
      <SearchInput
        value={search}
        onChangeText={setSearch}
        placeholder={t("consultants.search")}
      />

      {/* List */}
      {query.isLoading ? (
        <>
          <SkeletonRow />
          <SkeletonRow />
          <SkeletonRow />
          <SkeletonRow />
        </>
      ) : query.isError ? (
        <ErrorBanner
          messageKey={(query.error as PortalError)?.message ?? "error.UNKNOWN"}
          correlationId={(query.error as PortalError)?.correlationId}
          onRetry={() => void query.refetch()}
        />
      ) : filtered.length === 0 ? (
        <EmptyState
          messageKey="consultants.empty"
          icon="people-outline"
          description="No doctors match your search criteria."
        />
      ) : (
        filtered.map((c: Consultant) => (
          <Card
            key={c.consultantId}
            accessibilityLabel={c.fullName}
            onPress={() =>
              router.push(`/book/${c.consultantId}/slots` as never)
            }
          >
            <View style={s.consultantRow}>
              <Avatar initials={initials(c.fullName)} />
              <View style={{ flex: 1 }}>
                <Heading>{c.fullName}</Heading>
                {c.specialisation ? (
                  <Text style={s.specialisation}>{c.specialisation}</Text>
                ) : null}
                {c.departmentName ? (
                  <Caption>{c.departmentName}</Caption>
                ) : null}
                {c.qualification ? (
                  <Caption>{c.qualification}</Caption>
                ) : null}
              </View>
              <Ionicons name="chevron-forward" size={18} color={colors.textMuted} />
            </View>
          </Card>
        ))
      )}
    </Screen>
  );
}

const s = StyleSheet.create({
  consultantRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.md,
  },
  specialisation: {
    ...typography.body,
    fontSize: 14,
    color: colors.text,
    fontWeight: "500",
  },
});
