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
            l.deform?.let { o.put("deform", serializeDeform(it)) }
            l.deformSource?.let { src ->
                val srcArr = JSONArray()
                src.forEach { pl ->
                    val pts = JSONArray()
                    pl.points.forEach { pts.put(JSONArray().put(it.xMm).put(it.yMm)) }
                    srcArr.put(JSONObject().put("c", pl.closed).put("p", pts))
                }
                o.put("deformSrc", srcArr)
            }
            l.editPath?.let { o.put("editPath", serializeEditPath(it)) }
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
                    points.add(Pt(pr.optDouble(0), pr.optDouble(1)))
                }
                if (points.size >= 2) polys.add(Polyline(points, po.optBoolean("c")))
            }
            if (polys.isEmpty()) continue
            val pcolors = o.optJSONArray("pcolors")?.let { pc ->
                (0 until pc.length()).map { if (pc.isNull(it)) null else pc.optInt(it) }
            }
            val deform = o.optJSONObject("deform")?.let { deserializeDeform(it) }
            val deformSrc = o.optJSONArray("deformSrc")?.let { srcArr ->
                val src = ArrayList<Polyline>()
                for (j in 0 until srcArr.length()) {
                    val po = srcArr.optJSONObject(j) ?: continue
                    val pts = po.optJSONArray("p") ?: JSONArray()
                    val points = ArrayList<Pt>(pts.length())
                    for (k in 0 until pts.length()) {
                        val pr = pts.optJSONArray(k) ?: continue
                        points.add(Pt(pr.optDouble(0), pr.optDouble(1)))
                    }
                    if (points.size >= 2) src.add(Polyline(points, po.optBoolean("c")))
                }
                src.takeIf { it.isNotEmpty() }
            }
            val editPath = o.optJSONObject("editPath")?.let { deserializeEditPath(it) }
            // Enforce invariant: deform is non-null iff deformSource is non-null. A file written with
            // a deform but without a deformSrc (e.g. a degenerate 1-point source that was dropped on
            // load) would leave the layer in a broken state where the next warp double-applies.
            val safeDeform = if (deform != null && deformSrc != null) deform else null
            val safeDeformSrc = if (deform != null && deformSrc != null) deformSrc else null
            out.add(Layer(
                name = o.optString("name", "Ebene"),
                polylines = polys,
                tool = runCatching { Tool.valueOf(o.optString("tool")) }.getOrDefault(Tool.KNIFE),
                visible = o.optBoolean("visible", true),
                centerMm = Pt(o.optDouble("cx"), o.optDouble("cy")),
                scaleX = o.optDouble("sx", 1.0), scaleY = o.optDouble("sy", 1.0),
                rotationDeg = o.optDouble("rot", 0.0),
                flipX = o.optBoolean("fx"), flipY = o.optBoolean("fy"),
                colorArgb = if (o.has("color")) o.optInt("color") else null,
                polylineColors = pcolors,
                deform = safeDeform,
                deformSource = safeDeformSrc,
                editPath = editPath,
            ))
        }
        return out
    }

    // ------------------------------------------------------------------
    // Deform serialisation helpers
    // ------------------------------------------------------------------

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
            nodes.put(n)
        }
        return JSONObject().put("nodes", nodes).put("closed", path.closed)
    }

    private fun deserializeEditPath(o: JSONObject): EditablePath? {
        val nodesArr = o.optJSONArray("nodes") ?: return null
        val nodes = ArrayList<PathNode>(nodesArr.length())
        for (i in 0 until nodesArr.length()) {
            val n = nodesArr.optJSONObject(i) ?: continue
            val anchor = Pt(n.optDouble("ax"), n.optDouble("ay"))
            val handleIn  = if (n.has("inx"))  Pt(n.optDouble("inx"),  n.optDouble("iny"))  else null
            val handleOut = if (n.has("outx")) Pt(n.optDouble("outx"), n.optDouble("outy")) else null
            nodes.add(PathNode(anchor, handleIn, handleOut))
        }
        if (nodes.isEmpty()) return null
        return EditablePath(nodes, o.optBoolean("closed", false))
    }

    private fun serializeDeform(spec: DeformSpec): JSONObject = when (spec) {
        is CircleDeform -> JSONObject().apply {
            put("type", "circle")
            put("cx", spec.centerXMm)
            put("cy", spec.centerYMm)
            put("r", spec.radiusMm)
            put("start", spec.startAngleDeg)
            put("cw", spec.clockwise)
            put("base", spec.baseline.name)
        }
        is PathDeform -> JSONObject().apply {
            put("type", "path")
            put("closed", spec.closed)
            put("base", spec.baseline.name)
            val nodes = JSONArray()
            spec.guide.forEach { node ->
                val n = JSONObject()
                n.put("ax", node.anchor.xMm)
                n.put("ay", node.anchor.yMm)
                node.handleIn?.let  { n.put("inx",  it.xMm); n.put("iny",  it.yMm) }
                node.handleOut?.let { n.put("outx", it.xMm); n.put("outy", it.yMm) }
                nodes.put(n)
            }
            put("nodes", nodes)
        }
        is EnvelopeDeform -> JSONObject().apply {
            put("type", "envelope")
            put("tl", JSONObject().put("x", spec.tl.xMm).put("y", spec.tl.yMm))
            put("tr", JSONObject().put("x", spec.tr.xMm).put("y", spec.tr.yMm))
            put("br", JSONObject().put("x", spec.br.xMm).put("y", spec.br.yMm))
            put("bl", JSONObject().put("x", spec.bl.xMm).put("y", spec.bl.yMm))
        }
    }

    private fun deserializePathDeform(o: JSONObject): PathDeform? {
        val nodesArr = o.optJSONArray("nodes") ?: return null
        val nodes = ArrayList<PathNode>(nodesArr.length())
        for (i in 0 until nodesArr.length()) {
            val n = nodesArr.optJSONObject(i) ?: continue
            val anchor = Pt(n.optDouble("ax"), n.optDouble("ay"))
            val handleIn  = if (n.has("inx"))  Pt(n.optDouble("inx"),  n.optDouble("iny"))  else null
            val handleOut = if (n.has("outx")) Pt(n.optDouble("outx"), n.optDouble("outy")) else null
            nodes.add(PathNode(anchor, handleIn, handleOut))
        }
        if (nodes.isEmpty()) return null
        return PathDeform(
            guide = nodes,
            closed = o.optBoolean("closed", false),
            baseline = runCatching { DeformBaseline.valueOf(o.optString("base")) }
                .getOrDefault(DeformBaseline.CENTER),
        )
    }

    private fun deserializeDeform(o: JSONObject): DeformSpec? = when (o.optString("type")) {
        "circle" -> CircleDeform(
            centerXMm = o.optDouble("cx", 0.0),
            centerYMm = o.optDouble("cy", 0.0),
            radiusMm = o.optDouble("r", 50.0),
            startAngleDeg = o.optDouble("start", 0.0),
            clockwise = o.optBoolean("cw", false),
            baseline = runCatching { DeformBaseline.valueOf(o.optString("base")) }
                .getOrDefault(DeformBaseline.BOTTOM),
        )
        "path" -> deserializePathDeform(o)
        "envelope" -> deserializeEnvelopeDeform(o)
        else -> null
    }

    private fun readPt(parent: JSONObject, key: String): Pt? {
        val obj = parent.optJSONObject(key) ?: return null
        return Pt(obj.optDouble("x", 0.0), obj.optDouble("y", 0.0))
    }

    private fun deserializeEnvelopeDeform(o: JSONObject): EnvelopeDeform? {
        val tl = readPt(o, "tl") ?: return null
        val tr = readPt(o, "tr") ?: return null
        val br = readPt(o, "br") ?: return null
        val bl = readPt(o, "bl") ?: return null
        return EnvelopeDeform(tl, tr, br, bl)
    }
}
