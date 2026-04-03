/*
 * 主界面连接状态弹窗的网络诊断工具，负责探测服务器延迟与地理位置。
 * 与 MainActivity、网关地址解析工具协同工作。
 */
package com.binance.monitor.ui.main;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.util.GatewayUrlResolver;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;

public final class ConnectionDetailNetworkHelper {

    private static final int CONNECT_TIMEOUT_MS = 1200;
    private static final int READ_TIMEOUT_MS = 1500;
    private static final String HEALTH_PATH = "/health";
    private static final String IP_GEO_LOOKUP_PREFIX = "https://ipwho.is/";

    private ConnectionDetailNetworkHelper() {
    }

    // 统一承载服务器地理位置和延迟文案，避免 Activity 里再拼装字符串。
    public static final class ServerDiagnostics {
        public final String location;
        public final String latencyText;

        private ServerDiagnostics(String location, String latencyText) {
            this.location = location;
            this.latencyText = latencyText;
        }
    }

    // 加载服务器诊断信息，供连接状态弹窗异步回填。
    @NonNull
    public static ServerDiagnostics load(@Nullable String gatewayRoot) {
        String normalizedRoot = GatewayUrlResolver.resolveGatewayRootBaseUrl(
                gatewayRoot,
                AppConstants.MT5_GATEWAY_BASE_URL
        );
        String host = extractHost(normalizedRoot);
        if (host.isEmpty()) {
            return new ServerDiagnostics("--", "--");
        }
        long latencyMs = measureLatency(normalizedRoot);
        String location = resolveLocation(host);
        return new ServerDiagnostics(location, formatLatency(latencyMs));
    }

    // 判断主机是否属于本地/内网，避免对本地地址做公网地理查询。
    static boolean isPrivateHost(@Nullable String host) {
        String normalized = host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return true;
        }
        if ("localhost".equals(normalized) || normalized.endsWith(".local")) {
            return true;
        }
        if (normalized.startsWith("127.")
                || normalized.startsWith("10.")
                || normalized.startsWith("192.168.")
                || normalized.startsWith("169.254.")) {
            return true;
        }
        if (!normalized.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
            return false;
        }
        String[] segments = normalized.split("\\.");
        if (segments.length != 4) {
            return false;
        }
        try {
            int second = Integer.parseInt(segments[1]);
            return "172".equals(segments[0]) && second >= 16 && second <= 31;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    // 统一格式化服务器地理位置文本，避免同一信息重复显示或为空。
    static String formatLocation(@Nullable String host,
                                 @Nullable String ip,
                                 @Nullable String country,
                                 @Nullable String region,
                                 @Nullable String city) {
        if (isPrivateHost(host) || isPrivateHost(ip)) {
            return "内网/本地服务器";
        }
        List<String> parts = new ArrayList<>();
        appendIfNotBlank(parts, city);
        appendIfNotBlank(parts, region);
        appendIfNotBlank(parts, country);
        if (!parts.isEmpty()) {
            return String.join(", ", parts);
        }
        if (ip != null && !ip.trim().isEmpty()) {
            return ip.trim();
        }
        if (host != null && !host.trim().isEmpty()) {
            return host.trim();
        }
        return "--";
    }

    // 统一格式化服务器延迟，没有成功探测时显示占位。
    static String formatLatency(long latencyMs) {
        return latencyMs >= 0L ? latencyMs + "ms" : "--";
    }

    // 解析服务器位置，公网地址优先做 IP 地理查询，失败时退回主机信息。
    @NonNull
    private static String resolveLocation(@NonNull String host) {
        if (isPrivateHost(host)) {
            return "内网/本地服务器";
        }
        String ip = resolveIpv4(host);
        if (ip.isEmpty()) {
            return host;
        }
        if (isPrivateHost(ip)) {
            return "内网/本地服务器";
        }
        GeoInfo geoInfo = lookupGeo(ip);
        return formatLocation(host, ip, geoInfo.country, geoInfo.region, geoInfo.city);
    }

    // 通过健康检查接口测量当前服务器延迟。
    private static long measureLatency(@NonNull String gatewayRoot) {
        String healthUrl = gatewayRoot.endsWith("/")
                ? gatewayRoot.substring(0, gatewayRoot.length() - 1) + HEALTH_PATH
                : gatewayRoot + HEALTH_PATH;
        HttpURLConnection connection = null;
        long startNs = System.nanoTime();
        try {
            connection = (HttpURLConnection) new URI(healthUrl).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.connect();
            int responseCode = connection.getResponseCode();
            consumeQuietly(responseCode >= 400 ? connection.getErrorStream() : connection.getInputStream());
            return responseCode >= 200 && responseCode < 500
                    ? Math.max(1L, Math.round((System.nanoTime() - startNs) / 1_000_000d))
                    : -1L;
        } catch (Exception ignored) {
            return -1L;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    // 解析 URL 中的主机名，兼容 host 与完整 http 地址两种输入。
    @NonNull
    private static String extractHost(@Nullable String gatewayRoot) {
        if (gatewayRoot == null || gatewayRoot.trim().isEmpty()) {
            return "";
        }
        try {
            URI uri = new URI(gatewayRoot.trim());
            return uri.getHost() == null ? "" : uri.getHost().trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    // 尝试把主机解析成 IPv4 地址，供地理位置查询使用。
    @NonNull
    private static String resolveIpv4(@NonNull String host) {
        try {
            InetAddress address = InetAddress.getByName(host);
            return address == null ? "" : address.getHostAddress();
        } catch (Exception ignored) {
            return "";
        }
    }

    // 查询公网 IP 的地理位置，失败时返回空信息供上层兜底。
    @NonNull
    private static GeoInfo lookupGeo(@NonNull String ip) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URI(IP_GEO_LOOKUP_PREFIX + ip).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.connect();
            if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
                return GeoInfo.EMPTY;
            }
            String body = readUtf8(connection.getInputStream());
            if (body.isEmpty()) {
                return GeoInfo.EMPTY;
            }
            JSONObject root = new JSONObject(body);
            if (!root.optBoolean("success", false)) {
                return GeoInfo.EMPTY;
            }
            return new GeoInfo(
                    root.optString("ip", ip),
                    root.optString("country", ""),
                    root.optString("region", ""),
                    root.optString("city", "")
            );
        } catch (Exception ignored) {
            return GeoInfo.EMPTY;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    // 安全读取响应体，避免网络探测时泄露异常到 UI 层。
    @NonNull
    private static String readUtf8(@Nullable InputStream inputStream) {
        if (inputStream == null) {
            return "";
        }
        try (InputStream stream = inputStream) {
            byte[] buffer = new byte[1024];
            StringBuilder builder = new StringBuilder();
            int read;
            while ((read = stream.read(buffer)) >= 0) {
                builder.append(new String(buffer, 0, read, java.nio.charset.StandardCharsets.UTF_8));
            }
            return builder.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    // 安静消费连接流，确保连接及时释放。
    private static void consumeQuietly(@Nullable InputStream inputStream) {
        readUtf8(inputStream);
    }

    // 只把有效的地理字段加入结果列表。
    private static void appendIfNotBlank(List<String> output, @Nullable String value) {
        if (output == null || value == null) {
            return;
        }
        String normalized = value.trim();
        if (!normalized.isEmpty()) {
            output.add(normalized);
        }
    }

    // 承载公网 IP 地理位置查询结果。
    private static final class GeoInfo {
        private static final GeoInfo EMPTY = new GeoInfo("", "", "", "");

        private final String ip;
        private final String country;
        private final String region;
        private final String city;

        private GeoInfo(String ip, String country, String region, String city) {
            this.ip = ip;
            this.country = country;
            this.region = region;
            this.city = city;
        }
    }
}
