import "react-native-gesture-handler";
import React, { useEffect, useMemo, useState } from "react";
import { StyleSheet } from "react-native";
import { Stack, useRouter, useSegments } from "expo-router";
import { GestureHandlerRootView } from "react-native-gesture-handler";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { StatusBar } from "expo-status-bar";
import * as Localization from "expo-localization";
import {
  useFonts,
  Inter_400Regular,
  Inter_500Medium,
  Inter_600SemiBold,
  Inter_700Bold,
} from "@expo-google-fonts/inter";
import { createContainer, type AppContainer } from "../state/container";
import { useAuthStore } from "../state/authStore";
import { setLocale } from "../i18n";
import { Loading } from "../ui/components";
import { colors } from "../ui/tokens";

export const ContainerContext = React.createContext<AppContainer | null>(null);

export function useContainer(): AppContainer {
  const c = React.useContext(ContainerContext);
  if (!c) throw new Error("useContainer must be used inside the root layout");
  return c;
}

export default function RootLayout() {
  const [fontsLoaded] = useFonts({
    Inter_400Regular,
    Inter_500Medium,
    Inter_600SemiBold,
    Inter_700Bold,
  });

  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            retry: 1,
            refetchOnWindowFocus: true,
            staleTime: 60_000,
          },
        },
      }),
  );

  const phase = useAuthStore((s) => s.phase);
  const bootstrap = useAuthStore((s) => s.bootstrap);
  const reset = useAuthStore((s) => s.reset);

  const container = useMemo(
    () =>
      createContainer({
        onSessionLost: () => {
          queryClient.clear();
          reset();
        },
      }),
    [queryClient, reset],
  );

  useEffect(() => {
    setLocale(Localization.getLocales()[0]?.languageCode ?? "en");
    void bootstrap(container);
  }, [bootstrap, container]);

  const segments = useSegments();
  const router = useRouter();

  useEffect(() => {
    if (phase === "unknown" || !fontsLoaded) return;
    const inAuthGroup = segments[0] === "(auth)" || segments[0] === "(register)";
    if (phase === "ready" && inAuthGroup) {
      router.replace("/(tabs)");
    } else if (phase !== "ready" && !inAuthGroup) {
      router.replace("/(auth)/login");
    }
  }, [phase, fontsLoaded, segments, router]);

  if (phase === "unknown" || !fontsLoaded) {
    return (
      <GestureHandlerRootView style={rootStyle.flex}>
        <ContainerContext.Provider value={container}>
          <QueryClientProvider client={queryClient}>
            <StatusBar style="dark" />
            <Loading />
          </QueryClientProvider>
        </ContainerContext.Provider>
      </GestureHandlerRootView>
    );
  }

  return (
    <GestureHandlerRootView style={rootStyle.flex}>
      <ContainerContext.Provider value={container}>
        <QueryClientProvider client={queryClient}>
          <StatusBar style="dark" />
          <Stack
            initialRouteName={phase === "ready" ? "(tabs)" : "(auth)/login"}
            screenOptions={{
              headerShown: false,
              contentStyle: { backgroundColor: colors.surfaceAlt },
            }}
          />
        </QueryClientProvider>
      </ContainerContext.Provider>
    </GestureHandlerRootView>
  );
}

const rootStyle = StyleSheet.create({
  flex: { flex: 1 },
});
