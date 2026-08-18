import React, { useState, useMemo } from "react";
import { StyleSheet, TextInput, View } from "react-native";
import { useRouter } from "expo-router";
import { useQuery } from "@tanstack/react-query";
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
  Loading,
  Screen,
  Title,
} from "../../ui/components";
import { colors, radius, spacing, typography } from "../../ui/tokens";
import { PortalError } from "../../core/errors";
import { initials } from "../../core/format";
import type { Consultant } from "../../core/contracts";
import { Pressable, Text } from "react-native";

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
    <Screen>
      {/* Back button */}
      <Pressable onPress={() => router.back()} hitSlop={8}>
        <Text style={styles.back}>{t("common.back")}</Text>
      </Pressable>

      <Title>{t("consultants.title")}</Title>

      {/* Search */}
      <TextInput
        style={styles.searchInput}
        placeholder={t("consultants.search")}
        placeholderTextColor={colors.textMuted}
        value={search}
        onChangeText={setSearch}
        autoCapitalize="none"
        autoCorrect={false}
        returnKeyType="search"
      />

      {/* List */}
      {query.isLoading ? (
        <Loading />
      ) : query.isError ? (
        <ErrorBanner
          messageKey={(query.error as PortalError)?.message ?? "error.UNKNOWN"}
          correlationId={(query.error as PortalError)?.correlationId}
          onRetry={() => void query.refetch()}
        />
      ) : filtered.length === 0 ? (
        <EmptyState messageKey="consultants.empty" />
      ) : (
        filtered.map((c: Consultant) => (
          <Card
            key={c.consultantId}
            accessibilityLabel={c.fullName}
            onPress={() =>
              router.push(`/book/${c.consultantId}/slots` as never)
            }
          >
            <View style={styles.consultantRow}>
              <Avatar initials={initials(c.fullName)} />
              <View style={{ flex: 1 }}>
                <Heading>{c.fullName}</Heading>
                {c.specialisation ? (
                  <Body>{c.specialisation}</Body>
                ) : null}
                {c.departmentName ? (
                  <Caption>{c.departmentName}</Caption>
                ) : null}
                {c.qualification ? (
                  <Caption>{c.qualification}</Caption>
                ) : null}
              </View>
            </View>
          </Card>
        ))
      )}
    </Screen>
  );
}

const styles = StyleSheet.create({
  back: {
    ...typography.label,
    color: colors.primary,
    paddingVertical: spacing.xs,
  },
  searchInput: {
    height: 48,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    backgroundColor: colors.surface,
    paddingHorizontal: spacing.md,
    ...typography.body,
    color: colors.text,
  },
  consultantRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.md,
  },
});
