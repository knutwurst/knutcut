package de.knutwurst.knutcut.data

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
            o.put("polys", pls); arr.put(o)
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
            ))
        }
        return out
    }
}
