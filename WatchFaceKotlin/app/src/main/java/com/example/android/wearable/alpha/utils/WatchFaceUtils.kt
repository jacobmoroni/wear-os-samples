package com.example.android.wearable.alpha.utils

import android.content.res.AssetManager
import android.graphics.Path
import java.time.ZonedDateTime
import kotlin.math.*

fun calculateSunriseAndSunset(zdt: ZonedDateTime,
                              lat: Float,
                              lon: Float,
                              sunrise: Boolean): Float {
    /*
    localOffset will be <0 for western hemisphere and >0 for eastern hemisphere
    */
    val zenith = -0.83f

    //1. first calculate the day of the year
    val n1 = floor(275f * zdt.monthValue / 9f)
    val n2 = floor((zdt.monthValue + 9f) / 12f)
    val n3 = (1f + floor((zdt.year - 4f * floor(zdt.year / 4f) + 2f) / 3f))
    val dayOfYear = n1 - (n2 * n3) + zdt.dayOfMonth - 30f

    //2. convert the longitude to hour value and calculate an approximate time
    val lonHour = lon / 15.0f
    val t = if (sunrise) {
        dayOfYear + ((6f - lonHour) / 24f)   //if rising time is desired:
    } else {
        dayOfYear + ((18f - lonHour) / 24f)   //if setting time is desired:
    }

    //3. calculate the Sun's mean anomaly
    val meanAnomaly = (0.9856f * t) - 3.289f

    //4. calculate the Sun's true longitude
    val trueLon = (meanAnomaly + (1.916f * sin((Math.PI / 180f) * meanAnomaly)) +
        (0.020f * sin(2f * (Math.PI / 180f) * meanAnomaly)) + 282.634f).mod(360.0f)

    //5a. calculate the Sun's right ascension
    var rightAscension =
        (180f / Math.PI * atan(0.91764f * tan((Math.PI / 180f) * trueLon))).mod(360.0f)

    //5b. right ascension value needs to be in the same quadrant as trueLon
    val trueLonQuadrant = floor(trueLon / 90f) * 90f
    val rightAscensionQuadrant = floor(rightAscension / 90f) * 90f
    rightAscension += (trueLonQuadrant - rightAscensionQuadrant)

    //5c. right ascension value needs to be converted into hours
    rightAscension /= 15f

    //6. calculate the Sun's declination
    val sinDec = 0.39782f * sin((Math.PI / 180f) * trueLon)
    val cosDec = cos(asin(sinDec))

    //7a. calculate the Sun's local hour angle
    val cosH = (sin((Math.PI / 180f) * zenith) - (sinDec * sin((Math.PI / 180f) * lat))) /
        (cosDec * cos((Math.PI / 180f) * lat))
    /*
    if (cosH >  1)
    the sun never rises on this location (on the specified date)
    if (cosH < -1)
    the sun never sets on this location (on the specified date)
    */

    //7b. finish calculating local hour angle and convert into hours
    var localHourAngle = if (sunrise) {
        360f - (180f / Math.PI) * acos(cosH) // if rising time is desired:
    } else {
        (180f / Math.PI) * acos(cosH) // if setting time is desired:
    }
    localHourAngle /= 15f

    //8. calculate local mean time of rising/setting
    val localMeanTime = localHourAngle + rightAscension - (0.06571f * t) - 6.622f

    //9. adjust back to UTC
    val utcTime = (localMeanTime - lonHour).mod(24.0f)

    //10. convert UT value to local time zone of latitude/longitude
    val localOffset = zdt.offset.totalSeconds / 60f / 60f
    val localTime = (utcTime + localOffset + 24.0f).mod(24.0f)
    return localTime.toFloat()
}

fun wrapAngle(angle: Double): Double {
    return angle - 360.0 * floor(angle / 360.0)
}

fun kepler(m: Double, ecc: Double): Double {
    //Solve the equation of Kepler.
    var mLocal = m
    val epsilon = 1e-6f
    mLocal = mLocal * Math.PI.toFloat() / 180
    var e = mLocal
    while (true) {
        val delta = e - ecc * sin(e) - mLocal
        e -= (delta / (1.0 - ecc * cos(e)))
        if (abs(delta) <= epsilon) {
            break
        }
    }
    return e
}

fun calculateMoonPhase(zdt: ZonedDateTime): Float {
    //JDN stands for Julian Day Number
    // might need this in next line: - zdt.offset.totalSeconds /3600.0f
    val daySegment =
        ((zdt.hour + (zdt.minute / 60.0) + (zdt.second / 3600.0)
            - zdt.offset.totalSeconds / 3600.0f) / 24.0)
    val a = ((zdt.monthValue + 9).toDouble() / 12.0).toInt()
    val b = (7 * (zdt.year + a).toDouble() / 4.0).toInt()
    val c = ((275 * (zdt.monthValue)).toDouble() / 9.0).toInt()
    val julianDatetime =
        (367 * (zdt.year) - b + c + zdt.dayOfMonth).toDouble() + 1721013.5 + daySegment
    (367 * (zdt.year) - b + c + zdt.dayOfMonth).toDouble() + 1721013.5 + daySegment
    //Angles here are in degrees
    //1980 January 0.0 in JDN
    //XXX: DateTime(1980).jdn yields 2444239.5 -- which one is right?
    val epoch = 2444238.5

    //Ecliptic longitude of the Sun at epoch 1980.0
    val eclipticLongitudeEpoch = 278.833540

    //Ecliptic longitude of the Sun at perigee
    val eclipticLongitudePerigee = 282.596403

    //Eccentricity of Earth's orbit
    val eccentricity = 0.016718

    //Elements of the Moon's orbit, epoch 1980.0

    //Moon's mean longitude at the epoch
    val moonMeanLongitudeEpoch = 64.975464
    //Mean longitude of the perigee at the epoch
    val moonMeanPerigeeEpoch = 349.383063

    val day = julianDatetime - epoch
    //Mean anomaly of the Sun
    val n = wrapAngle((360 / 365.2422) * day)
    // Convert from perigee coordinates to epoch 1980
    val m = wrapAngle(n + eclipticLongitudeEpoch - eclipticLongitudePerigee)

    //Solve Kepler's equation
    var ec = kepler(m, eccentricity)
    ec = (sqrt((1 + eccentricity) / (1 - eccentricity)) * tan(ec / 2.0))
    //True anomaly
    ec = (2 * (atan(ec)) * 180.0 / Math.PI)
    //Sun's geometric ecliptic longitude
    val lambdaSun = wrapAngle(ec + eclipticLongitudePerigee)

    //Moon's mean longitude
    val moonLongitude = wrapAngle(13.1763966 * day + moonMeanLongitudeEpoch)
    //Moon's mean anomaly
    val mm = wrapAngle(moonLongitude - 0.1114041 * day - moonMeanPerigeeEpoch)
    val evection = 1.2739 * sin((2 * (moonLongitude - lambdaSun) - mm) * Math.PI / 180.0)
    //Annual equation
    val annualEq = 0.1858 * sin((m) * Math.PI / 180)
    //Correction term
    val a3 = 0.37 * sin((m) * Math.PI / 180)
    val mmP = mm + evection - annualEq - a3
    //Correction for the equation of the center
    val mEc = 6.2886 * sin((mmP) * Math.PI / 180.0)
    //Another correction term
    val a4 = 0.214 * sin((2 * mmP) * Math.PI / 180.0)
    //Corrected longitude
    val lP = moonLongitude + evection + mEc - annualEq + a4
    //Variation
    val variation = 0.6583 * sin((2 * (lP - lambdaSun)) * Math.PI / 180.0)
    //True longitude
    val lPP = lP + variation
    val moonAge = lPP - lambdaSun
    val phase = wrapAngle(moonAge) / 360.0f
    return phase.toFloat()
}

fun AssetManager.readFile(fileName: String) = open(fileName)
    .bufferedReader()
    .use { it.readText() }

fun Float.format(digits: Int) = "%.${digits}f".format(this)
fun Double.format(digits: Int) = "%.${digits}f".format(this)

fun generateArrowPath(baseX: Float,
                      baseY: Float,
                      height: Float,
                      width: Float,
                      up: Boolean): Path {
    val arrowPath = Path()
    if (up) {
        arrowPath.moveTo(baseX, baseY)
        arrowPath.lineTo(baseX, baseY - height)
        arrowPath.lineTo(baseX - width, baseY - height * 0.55f)
        arrowPath.lineTo(baseX, baseY - height * 0.55f)
    } else {
        arrowPath.moveTo(baseX, baseY - height)
        arrowPath.lineTo(baseX, baseY)
        arrowPath.lineTo(baseX - width, baseY - height * 0.45f)
        arrowPath.lineTo(baseX, baseY - height * 0.45f)
    }
    return arrowPath
}

fun calcBezierArc(cx: Float,
                  cy: Float,
                  sx: Float,
                  sy: Float,
                  sweepAngleDeg: Float): Array<Float> {
    val leg1x = sx - cx
    val leg1y = sy - cy
    val theta1 = atan2(leg1y, leg1x)
    val theta2 = theta1 + sweepAngleDeg * PI / 180f
    val radius = sqrt(leg1x * leg1x + leg1y * leg1y)
    val numSegments = 360f / sweepAngleDeg
    val ctrlPtLen = radius * 4f / 3f * tan(PI / (2 * numSegments))
    val cp3x = radius * cos(theta2) + cx
    val cp3y = radius * sin(theta2) + cy
    val ctrlPtAngle = atan2(ctrlPtLen, radius.toDouble())
    val lenToCtrlPt = sqrt(radius * radius + ctrlPtLen * ctrlPtLen)
    val cp1x = lenToCtrlPt * cos(theta1 + ctrlPtAngle) + cx
    val cp1y = lenToCtrlPt * sin(theta1 + ctrlPtAngle) + cy
    val cp2x = lenToCtrlPt * cos(theta2 - ctrlPtAngle) + cx
    val cp2y = lenToCtrlPt * sin(theta2 - ctrlPtAngle) + cy
    return arrayOf(cp1x.toFloat(),
                   cp1y.toFloat(),
                   cp2x.toFloat(),
                   cp2y.toFloat(),
                   cp3x.toFloat(),
                   cp3y.toFloat())
}

fun calcPointOnArc(cx: Float,
                   cy: Float,
                   radius: Float,
                   distY: Float,
                   quadrant: Int): Array<Float> {
    val distX = sqrt(radius * radius - distY * distY)
    var px = 0f
    var py = 0f
    when (quadrant) {
        1 -> {
            px = cx + distX
            py = cy - distY
        }
        2 -> {
            px = cx - distX
            py = cy - distY
        }
        3 -> {
            px = cx - distX
            py = cy + distY
        }
        4 -> {
            px = cx + distX
            py = cy + distY
        }
    }
    return arrayOf(px, py)
}

fun generateSquare(cx: Float,
                   cy: Float,
                   sideDist: Float,
                   rotation: Float,
                   lineWidth: Float): Array<FloatArray> {
    val points = arrayOf(floatArrayOf(-sideDist - lineWidth / 2, -sideDist),
                         floatArrayOf(sideDist, -sideDist),
                         floatArrayOf(sideDist, sideDist),
                         floatArrayOf(-sideDist, sideDist),
                         floatArrayOf(-sideDist, -sideDist - lineWidth / 2))
    if (rotation != 0f) {
        val theta = rotation * Math.PI / 180
        for (point in points) {
            val p1x = point[0] * cos(theta) + point[1] * sin(theta)
            val p1y = point[0] * -sin(theta) + point[1] * cos(theta)
            point[0] = p1x.toFloat()
            point[1] = p1y.toFloat()
        }
    }
    for (point in points) {
        point[0] += cx
        point[1] += cy
    }
    return points
}
