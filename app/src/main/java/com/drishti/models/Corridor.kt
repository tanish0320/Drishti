package com.drishti.models

import android.graphics.PointF
import android.graphics.RectF

data class Corridor(
    val topWidth: Float,
    val bottomWidth: Float,
    val startY: Float,
    val endY: Float
) {
    // Vertices of the trapezoid in model coordinate space (640x640)
    val topLeft = PointF(320f - topWidth / 2f, startY)
    val topRight = PointF(320f + topWidth / 2f, startY)
    val bottomRight = PointF(320f + bottomWidth / 2f, endY)
    val bottomLeft = PointF(320f - bottomWidth / 2f, endY)

    /**
     * Checks if a point (x, y) is inside this perspective corridor trapezoid.
     */
    fun containsPoint(x: Float, y: Float): Boolean {
        if (y < startY || y > endY) return false
        val fraction = (y - startY) / (endY - startY)
        val leftBound = topLeft.x + (bottomLeft.x - topLeft.x) * fraction
        val rightBound = topRight.x + (bottomRight.x - topRight.x) * fraction
        return x in leftBound..rightBound
    }

    /**
     * Checks if a bounding box intersects the corridor.
     * It intersects if its center lies inside, OR if a minimum percentage 
     * of its 9-point grid samples lies inside the corridor.
     */
    fun intersects(rect: RectF, minOverlapPercent: Float): Boolean {
        // Quick path: if center is inside, count as intersecting
        if (containsPoint(rect.centerX(), rect.centerY())) return true

        var pointsInside = 0
        val left = rect.left
        val centerX = rect.centerX()
        val right = rect.right
        val top = rect.top
        val centerY = rect.centerY()
        val bottom = rect.bottom

        // Sample 9 grid points to estimate overlay area without allocating heap lists
        if (containsPoint(left, top)) pointsInside++
        if (containsPoint(centerX, top)) pointsInside++
        if (containsPoint(right, top)) pointsInside++
        if (containsPoint(left, centerY)) pointsInside++
        if (containsPoint(centerX, centerY)) pointsInside++
        if (containsPoint(right, centerY)) pointsInside++
        if (containsPoint(left, bottom)) pointsInside++
        if (containsPoint(centerX, bottom)) pointsInside++
        if (containsPoint(right, bottom)) pointsInside++

        val overlapFraction = pointsInside.toFloat() / 9f
        return overlapFraction >= (minOverlapPercent / 100f)
    }
}
