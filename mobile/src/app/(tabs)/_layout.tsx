import React from "react";
import { Tabs } from "expo-router";
import { t } from "../../i18n";
import { colors } from "../../ui/tokens";

export default function TabsLayout() {
  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: colors.primary,
        tabBarInactiveTintColor: colors.textMuted,
        tabBarStyle: { backgroundColor: colors.surface, borderTopColor: colors.border },
        tabBarLabelStyle: { fontSize: 12 },
      }}
    >
      <Tabs.Screen name="index" options={{ title: t("app.name") }} />
      <Tabs.Screen name="appointments" options={{ title: t("appointments.title") }} />
      <Tabs.Screen name="visits" options={{ title: t("visits.title") }} />
      <Tabs.Screen name="settings" options={{ title: t("settings.title") }} />
    </Tabs>
  );
}
