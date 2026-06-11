package de.knutwurst.knutcut.data

import de.knutwurst.knutcut.svgcore.EditablePath
import de.knutwurst.knutcut.svgcore.PathNode
import de.knutwurst.knutcut.svgcore.Polyline
import de.knutwurst.knutcut.svgcore.Pt
import org.json.JSONArray
import org.json.JSONObject

/** Serialises the layer list (placement + geometry + colour) to/from JSON so a project can be saved
 *  and reopened. Plain org.json, no new dependency — mirrors the custom-materials store in [Settings]. */
object ProjectIO {

    fun toJson(layers: List<Layer>): String {
        val arr = JSONArray()
        layers.forEach { l ->
            val o = JSONObject()
            o.put("name", l.name); o.put("tool", l.tool.name); o.put("visible", l.visible)
            o.put("cx", l.centerMm.xMm); o.put("cy", l.centerMm.yMm)
            o.put("sx", l.scaleX); o.put("sy", l.scaleY); o.put("rot", l.rotationDeg)
            o.put("fx", l.flipX); o.put("fy", l.flipY)
            l.colorArgb?.let { o.put("color", it) }
            l.polylineColors?.let { pc -> o.put("pcolors", JSONArray().apply { pc.forEach { put(it ?: JSONObject.NULL) } }) }
            val pls = JSONArray()
            l.polylines.forEach { pl ->
                val pts = JSONArray()
                pl.points.forEach { pts.put(JSONArray().put(it.xMm).put(it.yMm)) }
                pls.put(JSONObject().put("c", pl.closed).put("p", pts))
            }
            o.put("polys", pls)
            l.editPath?.let { o.put("editPath", serializeEditPath(it)) }
            l.editOriginMm?.let { o.put("editOrigin", JSONArray().put(it.xMm).put(it.yMm)) }
            l.textSpec?.let { ts ->
                o.put("textSpec", JSONObject()
                    .put("text", ts.text)
                    .put("font", ts.fontIndex)
                    .put("h", ts.heightMm)
                    .put("curve", ts.curve))
            }
            arr.put(o)
        }
        return arr.toString()
    }

    fun fromJson(s: String): List<Layer> {
        val arr = JSONArray(s)
        val out = ArrayList<Layer>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val polys = ArrayList<Polyline>()
            val pls = o.optJSONArray("polys") ?: JSONArray()
            for (j in 0 until pls.length()) {
                val po = pls.optJSONObject(j) ?: continue
                val pts = po.optJSONArray("p") ?: JSONArray()
                val points = ArrayList<Pt>(pts.length())
                for (k in 0 until pts.length()) {
                    val pr = pts.optJSONArray(k) ?: continue
                    // Drop non-finite coordinates (missing/NaN from a corrupt file) rather than letting
                    // them poison the geometry.
                    val p = finitePt(pr.optDouble(0), pr.optDouble(1)) ?: continue
                    points.add(p)
                }
                if (points.size >= 2) polys.add(Polyline(points, po.optBoolean("c")))
            }
            if (polys.isEmpty()) continue
            val pcolors = o.optJSONArray("pcolors")?.let { pc ->
                (0 until pc.length()).map { if (pc.isNull(it)) null else pc.optInt(it) }
            }
            // A non-finite centre means a broken layer — discard it rather than place geometry at NaN.
            val center = finitePt(o.optDouble("cx"), o.optDouble("cy")) ?: continue
            val editPath = o.optJSONObject("editPath")?.let { deserializeEditPath(it) }
            // Frozen edit pivot; only meaningful alongside an editPath. Legacy files lack it.
            val editOrigin = o.optJSONArray("editOrigin")
                ?.takeIf { editPath != null && it.length() == 2 }
                ?.let { finitePt(it.optDouble(0), it.optDouble(1)) }
            val textSpec = o.optJSONObject("textSpec")?.let { ts ->
                TextSpec(
                    text = ts.optString("text", ""),
                    fontIndex = ts.optInt("font", 0),
                    heightMm = ts.optDouble("h", 25.0).let { if (it.isFinite() && it > 0) it else 25.0 },
                    curve = ts.optInt("curve", 0),
                )
            }
            out.add(Layer(
                name = o.optString("name", "Ebene"),
                polylines = polys,
                tool = runCatching { Tool.valueOf(o.optString("tool")) }.getOrDefault(Tool.KNIFE),
                visible = o.optBoolean("visible", true),
                centerMm = center,
                scaleX = finiteOr(o.optDouble("sx", 1.0), 1.0), scaleY = finiteOr(o.optDouble("sy", 1.0), 1.0),
                rotationDeg = finiteOr(o.optDouble("rot", 0.0), 0.0),
                flipX = o.optBoolean("fx"), flipY = o.optBoolean("fy"),
                colorArgb = if (o.has("color")) o.optInt("color") else null,
                polylineColors = pcolors,
                editPath = editPath,
                editOriginMm = editOrigin,
                textSpec = textSpec,
            ))
        }
        return out
    }

    /** A [Pt] only when both coordinates are finite (guards against NaN/∞ from a corrupt file). */
    private fun finitePt(x: Double, y: Double): Pt? = if (x.isFinite() && y.isFinite()) Pt(x, y) else null

    /** [v] when finite, else [fallback]. */
    private fun finiteOr(v: Double, fallback: Double): Double = if (v.isFinite()) v else fallback

    // ------------------------------------------------------------------
    // EditablePath serialisation helpers (for drawn layers)
    // ------------------------------------------------------------------

    private fun serializeEditPath(path: EditablePath): JSONObject {
        val nodes = JSONArray()
        path.nodes.forEach { node ->
            val n = JSONObject()
            n.put("ax", node.anchor.xMm)
            n.put("ay", node.anchor.yMm)
            node.handleIn?.let  { n.put("inx",  it.xMm); n.put("iny",  it.yMm) }
            node.handleOut?.let { n.put("outx", it.xMm); n.put("outy", it.yMm) }
            if (node.smooth) n.put("sm", true)
            nodes.put(n)
        }
        return JSONObject().put("nodes", nodes).put("closed", path.closed)
    }

    private fun deserializeEditPath(o: JSONObject): EditablePath? {
        val nodesArr = o.optJSONArray("nodes") ?: return null
        val nodes = ArrayList<PathNode>(nodesArr.length())
        for (i in 0 until nodesArr.length()) {
            val n = nodesArr.optJSONObject(i) ?: continue
            val anchor = finitePt(n.optDouble("ax"), n.optDouble("ay")) ?: continue
            val handleIn  = if (n.has("inx"))  finitePt(n.optDouble("inx"),  n.optDouble("iny"))  else null
            val handleOut = if (n.has("outx")) finitePt(n.optDouble("outx"), n.optDouble("outy")) else null
            nodes.add(PathNode(anchor, handleIn, handleOut, smooth = n.optBoolean("sm", false)))
        }
        if (nodes.isEmpty()) return null
        return EditablePath(nodes, o.optBoolean("closed", false))
    }
}
