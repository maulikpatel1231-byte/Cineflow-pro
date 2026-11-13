package com.example.camera

import android.opengl.Matrix
import com.example.model.GyroStabilizerStrength
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Pure Kotlin math & filter engine for 3-axis gyro video frame stabilization.
 *
 * Designed to run at high sensor frequency (~200-400Hz) and be sampled per video frame
 * inside the real-time OpenGL SurfaceProcessor (both Preview and VideoCapture streams).
 */
data class StabilizerTransform(
    val txAspect: Float = 0f,
    val tyAspect: Float = 0f,
    val rollDegrees: Float = 0f,
    val overscanScale: Float = 1.0f,
    val shakeCancelledDegrees: Float = 0f
)

class GyroStabilizerFilter {

    @Volatile
    var strength: GyroStabilizerStrength = GyroStabilizerStrength.ACTION

    @Volatile
    var isHorizonLockEnabled: Boolean = true

    @Volatile
    var gainMultiplier: Float = 1.0f

    // High-pass filtered angular offsets (radians) in device coordinates
    private var angleXRad = 0f // pitch
    private var angleYRad = 0f // yaw
    private var angleZRad = 0f // roll
    private var lastGyroTimestampNs = 0L

    // Latest gravity-derived roll (radians) for horizon lock
    private var gravityRollRad = 0f
    private var hasGravityRoll = false

    // Smoothed RMS shake magnitude (degrees) for telemetry
    private var rmsShakeDeg = 0f

    @Synchronized
    fun reset() {
        angleXRad = 0f
        angleYRad = 0f
        angleZRad = 0f
        lastGyroTimestampNs = 0L
        rmsShakeDeg = 0f
    }

    @Synchronized
    fun onGyroSample(gxRadPerSec: Float, gyRadPerSec: Float, gzRadPerSec: Float, timestampNs: Long) {
        val currentStrength = strength
        if (currentStrength == GyroStabilizerStrength.OFF) {
            angleXRad = 0f
            angleYRad = 0f
            angleZRad = 0f
            lastGyroTimestampNs = timestampNs
            rmsShakeDeg = 0f
            return
        }

        if (lastGyroTimestampNs == 0L) {
            lastGyroTimestampNs = timestampNs
            return
        }

        val dtSec = ((timestampNs - lastGyroTimestampNs) * 1e-9f).coerceIn(0.0005f, 0.05f)
        lastGyroTimestampNs = timestampNs

        val decay = exp(-currentStrength.panFollowRateHz * dtSec)
        val rollDecay = exp(-(currentStrength.panFollowRateHz * 0.75f) * dtSec)

        angleXRad = angleXRad * decay + gxRadPerSec * dtSec
        angleYRad = angleYRad * decay + gyRadPerSec * dtSec
        angleZRad = angleZRad * rollDecay + gzRadPerSec * dtSec

        // Bound integrator state to prevent runaway windup on rapid spins
        val maxIntegratorRad = 0.45f
        angleXRad = angleXRad.coerceIn(-maxIntegratorRad, maxIntegratorRad)
        angleYRad = angleYRad.coerceIn(-maxIntegratorRad, maxIntegratorRad)
        angleZRad = angleZRad.coerceIn(-maxIntegratorRad, maxIntegratorRad)

        val magDeg = Math.toDegrees(sqrt((angleXRad * angleXRad + angleYRad * angleYRad + angleZRad * angleZRad).toDouble())).toFloat()
        rmsShakeDeg = rmsShakeDeg * 0.90f + magDeg * 0.10f
    }

    @Synchronized
    fun onRotationRollSample(rollDegrees: Float) {
        gravityRollRad = Math.toRadians(rollDegrees.toDouble()).toFloat()
        hasGravityRoll = true
    }

    /**
     * Computes a safe, zero-black-border stabilization transform in the SurfaceOutput's
     * coordinate frame by inspecting [surfaceTexMatrix] (from SurfaceOutput.updateTransformMatrix).
     */
    @Synchronized
    fun sampleTransformForSurface(
        surfaceWidth: Int,
        surfaceHeight: Int,
        surfaceTexMatrix: FloatArray,
        sensorOrientationDegrees: Int,
        isFrontCamera: Boolean
    ): StabilizerTransform {
        val currentStrength = strength
        if (currentStrength == GyroStabilizerStrength.OFF || surfaceWidth <= 0 || surfaceHeight <= 0) {
            return StabilizerTransform(overscanScale = 1.0f, shakeCancelledDegrees = 0f)
        }

        val aspect = surfaceWidth.toFloat() / surfaceHeight.toFloat()
        val overscan = currentStrength.overscanScale
        val gain = gainMultiplier.coerceIn(0.25f, 1.35f)

        // Effective FOV projection gain: ~1.55 half-screen units per radian (~66 deg horizontal FOV)
        val fovProjectionGain = 1.55f * gain

        // Counter-shift in upright device portrait plane (X right, Y up)
        // For back camera: yaw left (+angleY) moves world right -> counter-shift (-angleY)
        // Pitch up (+angleX) moves world down -> counter-shift (+angleX)
        val rawDevShiftX = (if (isFrontCamera) angleYRad else -angleYRad) * fovProjectionGain
        val rawDevShiftY = (if (isFrontCamera) -angleXRad else angleXRad) * fovProjectionGain

        // Convert upright device shift into OES sensor texture axes via sensorOrientation
        val sensorRad = Math.toRadians(sensorOrientationDegrees.toDouble())
        val cosS = cos(sensorRad).toFloat()
        val sinS = sin(sensorRad).toFloat()

        // Upright worldRight (+X_dev) -> (cosS, sinS) in OES; worldUp (+Y_dev) -> (-sinS, cosS) in OES
        val shiftOesX = rawDevShiftX * cosS - rawDevShiftY * sinS
        val shiftOesY = rawDevShiftX * sinS + rawDevShiftY * cosS

        // Map OES shift to THIS SurfaceOutput's NDC coordinates via the inverse of surfaceTexMatrix's 2x2 UV Jacobian
        // Column 0 of surfaceTexMatrix = (m0, m1), Column 1 = (m4, m5)
        val m0 = surfaceTexMatrix[0]
        val m1 = surfaceTexMatrix[1]
        val m4 = surfaceTexMatrix[4]
        val m5 = surfaceTexMatrix[5]
        val det = m0 * m5 - m1 * m4

        val (rawTxNorm, rawTyNorm, isMirrored) = if (abs(det) > 1e-4f) {
            val invDet = 1.0f / det
            // Normalize Jacobian scale so crop scaling inside surfaceTexMatrix doesn't distort translation sensitivity
            val col0Len = sqrt((m0 * m0 + m1 * m1).toDouble()).toFloat().coerceAtLeast(1e-3f)
            val col1Len = sqrt((m4 * m4 + m5 * m5).toDouble()).toFloat().coerceAtLeast(1e-3f)
            val tx = ((m5 * shiftOesX - m4 * shiftOesY) * invDet) * col0Len
            val ty = ((-m1 * shiftOesX + m0 * shiftOesY) * invDet) * col1Len
            Triple(tx, ty, det < 0f)
        } else {
            Triple(rawDevShiftX, rawDevShiftY, false)
        }

        // Convert normalized translation to aspect-corrected space
        val rawTxAspect = rawTxNorm * aspect
        val rawTyAspect = rawTyNorm

        // Compute counter-roll in degrees (combine fast gyro roll + optional horizon lock)
        val gyroRollDeg = Math.toDegrees(angleZRad.toDouble()).toFloat() * gain
        val rawRollDeg = if (isHorizonLockEnabled && hasGravityRoll) {
            val gravDeg = -Math.toDegrees(gravityRollRad.toDouble()).toFloat()
            (gravDeg * 0.65f + gyroRollDeg * 0.55f)
        } else {
            gyroRollDeg
        }.let { if (isMirrored) -it else it }

        val clamped = clampToSafeBounds(
            rawTxAspect = rawTxAspect,
            rawTyAspect = rawTyAspect,
            rawRollDeg = rawRollDeg,
            overscanScale = overscan,
            aspectRatio = aspect,
            maxRollDeg = currentStrength.rollLockMaxDeg
        )

        return clamped.copy(shakeCancelledDegrees = rmsShakeDeg)
    }

    companion object {
        /**
         * Ensures the transformed overscan quad NEVER leaves any corner of the output viewport
         * uncovered (guaranteeing zero black borders at any aspect ratio or roll angle).
         */
        fun clampToSafeBounds(
            rawTxAspect: Float,
            rawTyAspect: Float,
            rawRollDeg: Float,
            overscanScale: Float,
            aspectRatio: Float,
            maxRollDeg: Float = 12.5f
        ): StabilizerTransform {
            if (overscanScale <= 1.001f || aspectRatio <= 0f) {
                return StabilizerTransform(overscanScale = 1.0f)
            }

            // First find the maximum roll angle that leaves positive translational headroom in BOTH axes
            val safeMaxRoll = findMaxSafeRollDegrees(overscanScale, aspectRatio, maxRollDeg)
            val rollSoft = if (safeMaxRoll > 0.1f) {
                safeMaxRoll * tanh((rawRollDeg / safeMaxRoll).toDouble()).toFloat()
            } else {
                0f
            }.coerceIn(-safeMaxRoll, safeMaxRoll)

            val rollRad = Math.toRadians(rollSoft.toDouble())
            val cosR = abs(cos(rollRad)).toFloat()
            val sinR = abs(sin(rollRad)).toFloat()

            // In rotated frame (u, v):
            // |tx*cosR + ty*sinR| <= s*a - (a*cosR + sinR) = limitU
            // |-tx*sinR + ty*cosR| <= s*1 - (a*sinR + cosR) = limitV
            val limitU = (overscanScale * aspectRatio - (aspectRatio * cosR + sinR)).coerceAtLeast(0f) * 0.96f
            val limitV = (overscanScale - (aspectRatio * sinR + cosR)).coerceAtLeast(0f) * 0.96f

            // Rotate raw translation into the rotated frame, soft-clip with tanh, and rotate back
            val rCos = cos(rollRad).toFloat()
            val rSin = sin(rollRad).toFloat()

            val rawU = rawTxAspect * rCos + rawTyAspect * rSin
            val rawV = -rawTxAspect * rSin + rawTyAspect * rCos

            val clampedU = if (limitU > 1e-4f) {
                (limitU * tanh((rawU / limitU).toDouble()).toFloat()).coerceIn(-limitU, limitU)
            } else {
                0f
            }
            val clampedV = if (limitV > 1e-4f) {
                (limitV * tanh((rawV / limitV).toDouble()).toFloat()).coerceIn(-limitV, limitV)
            } else {
                0f
            }

            val finalTx = clampedU * rCos - clampedV * rSin
            val finalTy = clampedU * rSin + clampedV * rCos

            return StabilizerTransform(
                txAspect = finalTx,
                tyAspect = finalTy,
                rollDegrees = rollSoft,
                overscanScale = overscanScale
            )
        }

        fun findMaxSafeRollDegrees(
            overscanScale: Float,
            aspectRatio: Float,
            requestedMaxDeg: Float
        ): Float {
            var best = 0f
            val step = 0.5f
            var deg = 0.5f
            while (deg <= requestedMaxDeg) {
                val rad = Math.toRadians(deg.toDouble())
                val c = abs(cos(rad)).toFloat()
                val s = abs(sin(rad)).toFloat()
                val marginU = overscanScale * aspectRatio - (aspectRatio * c + s)
                val marginV = overscanScale - (aspectRatio * s + c)
                // Reserve at least 55% of overscan margin for translational X/Y shake cancellation!
                val minReserve = (overscanScale - 1.0f) * 0.55f
                if (marginU >= minReserve * aspectRatio && marginV >= minReserve) {
                    best = deg
                } else {
                    break
                }
                deg += step
            }
            return best
        }

        /**
         * Builds the 4x4 column-major MVP matrix for rendering the NDC [-1, 1] quad with
         * aspect-preserving scale, roll rotation, and translation.
         */
        fun buildMvpMatrix(outMatrix: FloatArray, transform: StabilizerTransform, aspectRatio: Float) {
            Matrix.setIdentityM(outMatrix, 0)
            if (aspectRatio <= 0f || transform.overscanScale <= 1.0001f) {
                return
            }
            // Right-to-left:
            // 1. Scale NDC [-1,1] to [-aspect, aspect] x [-1, 1] * overscan
            // 2. Rotate by rollDegrees in isotropic space
            // 3. Translate by (txAspect, tyAspect)
            // 4. Scale X back by 1/aspect to return to NDC [-1, 1]
            Matrix.scaleM(outMatrix, 0, 1f / aspectRatio, 1f, 1f)
            Matrix.translateM(outMatrix, 0, transform.txAspect, transform.tyAspect, 0f)
            if (abs(transform.rollDegrees) > 0.01f) {
                Matrix.rotateM(outMatrix, 0, transform.rollDegrees, 0f, 0f, 1f)
            }
            Matrix.scaleM(outMatrix, 0, transform.overscanScale * aspectRatio, transform.overscanScale, 1f)
        }
    }
}
