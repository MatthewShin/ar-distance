package com.reodavin.ardistance.geometry

/**
 * 여러 시점(world 좌표계의 카메라 위치 + 그 위치에서 타겟을 바라보는 단위 방향벡터)으로부터
 * 광선들에 가장 가까운 3D 점을 최소자승으로 구한다 (고성능 모드 §다중 시점 삼각측량).
 *
 * 각 광선 i(원점 o_i, 단위방향 d_i)에 대한 점-직선 거리 제곱합을 최소화하면 아래 3x3 선형방정식이 된다:
 *   A = Σ (I - d_i·d_i^T),  b = Σ (I - d_i·d_i^T)·o_i
 *   A·X = b
 * 광선들이 거의 평행(폰을 충분히 움직이지 않음)이면 A가 특이행렬에 가까워져 풀 수 없다.
 */
object RayTriangulator {
    data class Ray(val origin: FloatArray, val direction: FloatArray)

    /** [rays]가 2개 미만이거나, 광선들이 거의 평행해 안정적으로 풀 수 없으면 null. */
    fun triangulate(rays: List<Ray>): FloatArray? {
        if (rays.size < 2) return null

        // A(3x3)를 1차원 배열(row-major)로, b(3)를 누적한다.
        val a = DoubleArray(9)
        val b = DoubleArray(3)

        for (ray in rays) {
            val d = normalize(ray.direction) ?: continue
            val o = ray.origin

            // m = I - d·d^T
            val m00 = 1.0 - d[0] * d[0]
            val m01 = -d[0] * d[1]
            val m02 = -d[0] * d[2]
            val m11 = 1.0 - d[1] * d[1]
            val m12 = -d[1] * d[2]
            val m22 = 1.0 - d[2] * d[2]

            a[0] += m00; a[1] += m01; a[2] += m02
            a[3] += m01; a[4] += m11; a[5] += m12
            a[6] += m02; a[7] += m12; a[8] += m22

            b[0] += m00 * o[0] + m01 * o[1] + m02 * o[2]
            b[1] += m01 * o[0] + m11 * o[1] + m12 * o[2]
            b[2] += m02 * o[0] + m12 * o[1] + m22 * o[2]
        }

        val x = solve3x3(a, b) ?: return null
        return floatArrayOf(x[0].toFloat(), x[1].toFloat(), x[2].toFloat())
    }

    private fun normalize(v: FloatArray): FloatArray? {
        val len = kotlin.math.sqrt((v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).toDouble())
        if (len < 1e-9) return null
        return floatArrayOf((v[0] / len).toFloat(), (v[1] / len).toFloat(), (v[2] / len).toFloat())
    }

    /** 가우스 소거(부분 피벗팅)로 3x3 연립방정식 A·x = b를 푼다. [a]는 row-major 9원소. */
    private fun solve3x3(a: DoubleArray, b: DoubleArray): DoubleArray? {
        // 증강행렬 [3][4]로 재구성.
        val m = Array(3) { r -> DoubleArray(4).also { row ->
            row[0] = a[r * 3]; row[1] = a[r * 3 + 1]; row[2] = a[r * 3 + 2]; row[3] = b[r]
        } }

        for (col in 0 until 3) {
            var pivotRow = col
            for (r in col + 1 until 3) {
                if (kotlin.math.abs(m[r][col]) > kotlin.math.abs(m[pivotRow][col])) pivotRow = r
            }
            if (kotlin.math.abs(m[pivotRow][col]) < 1e-9) return null // 특이행렬 — 광선이 거의 평행
            val tmp = m[col]; m[col] = m[pivotRow]; m[pivotRow] = tmp

            for (r in 0 until 3) {
                if (r == col) continue
                val factor = m[r][col] / m[col][col]
                for (c2 in col..3) {
                    m[r][c2] -= factor * m[col][c2]
                }
            }
        }

        return doubleArrayOf(m[0][3] / m[0][0], m[1][3] / m[1][1], m[2][3] / m[2][2])
    }
}
