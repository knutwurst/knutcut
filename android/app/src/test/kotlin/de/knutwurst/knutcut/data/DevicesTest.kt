package de.knutwurst.knutcut.data

import de.knutwurst.knutcut.svgcore.LinkTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the separation between the VEVOR (SPP/HPGL) and Silhouette (BLE/GPGL) stacks. The two must
 * never cross: a VEVOR name must not match the BLE filter, a Silhouette name must not match the
 * classic filter, and a family's protocol must only ever run over its own transport. These are the
 * pure pieces the cut() dispatch relies on, so locking them down here keeps the old VEVOR path from
 * silently breaking when Silhouette support evolves.
 */
class DevicesTest {

    // ── Backwards compatibility: the existing VEVOR catalog is unchanged ──────────────────────

    @Test
    fun vevorCatalogIsUnchanged() {
        assertEquals(
            listOf("Smart1", "Smart2", "Smart3", "Smart4"),
            Devices.vevorModels.map { it.name },
        )
        // The default stays the first VEVOR model, so existing installs behave exactly as before.
        assertEquals("Smart1", Devices.default.name)
        assertEquals(PlotterFamily.SPP_VEVOR, Devices.default.family)
    }

    @Test
    fun vevorModelsAreSppWithoutSilhouetteProfile() {
        Devices.vevorModels.forEach { m ->
            assertEquals("${m.name} must be SPP_VEVOR", PlotterFamily.SPP_VEVOR, m.family)
            assertNull("${m.name} must not carry a Silhouette profile", m.silhouetteDevice)
        }
    }

    @Test
    fun silhouetteModelsAreBleWithProfile() {
        assertTrue("expected Silhouette models", Devices.silhouetteModels.isNotEmpty())
        Devices.silhouetteModels.forEach { m ->
            assertEquals("${m.name} must be BLE_SILHOUETTE", PlotterFamily.BLE_SILHOUETTE, m.family)
            assertNotNull("${m.name} must carry a Silhouette profile", m.silhouetteDevice)
        }
    }

    // ── The two model groups partition the catalog with no overlap ────────────────────────────

    @Test
    fun vevorAndSilhouetteModelsPartitionTheCatalogWithoutOverlap() {
        val vevorIds = Devices.vevorModels.map { it.modelId }.toSet()
        val silIds = Devices.silhouetteModels.map { it.modelId }.toSet()
        // No model is in both groups.
        assertTrue("model groups overlap", vevorIds.intersect(silIds).isEmpty())
        // Together they cover every model exactly once.
        assertEquals(Devices.models.size, vevorIds.size + silIds.size)
        assertEquals(Devices.models.map { it.modelId }.toSet(), vevorIds + silIds)
    }

    @Test
    fun byIdResolvesAndFallsBackToDefault() {
        assertEquals("Smart3", Devices.byId(3).name)
        assertEquals("Cameo 3", Devices.byId(10).silhouetteDevice?.name)
        assertEquals(Devices.default.modelId, Devices.byId(9999).modelId)
    }

    // ── Name filters are disjoint: no device is ever both VEVOR and Silhouette ────────────────

    @Test
    fun vevorNameFilterIsUnchanged() {
        // Stock-app tokens "VEVOR"/"Smart" — kept exactly as the original behavior.
        assertTrue(Devices.isCompatible("Smart-BFA8"))
        assertTrue(Devices.isCompatible("Smart1"))
        assertTrue(Devices.isCompatible("VEVOR Cutter"))
        assertTrue(Devices.isCompatible("vevor"))
        assertFalse(Devices.isCompatible("Some Laptop"))
        assertFalse(Devices.isCompatible(null))
    }

    @Test
    fun silhouetteNameFilterMatchesOnlySilhouette() {
        assertTrue(Devices.isCompatibleLe("Silhouette Cameo3"))
        assertTrue(Devices.isCompatibleLe("silhouette portrait"))
        assertFalse(Devices.isCompatibleLe("Smart-BFA8"))
        assertFalse(Devices.isCompatibleLe(null))
    }

    @Test
    fun vevorNamesNeverMatchTheBleFilterAndViceVersa() {
        // A classic VEVOR name must not be picked up by the BLE scanner…
        listOf("Smart-BFA8", "Smart1", "VEVOR Cutter").forEach {
            assertTrue(Devices.isCompatible(it))
            assertFalse("$it leaked into the BLE filter", Devices.isCompatibleLe(it))
        }
        // …and a Silhouette name must not be picked up by the classic VEVOR filter.
        listOf("Silhouette Cameo3", "Silhouette Portrait").forEach {
            assertTrue(Devices.isCompatibleLe(it))
            assertFalse("$it leaked into the VEVOR filter", Devices.isCompatible(it))
        }
    }

    // ── A family's protocol only ever runs over its own transport ─────────────────────────────

    @Test
    fun familyMapsToExactlyOneTransport() {
        assertEquals(LinkTransport.SPP, PlotterFamily.SPP_VEVOR.transport)
        assertEquals(LinkTransport.BLE, PlotterFamily.BLE_SILHOUETTE.transport)
    }

    @Test
    fun vevorMatchesOnlySppAndSilhouetteOnlyBle() {
        assertTrue(PlotterFamily.SPP_VEVOR.matches(LinkTransport.SPP))
        assertFalse("VEVOR must not run over BLE", PlotterFamily.SPP_VEVOR.matches(LinkTransport.BLE))

        assertTrue(PlotterFamily.BLE_SILHOUETTE.matches(LinkTransport.BLE))
        assertFalse("Silhouette must not run over SPP", PlotterFamily.BLE_SILHOUETTE.matches(LinkTransport.SPP))
    }

    @Test
    fun everyModelOnlyMatchesItsOwnTransport() {
        Devices.models.forEach { m ->
            val ownTransport = m.family.transport
            LinkTransport.values().forEach { t ->
                assertEquals(
                    "${m.name} on $t",
                    t == ownTransport,
                    m.family.matches(t),
                )
            }
        }
    }
}
