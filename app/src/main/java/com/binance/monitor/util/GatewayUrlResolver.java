/*
 * MT5 网关地址解析工具，负责把设置页输入规范化成可直接请求的基础地址。
 * ConfigManager、设置页和 MT5 客户端都通过这里共享同一套规则。
 */
package com.binance.monitor.util;

import androidx.annotation.Nullable;

import java.net.URI;
import java.util.Locale;

public final class GatewayUrlResolver {

    private static final String DEFAULT_FALLBACK = "http://10.0.2.2:8787";
    private static final String PATH_MT5 = "/mt5";
    private static final String PATH_BINANCE_REST = "/binance-rest";
    private static final String PATH_BINANCE_WS = "/binance-ws";

    private GatewayUrlResolver() {
    }

    // 规范化基础地址，支持 host、health 地址或完整接口地址输入。
    public static String resolveBaseUrl(@Nullable String raw, @Nullable String fallback) {
        return normalizeBaseUrl(raw, fallback);
    }

    // 规范化基础地址，并在需要时补齐 scheme、端口和多余路径。
    public static String normalizeBaseUrl(@Nullable String raw, @Nullable String fallback) {
        String fallbackValue = normalizeCandidate(fallback, DEFAULT_FALLBACK);
        String source = raw == null ? "" : raw.trim();
        if (source.isEmpty()) {
            return fallbackValue;
        }
        return normalizeCandidate(source, fallbackValue);
    }

    // 把基础地址和接口路径拼成一个稳定可请求的完整地址。
    public static String buildEndpoint(@Nullable String baseUrl, @Nullable String path) {
        String base = normalizeBaseUrl(baseUrl, DEFAULT_FALLBACK);
        String suffix = path == null ? "" : path.trim();
        if (suffix.isEmpty()) {
            return base;
        }
        if (!suffix.startsWith("/")) {
            suffix = "/" + suffix;
        }
        return base + suffix;
    }

    // 解析网关根地址，供 Binance REST 与 WS 共用同一台服务器入口。
    public static String resolveGatewayRootBaseUrl(@Nullable String raw, @Nullable String fallback) {
        String normalized = normalizeBaseUrl(raw, fallback);
        try {
            URI uri = new URI(prepareForUri(normalized));
            String scheme = normalizeScheme(uri.getScheme());
            String host = uri.getHost();
            if (host == null || host.trim().isEmpty()) {
                return normalizeBaseUrl(fallback, DEFAULT_FALLBACK);
            }
            int port = uri.getPort();
            String path = sanitizeGatewayRootPath(uri.getPath());
            StringBuilder builder = new StringBuilder();
            builder.append(scheme).append("://").append(host.trim());
            if (port > 0 && port != defaultPort(scheme)) {
                builder.append(":").append(port);
            }
            if (!path.isEmpty()) {
                builder.append(path);
            }
            return builder.toString();
        } catch (Exception ignored) {
            return normalizeBaseUrl(fallback, DEFAULT_FALLBACK);
        }
    }

    // 基于同一个网关根地址拼出 Binance REST 地址。
    public static String buildBinanceRestBaseUrl(@Nullable String rawBaseUrl, @Nullable String fallback) {
        return buildEndpoint(resolveGatewayRootBaseUrl(rawBaseUrl, fallback), PATH_BINANCE_REST + "/fapi/v1/klines");
    }

    // 基于同一个网关根地址拼出 Binance WebSocket 地址。
    public static String buildBinanceWebSocketBaseUrl(@Nullable String rawBaseUrl, @Nullable String fallback) {
        String httpBase = buildEndpoint(resolveGatewayRootBaseUrl(rawBaseUrl, fallback), PATH_BINANCE_WS);
        if (httpBase.startsWith("https://")) {
            return "wss://" + httpBase.substring("https://".length());
        }
        if (httpBase.startsWith("http://")) {
            return "ws://" + httpBase.substring("http://".length());
        }
        return httpBase;
    }

    private static String normalizeCandidate(@Nullable String raw, String fallback) {
        try {
            String source = raw == null ? "" : raw.trim();
            if (source.isEmpty()) {
                return fallback;
            }
            String prepared = prepareForUri(source);
            URI uri = new URI(prepared);
            if (uri.getHost() == null || uri.getHost().trim().isEmpty()) {
                return fallback;
            }

            String scheme = normalizeScheme(uri.getScheme());
            String host = uri.getHost().trim();
            String sanitizedPath = sanitizePath(uri.getPath());
            boolean rawHasExplicitPort = uri.getPort() > 0;
            int port = resolvePort(uri, fallback, rawHasExplicitPort, sanitizedPath);
            boolean writePort = shouldWritePort(rawHasExplicitPort, sanitizedPath, port, scheme);

            StringBuilder builder = new StringBuilder();
            builder.append(scheme).append("://").append(host);
            if (writePort) {
                builder.append(":").append(port);
            }
            if (!sanitizedPath.isEmpty()) {
                builder.append(sanitizedPath);
            }
            return builder.toString();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String prepareForUri(String raw) {
        String value = raw.trim()
                .replace("wss://", "https://")
                .replace("ws://", "http://");
        if (value.startsWith("//")) {
            value = "http:" + value;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            value = "http://" + value;
        }
        return value;
    }

    private static String normalizeScheme(@Nullable String scheme) {
        if (scheme == null || scheme.trim().isEmpty()) {
            return "http";
        }
        String value = scheme.trim().toLowerCase(Locale.ROOT);
        if ("ws".equals(value)) {
            return "http";
        }
        if ("wss".equals(value)) {
            return "https";
        }
        return value;
    }

    private static String sanitizePath(@Nullable String rawPath) {
        String path = rawPath == null ? "" : rawPath.trim();
        if (path.isEmpty() || "/".equals(path)) {
            return "";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        int v1Index = path.indexOf("/v1/");
        if (v1Index >= 0) {
            path = path.substring(0, v1Index);
        } else if (path.endsWith("/health")) {
            path = path.substring(0, path.length() - "/health".length());
        }
        while (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        return "/".equals(path) ? "" : path;
    }

    private static String sanitizeGatewayRootPath(@Nullable String rawPath) {
        String path = sanitizePath(rawPath);
        if (path.isEmpty()) {
            return "";
        }
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(PATH_MT5)) {
            path = path.substring(0, path.length() - PATH_MT5.length());
        } else if (lower.contains(PATH_BINANCE_REST)) {
            path = path.substring(0, lower.indexOf(PATH_BINANCE_REST));
        } else if (lower.contains(PATH_BINANCE_WS)) {
            path = path.substring(0, lower.indexOf(PATH_BINANCE_WS));
        }
        while (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        return "/".equals(path) ? "" : path;
    }

    private static int resolvePort(URI uri,
                                   String fallback,
                                   boolean rawHasExplicitPort,
                                   String sanitizedPath) {
        if (rawHasExplicitPort) {
            return uri.getPort();
        }
        if (sanitizedPath.isEmpty()) {
            int fallbackPort = extractPort(fallback);
            if (fallbackPort > 0) {
                return fallbackPort;
            }
            return 8787;
        }
        return defaultPort(normalizeScheme(uri.getScheme()));
    }

    private static int extractPort(String url) {
        try {
            URI uri = new URI(prepareForUri(url));
            return uri.getPort();
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static boolean shouldWritePort(boolean rawHasExplicitPort,
                                           String sanitizedPath,
                                           int port,
                                           String scheme) {
        if (port <= 0) {
            return false;
        }
        if (rawHasExplicitPort) {
            return true;
        }
        if (!sanitizedPath.isEmpty()) {
            return false;
        }
        return port != defaultPort(scheme);
    }

    private static int defaultPort(String scheme) {
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }
}
