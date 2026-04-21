/*
 * 模块容器注册表，统一把页面标签映射到正式容器角色。
 */
package com.binance.monitor.ui.rules;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

public final class ContainerSurfaceRegistry {
    private ContainerSurfaceRegistry() {
    }

    // 按页面或模块标签解析正式容器角色。
    @NonNull
    public static ContainerSurfaceRole resolveSectionRole(@Nullable String tag) {
        String normalized = normalizeTag(tag);
        if (normalized.isEmpty()) {
            return ContainerSurfaceRole.SECTION_SURFACE;
        }
        if (normalized.contains("floating")) {
            return ContainerSurfaceRole.FLOATING_SURFACE;
        }
        if (normalized.contains("dialog")
                || normalized.contains("sheet")
                || normalized.contains("overlay")) {
            return ContainerSurfaceRole.OVERLAY_SURFACE;
        }
        if (normalized.contains("row")
                || normalized.contains("record")
                || normalized.contains("item")) {
            return ContainerSurfaceRole.ROW_SURFACE;
        }
        if (normalized.contains("field")
                || normalized.contains("input")
                || normalized.contains("select")) {
            return ContainerSurfaceRole.FIELD_SURFACE;
        }
        if (normalized.contains("page")
                || normalized.contains("canvas")) {
            return ContainerSurfaceRole.PAGE_CANVAS;
        }
        return ContainerSurfaceRole.SECTION_SURFACE;
    }

    // 统一规整标签文本。
    @NonNull
    private static String normalizeTag(@Nullable String tag) {
        if (tag == null) {
            return "";
        }
        return tag.trim().replace(" ", "").toLowerCase(Locale.ROOT);
    }
}
