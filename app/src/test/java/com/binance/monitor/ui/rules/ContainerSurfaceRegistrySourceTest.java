/*
 * 容器角色注册表源码测试，锁定 role-based surface 入口已经建立。
 */
package com.binance.monitor.ui.rules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ContainerSurfaceRegistrySourceTest {

    @Test
    public void containerSurfaceRoleShouldCoverSixCanonicalRoles() {
        assertEquals(ContainerSurfaceRole.PAGE_CANVAS, ContainerSurfaceRole.PAGE_CANVAS);
        assertEquals(ContainerSurfaceRole.SECTION_SURFACE, ContainerSurfaceRole.SECTION_SURFACE);
        assertEquals(ContainerSurfaceRole.ROW_SURFACE, ContainerSurfaceRole.ROW_SURFACE);
        assertEquals(ContainerSurfaceRole.FIELD_SURFACE, ContainerSurfaceRole.FIELD_SURFACE);
        assertEquals(ContainerSurfaceRole.OVERLAY_SURFACE, ContainerSurfaceRole.OVERLAY_SURFACE);
        assertEquals(ContainerSurfaceRole.FLOATING_SURFACE, ContainerSurfaceRole.FLOATING_SURFACE);
    }

    @Test
    public void containerRegistryShouldMapSectionAndOverlayRoles() {
        assertEquals(ContainerSurfaceRole.SECTION_SURFACE,
                ContainerSurfaceRegistry.resolveSectionRole("account_stats"));
        assertEquals(ContainerSurfaceRole.OVERLAY_SURFACE,
                ContainerSurfaceRegistry.resolveSectionRole("global_status_sheet"));
        assertEquals(ContainerSurfaceRole.FLOATING_SURFACE,
                ContainerSurfaceRegistry.resolveSectionRole("floating_window"));
    }

    @Test
    public void uiPaletteManagerShouldDelegateSurfaceCreationToRegistry() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("ContainerSurfaceRegistry"));
        assertTrue(source.contains("createSurfaceForRole("));
    }
}
