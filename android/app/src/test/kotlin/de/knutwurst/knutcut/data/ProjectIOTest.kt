package de.knutwurst.knutcut.data

import de.knutwurst.knutcut.svgcore.Polyline
import de.knutwurst.knutcut.svgcore.Pt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Project save/load round-trips the layer arrangement. Robolectric because ProjectIO uses org.json. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProjectIOTest {

    @Test
    fun roundTripPreservesPlacementGeometryAndColour() {
        val layers = listOf(
            Layer(
                name = "Stern",
                polylines = listOf(
                    Polyline(listOf(Pt(0.0, 0.0), Pt(10.0, 0.0), Pt(10.0, 5.0)), closed = true),
                    Polyline(listOf(Pt(1.0, 1.0), Pt(2.0, 2.0)), closed = false),
                ),
                tool = Tool.KNIFE,
                visible = false,
                centerMm = Pt(42.0, 17.5),
                scaleX = 1.5, scaleY = 0.75,
                rotationDeg = 90.0,
                flipX = true, flipY = false,
                colorArgb = 0xFF112233.toInt(),
            ),
            Layer("Pfad", listOf(Polyline(listOf(Pt(3.0, 3.0), Pt(4.0, 4.0)), false)), Tool.PEN, true),
        )

        val back = ProjectIO.fromJson(ProjectIO.toJson(layers))

        assertEquals(2, back.size)
        val a = back[0]
        assertEquals("Stern", a.name)
        assertEquals(Tool.KNIFE, a.tool)
        assertEquals(false, a.visible)
        assertEquals(Pt(42.0, 17.5), a.centerMm)
        assertEquals(1.5, a.scaleX, 1e-9)
        assertEquals(0.75, a.scaleY, 1e-9)
        assertEquals(90.0, a.rotationDeg, 1e-9)
        assertEquals(true, a.flipX)
        assertEquals(false, a.flipY)
        assertEquals(0xFF112233.toInt(), a.colorArgb)
        // Geometry survives exactly (Pt/Polyline are value types).
        assertEquals(layers[0].polylines, a.polylines)
        assertEquals(Tool.PEN, back[1].tool)
        assertEquals(layers[1].polylines, back[1].polylines)
    }

    @Test
    fun emptyProjectLoadsToNoLayers() {
        assertTrue(ProjectIO.fromJson("[]").isEmpty())
    }

    @Test
    fun dropsDegeneratePolylines() {
        // A one-point polyline isn't a path; it must not survive a round-trip as a layer.
        val layers = listOf(Layer("x", listOf(Polyline(listOf(Pt(0.0, 0.0)), false)), Tool.PEN, true))
        assertTrue(ProjectIO.fromJson(ProjectIO.toJson(layers)).isEmpty())
    }

    @Test
    fun textSpecRoundTrip() {
        val poly = listOf(Polyline(listOf(Pt(0.0, 0.0), Pt(5.0, 5.0)), closed = false))
        val spec = TextSpec(text = "Hallo", fontIndex = 2, heightMm = 30.0, curve = 42)
        val layer = Layer("Text: Hallo", poly, Tool.PEN, visible = true, textSpec = spec)

        val back = ProjectIO.fromJson(ProjectIO.toJson(listOf(layer)))[0]

        assertEquals("Hallo", back.textSpec?.text)
        assertEquals(2, back.textSpec?.fontIndex)
        assertEquals(30.0, back.textSpec?.heightMm ?: 0.0, 1e-9)
        assertEquals(42, back.textSpec?.curve)
    }

    @Test
    fun textSpecBackCompatAbsentMeansNull() {
        // A project file without "textSpec" must load with textSpec == null (old files stay valid).
        val json = """[{"name":"Kreis","tool":"KNIFE","visible":true,"cx":0.0,"cy":0.0,
            "sx":1.0,"sy":1.0,"rot":0.0,"fx":false,"fy":false,
            "polys":[{"c":false,"p":[[0.0,0.0],[1.0,1.0]]}]}]"""
        val layer = ProjectIO.fromJson(json)[0]
        assertNull("absent textSpec must load as null", layer.textSpec)
    }
}
