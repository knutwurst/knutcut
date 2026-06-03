package de.knutwurst.knutcut.svgcore

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * 2D affine transform written as SVG's matrix(a,b,c,d,e,f):
 *   x' = a*x + c*y + e
 *   y' = b*x + d*y + f
 */
data class Matrix(
    val a: Double, val b: Double, val c: Double,
    val d: Double, val e: Double, val f: Double,
) {
    fun apply(x: Double, y: Double): Pt = Pt(a * x + c * y + e, b * x + d * y + f)
    fun apply(p: Pt): Pt = apply(p.xMm, p.yMm)

    /** Composition: (this * other) applies other first, then this. */
    operator fun times(o: Matrix): Matrix = Matrix(
        a * o.a + c * o.b,
        b * o.a + d * o.b,
        a * o.c + c * o.d,
        b * o.c + d * o.d,
        a * o.e + c * o.f + e,
        b * o.e + d * o.f + f,
    )

    companion object {
        val IDENTITY = Matrix(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
        fun translate(tx: Double, ty: Double) = Matrix(1.0, 0.0, 0.0, 1.0, tx, ty)
        fun scale(sx: Double, sy: Double) = Matrix(sx, 0.0, 0.0, sy, 0.0, 0.0)
        fun rotate(deg: Double): Matrix {
            val r = Math.toRadians(deg)
            val cs = cos(r); val sn = sin(r)
            return Matrix(cs, sn, -sn, cs, 0.0, 0.0)
        }

        /** Parse an SVG transform attribute. Supports chained translate/scale/rotate/skew/matrix. */
        fun parse(s: String?): Matrix {
            if (s.isNullOrBlank()) return IDENTITY
            var m = IDENTITY
            val re = Regex("(matrix|translate|scale|rotate|skewX|skewY)\\s*\\(([^)]*)\\)")
            for (mt in re.findAll(s)) {
                val name = mt.groupValues[1]
                val args = mt.groupValues[2].trim()
                    .split(Regex("[\\s,]+")).filter { it.isNotEmpty() }.map { it.toDouble() }
                val t = when (name) {
                    "matrix" -> Matrix(args[0], args[1], args[2], args[3], args[4], args[5])
                    "translate" -> translate(args[0], if (args.size > 1) args[1] else 0.0)
                    "scale" -> scale(args[0], if (args.size > 1) args[1] else args[0])
                    "rotate" ->
                        if (args.size >= 3) translate(args[1], args[2]) * rotate(args[0]) * translate(-args[1], -args[2])
                        else rotate(args[0])
                    "skewX" -> Matrix(1.0, 0.0, tan(Math.toRadians(args[0])), 1.0, 0.0, 0.0)
                    "skewY" -> Matrix(1.0, tan(Math.toRadians(args[0])), 0.0, 1.0, 0.0, 0.0)
                    else -> IDENTITY
                }
                m *= t
            }
            return m
        }
    }
}
