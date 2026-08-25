import React from "react";
import { StyleSheet } from "react-native";
import { Tabs } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import { t } from "../../i18n";
import { colors, shadows, spacing, typography } from "../../ui/tokens";

export default function TabsLayout() {
  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: colors.primary,
        tabBarInactiveTintColor: colors.textMuted,
        tabBarStyle: tabBarStyle,
        tabBarLabelStyle: tabBarLabelStyle,
        tabBarItemStyle: { paddingVertical: 2 },
      }}
    >
      <Tabs.Screen
        name="index"
        options={{
          title: "Home",
          tabBarLabel: "Home",
          tabBarIcon: ({ color, focused }) => (
            <Ionicons name={focused ? "home" : "home-outline"} size={22} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="appointments"
        options={{
          title: "Appointments",
          tabBarLabel: "Appointments",
          tabBarIcon: ({ color, focused }) => (
            <Ionicons name={focused ? "calendar" : "calendar-outline"} size={22} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="visits"
        options={{
          title: "Visits",
          tabBarLabel: "Visits",
          tabBarIcon: ({ color, focused }) => (
            <Ionicons name={focused ? "clipboard" : "clipboard-outline"} size={22} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="settings"
        options={{
          title: "Profile",
          tabBarLabel: "Profile",
          tabBarIcon: ({ color, focused }) => (
            <Ionicons name={focused ? "person" : "person-outline"} size={22} color={color} />
          ),
        }}
      />
      <Tabs.Screen name="edit-profile" options={{ href: null }} />
    </Tabs>
  );
}

const tabBarStyle = {
  backgroundColor: colors.surface,
  borderTopColor: colors.border,
  borderTopWidth: StyleSheet.hairlineWidth,
  height: 56,
  paddingBottom: 4,
  ...shadows.sm,
  shadowOffset: { width: 0, height: -2 },
} as const;

const tabBarLabelStyle = {
  ...typography.caption,
  fontSize: 11,
  fontWeight: "600" as const,
};
