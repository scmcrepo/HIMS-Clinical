import React, { useState, useRef } from "react";
import {
  Image,
  Modal,
  PanResponder,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { WebView } from "react-native-webview";
import Constants from "expo-constants";
import { Ionicons } from "@expo/vector-icons";
import { colors, radius, spacing, typography } from "../tokens";
import { BASE_PDF_CSS } from "./helpers";

export function DocumentViewerModal({
  visible,
  title,
  html,
  imageUri,
  onClose,
  onPrint,
  onDownload,
}: {
  visible: boolean;
  title: string;
  html?: string;
  imageUri?: string;
  onClose: () => void;
  onPrint?: () => void;
  onDownload?: () => void;
}) {
  if (!visible) return null;

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <View style={s.viewerOverlay}>
        <View style={s.viewerHeader}>
          <View style={s.viewerHeaderTitleGroup}>
            <Ionicons name="document-text" size={20} color={colors.primary} />
            <Text style={s.viewerHeaderTitle} numberOfLines={1}>
              {title}
            </Text>
          </View>

          <View style={s.viewerActions}>
            {onDownload && (
              <Pressable onPress={onDownload} style={s.viewerDownloadBtn} hitSlop={6} accessibilityLabel="Download">
                <Ionicons name="download-outline" size={16} color={colors.surface} />
                <Text style={s.viewerDownloadBtnText}>Download</Text>
              </Pressable>
            )}
            <Pressable onPress={onClose} style={s.viewerCloseBtn} hitSlop={6} accessibilityLabel="Close Viewer">
              <Ionicons name="close" size={20} color={colors.textMuted} />
            </Pressable>
          </View>
        </View>

        <View style={s.viewerCanvasContainer}>
          {imageUri ? (
            <ImageViewerCard imageUri={imageUri} />
          ) : html ? (
            <DocHtmlCard html={html} />
          ) : (
            <Text style={s.viewerEmptyText}>No document preview available.</Text>
          )}
        </View>
      </View>
    </Modal>
  );
}

function ImageViewerCard({ imageUri }: { imageUri: string }) {
  const [scale, setScale] = useState(1);
  const [translateX, setTranslateX] = useState(0);
  const [translateY, setTranslateY] = useState(0);

  const scaleRef = useRef(1);
  const txRef = useRef(0);
  const tyRef = useRef(0);

  const startDistRef = useRef<number | null>(null);
  const startScaleRef = useRef(1);
  const startTouchRef = useRef<{ x: number; y: number }>({ x: 0, y: 0 });
  const startTxRef = useRef(0);
  const startTyRef = useRef(0);
  const lastTapRef = useRef(0);

  const apiBase = (Constants.expoConfig?.extra?.apiBaseUrl as string | undefined) || "";
  let fullUri = imageUri;
  if (imageUri && imageUri.startsWith("/") && apiBase) {
    fullUri = `${apiBase}${imageUri}`;
  }

  const getDistance = (touches: Array<{ pageX: number; pageY: number }>) => {
    const [t1, t2] = touches;
    if (!t1 || !t2) return 0;
    const dx = t1.pageX - t2.pageX;
    const dy = t1.pageY - t2.pageY;
    return Math.sqrt(dx * dx + dy * dy);
  };

  const panResponder = useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: () => true,
      onPanResponderGrant: (evt) => {
        const touches = evt.nativeEvent.touches;
        const now = Date.now();

        if (now - lastTapRef.current < 300) {
          const nextScale = scaleRef.current > 1.2 ? 1.0 : 2.5;
          scaleRef.current = nextScale;
          txRef.current = 0;
          tyRef.current = 0;
          setScale(nextScale);
          setTranslateX(0);
          setTranslateY(0);
        }
        lastTapRef.current = now;

        if (touches.length === 1 && touches[0]) {
          startTouchRef.current = { x: touches[0].pageX, y: touches[0].pageY };
          startTxRef.current = txRef.current;
          startTyRef.current = tyRef.current;
        } else if (touches.length === 2) {
          startDistRef.current = getDistance(touches);
          startScaleRef.current = scaleRef.current;
        }
      },
      onPanResponderMove: (evt) => {
        const touches = evt.nativeEvent.touches;

        if (touches.length === 1 && touches[0] && scaleRef.current > 1.0) {
          const dx = touches[0].pageX - startTouchRef.current.x;
          const dy = touches[0].pageY - startTouchRef.current.y;
          const nextTx = startTxRef.current + dx;
          const nextTy = startTyRef.current + dy;
          txRef.current = nextTx;
          tyRef.current = nextTy;
          setTranslateX(nextTx);
          setTranslateY(nextTy);
        } else if (touches.length === 2) {
          const currentDist = getDistance(touches);
          if (startDistRef.current && startDistRef.current > 0 && currentDist > 0) {
            const factor = currentDist / startDistRef.current;
            const nextScale = Math.max(0.6, Math.min(5.0, startScaleRef.current * factor));
            scaleRef.current = nextScale;
            setScale(nextScale);
          }
        }
      },
      onPanResponderRelease: () => {
        if (scaleRef.current < 1.0) {
          scaleRef.current = 1.0;
          txRef.current = 0;
          tyRef.current = 0;
          setScale(1.0);
          setTranslateX(0);
          setTranslateY(0);
        }
        startDistRef.current = null;
      },
    })
  ).current;

  return (
    <View style={s.imageViewerSheet} {...panResponder.panHandlers}>
      <View style={s.imageViewerScrollContent}>
        <Image
          source={{ uri: fullUri }}
          style={[
            s.fullViewerImage,
            { transform: [{ translateX }, { translateY }, { scale }] },
          ]}
          resizeMode="contain"
        />
      </View>
    </View>
  );
}

function DocHtmlCard({ html }: { html: string }) {
  let formattedHtml = html;

  const a4Viewport = '<meta name="viewport" content="width=794, initial-scale=0.45, minimum-scale=0.2, maximum-scale=3.0, user-scalable=yes" />';

  if (formattedHtml.includes('<meta name="viewport"')) {
    formattedHtml = formattedHtml.replace(/<meta\s+name="viewport"[^>]*>/i, a4Viewport);
  } else if (formattedHtml.includes("<head>")) {
    formattedHtml = formattedHtml.replace("<head>", `<head>${a4Viewport}`);
  } else {
    formattedHtml = `${a4Viewport}${formattedHtml}`;
  }

  if (!formattedHtml.includes("font-family: 'Inter'") && !formattedHtml.includes("BASE_PDF_CSS")) {
    if (formattedHtml.includes("</head>")) {
      formattedHtml = formattedHtml.replace("</head>", `<style>${BASE_PDF_CSS}</style></head>`);
    } else if (formattedHtml.includes("<head>")) {
      formattedHtml = formattedHtml.replace("<head>", `<head><style>${BASE_PDF_CSS}</style>`);
    }
  }

  if (Platform.OS === "web") {
    return (
      <iframe
        srcDoc={formattedHtml}
        style={{
          width: "100%",
          minWidth: 340,
          maxWidth: 640,
          height: "100%",
          minHeight: 720,
          border: "none",
          backgroundColor: "#ffffff",
          borderRadius: 8,
          boxShadow: "0 4px 16px rgba(0,0,0,0.18)",
        }}
      />
    );
  }

  return (
    <View style={s.a4PageSheet}>
      <WebView
        originWhitelist={["*"]}
        allowFileAccess={true}
        allowUniversalAccessFromFileURLs={true}
        allowFileAccessFromFileURLs={true}
        source={{ html: formattedHtml }}
        scalesPageToFit={true}
        showsHorizontalScrollIndicator={false}
        showsVerticalScrollIndicator={false}
        style={s.docWebView}
      />
    </View>
  );
}

const s = StyleSheet.create({
  viewerOverlay: {
    flex: 1,
    backgroundColor: "rgba(9, 9, 11, 0.95)",
  },
  viewerHeader: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: spacing.lg,
    paddingTop: Platform.OS === "ios" ? 50 : 20,
    paddingBottom: spacing.md,
    backgroundColor: colors.surface,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  viewerHeaderTitleGroup: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.sm,
    flex: 1,
    marginRight: spacing.md,
  },
  viewerHeaderTitle: {
    ...typography.label,
    fontSize: 16,
    color: colors.text,
    fontWeight: "700",
  },
  viewerActions: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.sm,
  },
  viewerDownloadBtn: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    backgroundColor: colors.primary,
    paddingHorizontal: spacing.md,
    paddingVertical: 6,
    borderRadius: radius.md,
  },
  viewerDownloadBtnText: {
    ...typography.label,
    fontSize: 13,
    color: colors.surface,
    fontWeight: "600",
  },
  viewerCloseBtn: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: colors.primarySoft,
    alignItems: "center",
    justifyContent: "center",
  },
  viewerCanvasContainer: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
  },
  viewerEmptyText: {
    ...typography.body,
    color: colors.surface,
  },
  imageViewerSheet: {
    flex: 1,
    width: "100%",
    justifyContent: "center",
    alignItems: "center",
  },
  imageViewerScrollContent: {
    flex: 1,
    width: "100%",
    justifyContent: "center",
    alignItems: "center",
  },
  fullViewerImage: {
    width: "100%",
    height: "100%",
  },
  a4PageSheet: {
    flex: 1,
    width: "100%",
    maxWidth: 794,
    backgroundColor: colors.surface,
  },
  docWebView: {
    flex: 1,
    backgroundColor: colors.surface,
  },
});
