import React, { useEffect, useMemo, useState } from "react";
import { Stack, useRouter, useSegments } from "expo-router";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { StatusBar } from "expo-status-bar";
import * as Localization from "expo-localization";
import { createContainer, type AppContainer } from "../state/container";
import { useAuthStore } from "../state/authStore";
import { setLocale } from "../i18n";
import { Loading } from "../ui/components";
import { colors } from "../ui/tokens";

/**
 * Root layout: builds the container once, restores any stored session, and
 * routes between the auth stack and the tab stack.
 *
 * The container is created in a useMemo rather than at module scope so that a
 * fast refresh in development does not leave two SessionManagers racing each
 * other to refresh the same token — which would look exactly like the token
 * reuse WO-017 treats as theft.
 */

export const ContainerContext = React.createContext<AppContainer | null>(null);

export function useContainer(): AppContainer {
  const c = React.useContext(ContainerContext);
  if (!c) throw new Error("useContainer must be used inside the root layout");
  return c;
}

export default function RootLayout() {
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
          // Clearing the query cache matters as much as clearing the token: the
          // in-memory cache still holds this patient's clinical responses, and
          // the next patient to log in on a shared handset must not see them.
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
    if (phase === "unknown") return;
    const inAuthGroup = segments[0] === "(auth)" || segments[0] === "(register)";
    if (phase === "ready" && inAuthGroup) {
      router.replace("/(tabs)");
    } else if (phase !== "ready" && !inAuthGroup) {
      router.replace("/(auth)/login");
    }
  }, [phase, segments, router]);

  return (
    <ContainerContext.Provider value={container}>
      <QueryClientProvider client={queryClient}>
        <StatusBar style="dark" />
        {phase === "unknown" ? (
          <Loading />
        ) : (
          <Stack
            screenOptions={{
              headerShown: false,
              contentStyle: { backgroundColor: colors.surfaceAlt },
            }}
          />
        )}
      </QueryClientProvider>
    </ContainerContext.Provider>
  );
}
