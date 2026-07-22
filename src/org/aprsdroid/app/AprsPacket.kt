package org.aprsdroid.app

import android.location.Location
import net.ab0oo.aprs.parser.CourseAndSpeedExtension
import net.ab0oo.aprs.parser.Position
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Kotlin port of the Scala `AprsPacket` object.
 *
 * Contains APRS-IS passcode generation, position/Mic-E encoding helpers,
 * unit conversions, and parsing utilities used across the app.
 */
object AprsPacket {

    private val QRG_RE = ".*?(\\d{2,3}[.,]\\d{3,4}).*?".toRegex()

    private val characters = arrayOf(
        "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "A", "B", "C", "D",
        "E", "F", "G", "H", "I", "J", "K", "L", "P", "Q", "R", "S", "T", "U", "V",
        "W", "X", "Y", "Z"
    )

    private const val APRS_AMBIGUITY_METERS_0 = 6
    private const val APRS_AMBIGUITY_METERS_1 = 37185
    private const val APRS_AMBIGUITY_METERS_2 = 6200
    private const val APRS_AMBIGUITY_METERS_3 = 620
    private const val APRS_AMBIGUITY_METERS_4 = 62

    private val APRS_AMBIGUITY_METERS = intArrayOf(
        APRS_AMBIGUITY_METERS_0,
        APRS_AMBIGUITY_METERS_1,
        APRS_AMBIGUITY_METERS_2,
        APRS_AMBIGUITY_METERS_3,
        APRS_AMBIGUITY_METERS_4
    )

    private const val DirectionsLatitude = "NS"
    private const val DirectionsLongitude = "EW"

    @JvmStatic
    fun statusToBits(status: String): Triple<Int, Int, Int> {
        return when (status) {
            "Off Duty" -> Triple(1, 1, 1)
            "En Route" -> Triple(1, 1, 0)
            "In Service" -> Triple(1, 0, 1)
            "Returning" -> Triple(1, 0, 0)
            "Committed" -> Triple(0, 1, 1)
            "Special" -> Triple(0, 1, 0)
            "Priority" -> Triple(0, 0, 1)
            "EMERGENCY!" -> Triple(0, 0, 0)
            else -> Triple(1, 1, 1)
        }
    }

    @JvmStatic
    fun degreesToDDM(dd: Double): Pair<Int, Double> {
        val degrees = kotlin.math.floor(dd).toInt()
        val minutes = (dd - degrees) * 60
        return degrees to minutes
    }

    @JvmStatic
    fun miceLong(dd: Double): Triple<Int, Int, Int> {
        val (degrees, minutes) = degreesToDDM(abs(dd))
        val minutesInt = kotlin.math.floor(minutes).toInt()
        val minutesHundreths = kotlin.math.floor(100 * (minutes - minutesInt)).toInt()
        return Triple(degrees, minutesInt, minutesHundreths)
    }

    @JvmStatic
    fun encodeDest(
        dd: Double,
        longOffset: Int,
        west: Int,
        messageA: Int,
        messageB: Int,
        messageC: Int,
        ambiguity: Int
    ): String {
        val north = if (dd < 0) 0 else 1

        val (degrees, minutes, minutesHundreths) = miceLong(dd)

        val degrees10 = kotlin.math.floor(degrees / 10.0).toInt()
        val degrees1 = degrees - (degrees10 * 10)

        val minutes10 = kotlin.math.floor(minutes / 10.0).toInt()
        val minutes1 = minutes - (minutes10 * 10)

        val minutesHundreths10 = kotlin.math.floor(minutesHundreths / 10.0).toInt()
        val minutesHundreths1 = minutesHundreths - (minutesHundreths10 * 10)

        val sb = StringBuilder()

        sb.append(characters[degrees10 + if (messageA == 1) 22 else 0])
        sb.append(characters[degrees1 + if (messageB == 1) 22 else 0])
        sb.append(characters[minutes10 + if (messageC == 1) 22 else 0])
        sb.append(characters[minutes1 + if (north == 1) 22 else 0])
        sb.append(characters[minutesHundreths10 + if (longOffset == 1) 22 else 0])
        sb.append(characters[minutesHundreths1 + if (west == 1) 22 else 0])

        val encoded = sb.toString()
        val validAmbiguity = max(0, min(4, ambiguity))
        val encodedArray = encoded.toCharArray()

        val modifyRules = mapOf(
            2 to Triple(messageC, 'Z', 'L'),
            3 to Triple(north, 'Z', 'L'),
            4 to Triple(longOffset, 'Z', 'L'),
            5 to Triple(west, 'Z', 'L')
        )

        for (i in (6 - validAmbiguity) until 6) {
            modifyRules[i]?.let { (condition, trueChar, falseChar) ->
                encodedArray[i] = if (condition == 1) trueChar else falseChar
            }
        }

        return String(encodedArray)
    }

    @JvmStatic
    fun encodeInfo(dd: Double, speed: Double, heading: Double, symbol: String): Triple<String, Int, Int> {
        val (degrees, minutes, minutesHundreths) = miceLong(dd)
        val west = if (dd < 0) 1 else 0

        val sb = StringBuilder()
        sb.append('`')

        val speedHT = kotlin.math.floor(speed / 10.0).toInt()
        val speedUnits = speed - (speedHT * 10)

        val headingHundreds = kotlin.math.floor(heading / 100.0).toInt()
        val headingTensUnits = heading - (headingHundreds * 100)

        var longOffset = 0

        if (degrees <= 9) {
            sb.append((degrees + 118).toChar())
            longOffset = 1
        } else if (degrees in 10..99) {
            sb.append((degrees + 28).toChar())
            longOffset = 0
        } else if (degrees in 100..109) {
            sb.append((degrees + 8).toChar())
            longOffset = 1
        } else if (degrees >= 110) {
            sb.append((degrees - 72).toChar())
            longOffset = 1
        }

        if (minutes <= 9) sb.append((minutes + 88).toChar()) else sb.append((minutes + 28).toChar())
        sb.append((minutesHundreths + 28).toChar())

        if (speed <= 199) sb.append((speedHT + 108).toChar()) else sb.append((speedHT + 28).toChar())
        sb.append((kotlin.math.floor(speedUnits * 10).toInt() + headingHundreds + 32).toChar())
        sb.append((headingTensUnits.toInt() + 28).toChar())

        sb.append(symbol[1])
        sb.append(symbol[0])
        sb.append('`')

        return Triple(sb.toString(), west, longOffset)
    }

    @JvmStatic
    fun altitude(alt: Double): String {
        val altM = kotlin.math.round(alt * 0.3048).toInt()
        val relAlt = altM + 10000

        val value1 = kotlin.math.floor(relAlt / 8281.0).toInt()
        val rem = relAlt % 8281
        val value2 = kotlin.math.floor(rem / 91.0).toInt()
        val value3 = rem % 91

        return charFromInt(value1).toString() +
            charFromInt(value2).toString() +
            charFromInt(value3).toString() +
            "}"
    }

    private fun charFromInt(value: Int): Char = (value + 33).toChar()

    @JvmStatic
    fun formatCourseSpeedMice(location: Location): Pair<Int, Int> {
        val statusSpd = if (location.hasSpeed() && location.speed > 2) {
            mps2kt(location.speed.toDouble())
        } else {
            0
        }

        val course = if (location.hasBearing()) {
            location.bearing.toInt()
        } else {
            0
        }

        return statusSpd to course
    }

    @JvmStatic
    fun formatAltitudeMice(location: Location): Int? {
        return if (location.hasAltitude()) {
            m2ft(location.altitude)
        } else {
            null
        }
    }

    /**
     * Generate the APRS-IS passcode for a callsign (SSID is ignored).
     */
    @JvmStatic
    fun passcode(callssid: String): Int {
        val call = callssid.split("-")[0].uppercase() + "\u0000"
        var hash = 0x73e2
        for (i in 0..call.length - 2 step 2) {
            hash = hash xor (call[i].code shl 8)
            hash = hash xor call[i + 1].code
        }
        return hash and 0x7fff
    }

    @JvmStatic
    fun passcodeAllowed(callssid: String, pass: String, optional: Boolean): Boolean {
        return when (pass) {
            "", "-1" -> optional
            else -> passcode(callssid).toString() == pass
        }
    }

    @JvmStatic
    fun formatCallSsid(callsign: String, ssid: String?): String {
        return if (!ssid.isNullOrEmpty()) "$callsign-$ssid" else callsign
    }

    @JvmStatic
    fun m2ft(meter: Double): Int = (meter * 3.2808399).toInt()

    @JvmStatic
    fun mps2kt(mps: Double): Int = (mps * 1.94384449).toInt()

    @JvmStatic
    fun formatAltitude(location: Location): String {
        return if (location.hasAltitude()) {
            "/A=%06d".format(m2ft(location.altitude))
        } else {
            ""
        }
    }

    @JvmStatic
    fun formatAltitudeCompressed(location: Location): String {
        return if (location.hasAltitude()) {
            val altitude = m2ft(location.altitude)
            val compressedAltitude = (ln(altitude.toDouble()) / ln(1.002) + 0.5).toInt()
            var c = (compressedAltitude / 91).toByte() + 33
            var s = (compressedAltitude % 91).toByte() + 33
            if (c < 33) c = 33
            if (s < 33) s = 33
            "%c%c".format(c.toInt().toChar(), s.toInt().toChar())
        } else {
            ""
        }
    }

    @JvmStatic
    fun formatCourseSpeed(location: Location): String {
        return if (location.hasSpeed() && location.hasBearing()) {
            "%03d/%03d".format(location.bearing.toInt(), mps2kt(location.speed.toDouble()))
        } else {
            ""
        }
    }

    @JvmStatic
    fun formatCourseSpeedCompressed(location: Location): String {
        return if (location.hasSpeed() && location.hasBearing()) {
            val compressedBearing = (location.bearing.toInt() / 4)
            val compressedSpeed = ((ln(mps2kt(location.speed.toDouble()).toDouble()) / ln(1.08)) - 1).toInt()
            var c = compressedBearing.toByte() + 33
            var s = compressedSpeed.toByte() + 33
            if (c < 33) c = 33
            if (s < 33) s = 33
            "%c%c".format(c.toInt().toChar(), s.toInt().toChar())
        } else {
            ""
        }
    }

    @JvmStatic
    fun formatFreq(csespd: String, freq: Float): String {
        return if (freq == 0f) {
            ""
        } else {
            val prefix = if (csespd.isNotEmpty()) "/" else ""
            prefix + "%07.3fMHz".format(freq)
        }
    }

    @JvmStatic
    fun formatFreqMice(freq: Float): String {
        return if (freq == 0f) {
            ""
        } else {
            "%07.3fMHz".format(freq)
        }
    }

    @JvmStatic
    fun formatLogin(callsign: String, ssid: String, passcode: String, version: String): String {
        return "user %s pass %s vers %s".format(formatCallSsid(callsign, ssid), passcode, version)
    }

    @JvmStatic
    fun formatRangeFilter(loc: Location?, range: Int): String {
        return if (loc != null) {
            "r/%.3f/%.3f/%d".format(loc.latitude, loc.longitude, range)
        } else {
            ""
        }
    }

    @JvmStatic
    fun formatDMS(coordinate: Float, nesw: String): String {
        val dms = Location.convert(abs(coordinate).toDouble(), Location.FORMAT_SECONDS).split(":")
        val neswIndex = if (coordinate < 0) 1 else 0
        return "%2s° %2s' %s\" %s".format(dms[0], dms[1], dms[2], nesw[neswIndex])
    }

    @JvmStatic
    fun formatCoordinates(latitude: Float, longitude: Float): Pair<String, String> {
        return formatDMS(latitude, DirectionsLatitude) to formatDMS(longitude, DirectionsLongitude)
    }

    @JvmStatic
    fun parseQrg(comment: String): String? {
        return QRG_RE.find(comment)?.groupValues?.get(1)
    }

    @JvmStatic
    fun parseHostPort(hostport: String, defaultport: Int): Pair<String, Int> {
        val splits = hostport.trim().split(":")
        return try {
            splits[0] to splits[1].toInt()
        } catch (_: Throwable) {
            splits[0] to defaultport
        }
    }

    @JvmStatic
    @JvmOverloads
    fun position2location(ts: Long, p: Position, cse: CourseAndSpeedExtension? = null): Location {
        val l = Location("APRS")
        l.latitude = p.latitude
        l.longitude = p.longitude
        l.time = ts
        l.accuracy = APRS_AMBIGUITY_METERS[p.positionAmbiguity].toFloat()
        if (cse != null) {
            l.bearing = cse.course.toFloat()
            l.speed = cse.speed / 1.94384449f
        }
        return l
    }
}
