package org.fossify.gallery.helpers

import android.graphics.Bitmap
import java.io.IOException
import java.io.OutputStream
import kotlin.math.abs

/**
 * GIFアニメーションのエンコーダ。
 * 各フレームの画像、表示時間（ディレイ）、ループ回数を設定して
 * GIFファイルを出力する。
 */
class AnimatedGifEncoder {
    private var width = 0
    private var height = 0
    private var transparent: Int? = null
    private var transIndex = 0
    private var repeat = -1
    private var delay = 0
    private var started = false
    private var out: OutputStream? = null
    private var currentImage: Bitmap? = null
    private var pixels: ByteArray? = null
    private var indexedPixels: ByteArray? = null
    private var colorDepth = 8
    private var colorTab: ByteArray? = null
    private var usedEntry = BooleanArray(256)
    private var palSize = 7
    private var dispose = -1
    private var closeStream = false
    private var firstFrame = true
    private var sizeSet = false
    private var sample = 10

    fun setDelay(ms: Int) {
        delay = ms / 10
    }

    fun setRepeat(iter: Int) {
        if (iter >= 0) {
            repeat = iter
        }
    }

    fun setTransparent(color: Int) {
        transparent = color
    }

    fun setQuality(quality: Int) {
        sample = if (quality < 1) 1 else quality
    }

    fun setSize(w: Int, h: Int) {
        width = w
        height = h
        if (width < 1) width = 1
        if (height < 1) height = 1
        sizeSet = true
    }

    fun start(os: OutputStream): Boolean {
        out = os
        closeStream = false
        return try {
            writeString("GIF89a")
            started = true
            true
        } catch (e: IOException) {
            false
        }
    }

    fun addFrame(im: Bitmap): Boolean {
        if (!started || out == null) return false
        try {
            if (!sizeSet) {
                setSize(im.width, im.height)
            }
            currentImage = im
            getImagePixels()
            analyzePixels()
            if (firstFrame) {
                writeLSD()
                writePalette()
                if (repeat >= 0) {
                    writeNetscapeExt()
                }
            }
            writeGraphicCtrlExt()
            writeImageDesc()
            if (!firstFrame) {
                writePalette()
            }
            writePixels()
            firstFrame = false
            return true
        } catch (e: IOException) {
            return false
        }
    }

    fun finish(): Boolean {
        if (!started) return false
        var ok = true
        started = false
        try {
            out?.write(0x3b)
            out?.flush()
            if (closeStream) {
                out?.close()
            }
        } catch (e: IOException) {
            ok = false
        }
        transIndex = 0
        out = null
        currentImage = null
        pixels = null
        indexedPixels = null
        colorTab = null
        firstFrame = true
        return ok
    }

    private fun getImagePixels() {
        val w = currentImage!!.width
        val h = currentImage!!.height
        val tempBitmap = if (w != width || h != height) {
            Bitmap.createScaledBitmap(currentImage!!, width, height, true)
        } else {
            currentImage!!
        }

        val rgb = IntArray(width * height)
        tempBitmap.getPixels(rgb, 0, width, 0, 0, width, height)

        pixels = ByteArray(rgb.size * 3)
        var count = 0
        for (i in rgb.indices) {
            val color = rgb[i]
            pixels!![count++] = (color and 0xFF).toByte()
            pixels!![count++] = ((color shr 8) and 0xFF).toByte()
            pixels!![count++] = ((color shr 16) and 0xFF).toByte()
        }
    }

    private fun analyzePixels() {
        val len = pixels!!.size
        val nPix = len / 3
        indexedPixels = ByteArray(nPix)
        val nq = NeuQuant(pixels!!, len, sample)
        colorTab = nq.process()

        var k = 0
        for (i in 0 until nPix) {
            val b = pixels!![k++].toInt() and 0xff
            val g = pixels!![k++].toInt() and 0xff
            val r = pixels!![k++].toInt() and 0xff
            val index = nq.map(b, g, r)
            usedEntry[index] = true
            indexedPixels!![i] = index.toByte()
        }
        pixels = null
        colorDepth = 8
        palSize = 7

        if (transparent != null) {
            transIndex = findClosest(transparent!!)
        }
    }

    private fun findClosest(color: Int): Int {
        if (colorTab == null) return -1
        val r = (color shr 16) and 0xff
        val g = (color shr 8) and 0xff
        val b = color and 0xff
        var minpos = 0
        var dmin = 256 * 256 * 256
        val len = colorTab!!.size
        var i = 0
        var index = 0
        while (i < len) {
            val dr = r - (colorTab!![i++].toInt() and 0xff)
            val dg = g - (colorTab!![i++].toInt() and 0xff)
            val db = b - (colorTab!![i++].toInt() and 0xff)
            val d = dr * dr + dg * dg + db * db
            if (usedEntry[index] && d < dmin) {
                dmin = d
                minpos = index
            }
            index++
        }
        return minpos
    }

    private fun writeGraphicCtrlExt() {
        out!!.write(0x21)
        out!!.write(0xf9)
        out!!.write(4)
        val transp: Int
        val disp: Int
        if (transparent == null) {
            transp = 0
            disp = 0
        } else {
            transp = 1
            disp = 2
        }
        if (dispose >= 0) {
            disp == dispose and 7
        }
        out!!.write(0 or (disp shl 2) or 0 or transp)
        writeShort(delay)
        out!!.write(transIndex)
        out!!.write(0)
    }

    private fun writeImageDesc() {
        out!!.write(0x2c)
        writeShort(0)
        writeShort(0)
        writeShort(width)
        writeShort(height)
        if (firstFrame) {
            out!!.write(0)
        } else {
            out!!.write(0x80 or palSize)
        }
    }

    private fun writeLSD() {
        writeShort(width)
        writeShort(height)
        out!!.write(0x80 or 0x70 or 0x00 or palSize)
        out!!.write(0)
        out!!.write(0)
    }

    private fun writeNetscapeExt() {
        out!!.write(0x21)
        out!!.write(0xff)
        out!!.write(11)
        writeString("NETSCAPE2.0")
        out!!.write(3)
        out!!.write(1)
        writeShort(repeat)
        out!!.write(0)
    }

    private fun writePalette() {
        out!!.write(colorTab!!, 0, colorTab!!.size)
        val n = (3 * 256) - colorTab!!.size
        for (i in 0 until n) {
            out!!.write(0)
        }
    }

    private fun writePixels() {
        val encoder = LZWEncoder(width, height, indexedPixels!!, colorDepth)
        encoder.encode(out!!)
    }

    private fun writeShort(value: Int) {
        out!!.write(value and 0xff)
        out!!.write((value shr 8) and 0xff)
    }

    private fun writeString(s: String) {
        for (i in 0 until s.length) {
            out!!.write(s[i].code)
        }
    }

    private class NeuQuant(
        private val thepicture: ByteArray,
        private val lengthcount: Int,
        private val samplefac: Int
    ) {
        private val netsize = 256
        private val prime1 = 499
        private val prime2 = 491
        private val prime3 = 487
        private val prime4 = 503
        private val minpicturebytes = 3 * prime4
        private val maxnetpos = netsize - 1
        private val netbiasshift = 4
        private val ncycles = 100
        private val intbiasshift = 16
        private val intbias = 1 shl intbiasshift
        private val gammashift = 10
        private val gamma = 1 shl gammashift
        private val betashift = 10
        private val beta = intbias shr betashift
        private val betagamma = intbias shl (gammashift - betashift)
        private val initrad = netsize shr 3
        private val radiusbiasshift = 6
        private val radiusbias = 1 shl radiusbiasshift
        private val initradius = initrad * radiusbias
        private val radiusdec = 30
        private val alphabiasshift = 10
        private val initalpha = 1 shl alphabiasshift
        private var alphadec = 0
        private val radbiasshift = 8
        private val radbias = 1 shl radbiasshift
        private val alpharadbise = initalpha * radbias

        private val network = Array(netsize) { IntArray(4) }
        private val netindex = IntArray(256)
        private val bias = IntArray(netsize)
        private val freq = IntArray(netsize)
        private val radpower = IntArray(initrad)

        init {
            for (i in 0 until netsize) {
                val v = (i shl (netbiasshift + 8)) / netsize
                network[i][0] = v
                network[i][1] = v
                network[i][2] = v
                freq[i] = intbias / netsize
                bias[i] = 0
            }
        }

        fun colorMap(): ByteArray {
            val map = ByteArray(3 * netsize)
            val index = IntArray(netsize)
            for (i in 0 until netsize) {
                index[network[i][3]] = i
            }
            var k = 0
            for (i in 0 until netsize) {
                val j = index[i]
                map[k++] = network[j][0].toByte()
                map[k++] = network[j][1].toByte()
                map[k++] = network[j][2].toByte()
            }
            return map
        }

        fun inxbuild() {
            var previouscol = 0
            var startpos = 0
            for (i in 0 until netsize) {
                val p = network[i]
                var smallpos = i
                var smallval = p[1]
                for (j in i + 1 until netsize) {
                    val q = network[j]
                    if (q[1] < smallval) {
                        smallpos = j
                        smallval = q[1]
                    }
                }
                val q = network[smallpos]
                if (i != smallpos) {
                    var j = p[0]; p[0] = q[0]; q[0] = j
                    j = p[1]; p[1] = q[1]; q[1] = j
                    j = p[2]; p[2] = q[2]; q[2] = j
                    j = p[3]; p[3] = q[3]; q[3] = j
                }
                if (smallval != previouscol) {
                    netindex[previouscol] = (startpos + i) shr 1
                    for (j in previouscol + 1 until smallval) netindex[j] = i
                    previouscol = smallval
                    startpos = i
                }
            }
            netindex[previouscol] = (startpos + maxnetpos) shr 1
            for (j in previouscol + 1 until 256) netindex[j] = maxnetpos
        }

        fun learn() {
            var i: Int
            if (lengthcount < minpicturebytes) {
                alphadec = 1
            } else {
                alphadec = 30 + ((samplefac - 1) / 3)
            }
            val lim = lengthcount
            val samplepixels = lengthcount / (3 * samplefac)
            var delta = samplepixels / ncycles
            if (delta == 0) delta = 1
            var alpha = initalpha
            var radius = initradius
            var rad = radius shr radiusbiasshift
            if (rad <= 1) rad = 0
            for (r in 0 until rad) {
                radpower[r] = alpha * (((rad * rad - r * r) * radbias) / (rad * rad))
            }
            val step = when {
                lengthcount < minpicturebytes -> 3
                lengthcount % prime1 != 0 -> 3 * prime1
                lengthcount % prime2 != 0 -> 3 * prime2
                lengthcount % prime3 != 0 -> 3 * prime3
                else -> 3 * prime4
            }
            var pix = 0
            i = 0
            while (i < samplepixels) {
                val b = (thepicture[pix].toInt() and 0xff) shl netbiasshift
                val g = (thepicture[pix + 1].toInt() and 0xff) shl netbiasshift
                val r = (thepicture[pix + 2].toInt() and 0xff) shl netbiasshift
                val j = contest(b, g, r)
                altersingle(alpha, j, b, g, r)
                if (rad != 0) alterneigh(rad, j, b, g, r)
                pix += step
                if (pix >= lim) pix -= lengthcount
                i++
                if (delta == 0) delta = 1
                if (i % delta == 0) {
                    alpha -= alpha / alphadec
                    radius -= radius / radiusdec
                    rad = radius shr radiusbiasshift
                    if (rad <= 1) rad = 0
                    for (k in 0 until rad) {
                        radpower[k] = alpha * (((rad * rad - k * k) * radbias) / (rad * rad))
                    }
                }
            }
        }

        fun map(b: Int, g: Int, r: Int): Int {
            var bestd = 1000
            var best = -1
            var i = netindex[g]
            var j = i - 1
            while (i < netsize || j >= 0) {
                if (i < netsize) {
                    val p = network[i]
                    var dist = p[1] - g
                    if (dist >= bestd) {
                        i = netsize
                    } else {
                        i++
                        if (dist < 0) dist = -dist
                        var a = p[0] - b
                        if (a < 0) a = -a
                        dist += a
                        if (dist < bestd) {
                            a = p[2] - r
                            if (a < 0) a = -a
                            dist += a
                            if (dist < bestd) {
                                bestd = dist
                                best = p[3]
                            }
                        }
                    }
                }
                if (j >= 0) {
                    val p = network[j]
                    var dist = g - p[1]
                    if (dist >= bestd) {
                        j = -1
                    } else {
                        j--
                        if (dist < 0) dist = -dist
                        var a = p[0] - b
                        if (a < 0) a = -a
                        dist += a
                        if (dist < bestd) {
                            a = p[2] - r
                            if (a < 0) a = -a
                            dist += a
                            if (dist < bestd) {
                                bestd = dist
                                best = p[3]
                            }
                        }
                    }
                }
            }
            return best
        }

        fun process(): ByteArray {
            learn()
            unbiasnet()
            inxbuild()
            return colorMap()
        }

        private fun unbiasnet() {
            for (i in 0 until netsize) {
                network[i][0] = network[i][0] shr netbiasshift
                network[i][1] = network[i][1] shr netbiasshift
                network[i][2] = network[i][2] shr netbiasshift
                network[i][3] = i
            }
        }

        private fun altersingle(alpha: Int, i: Int, b: Int, g: Int, r: Int) {
            network[i][0] -= (alpha * (network[i][0] - b)) / initalpha
            network[i][1] -= (alpha * (network[i][1] - g)) / initalpha
            network[i][2] -= (alpha * (network[i][2] - r)) / initalpha
        }

        private fun alterneigh(rad: Int, i: Int, b: Int, g: Int, r: Int) {
            var lo = i - rad
            if (lo < -1) lo = -1
            var hi = i + rad
            if (hi > netsize) hi = netsize
            var j = i + 1
            var k = i - 1
            var m = 1
            while (j < hi || k > lo) {
                val a = radpower[m++]
                if (j < hi) {
                    val p = network[j++]
                    try {
                        p[0] -= (a * (p[0] - b)) / alpharadbise
                        p[1] -= (a * (p[1] - g)) / alpharadbise
                        p[2] -= (a * (p[2] - r)) / alpharadbise
                    } catch (_: Exception) {}
                }
                if (k > lo) {
                    val p = network[k--]
                    try {
                        p[0] -= (a * (p[0] - b)) / alpharadbise
                        p[1] -= (a * (p[1] - g)) / alpharadbise
                        p[2] -= (a * (p[2] - r)) / alpharadbise
                    } catch (_: Exception) {}
                }
            }
        }

        private fun contest(b: Int, g: Int, r: Int): Int {
            var bestd = Int.MAX_VALUE
            var bestbiasd = bestd
            var bestpos = -1
            var bestbiaspos = bestpos
            for (i in 0 until netsize) {
                val n = network[i]
                var dist = abs(n[0] - b) + abs(n[1] - g) + abs(n[2] - r)
                if (dist < bestd) {
                    bestd = dist
                    bestpos = i
                }
                val biasdist = dist - (bias[i] shr (intbiasshift - netbiasshift))
                if (biasdist < bestbiasd) {
                    bestbiasd = biasdist
                    bestbiaspos = i
                }
                val betafreq = freq[i] shr betashift
                freq[i] -= betafreq
                bias[i] += betafreq shl gammashift
            }
            freq[bestpos] += beta
            bias[bestpos] -= betagamma
            return bestbiaspos
        }
    }

    private class LZWEncoder(
        private val imgW: Int,
        private val imgH: Int,
        private val pixAry: ByteArray,
        private val initCodeSize: Int
    ) {
        private val EOF = -1
        private var cur_accum = 0
        private var cur_bits = 0
        private val masks = intArrayOf(
            0x0000, 0x0001, 0x0003, 0x0007, 0x000F, 0x001F, 0x003F, 0x007F,
            0x00FF, 0x01FF, 0x03FF, 0x07FF, 0x0FFF, 0x1FFF, 0x3FFF, 0x7FFF, 0xFFFF
        )
        private val BITS = 12
        private val HSIZE = 5003
        private var n_bits = 0
        private var maxbits = BITS
        private var maxcode = 0
        private var maxmaxcode = 1 shl BITS
        private val htab = IntArray(HSIZE)
        private val codetab = IntArray(HSIZE)
        private var hsize = HSIZE
        private var free_ent = 0
        private var clear_flg = false
        private var g_init_bits = 0
        private var ClearCode = 0
        private var EOFCode = 0
        private var curPix = 0
        private val accum = ByteArray(256)
        private var a_count = 0

        fun encode(os: OutputStream) {
            os.write(initCodeSize)
            remaining = imgW * imgH
            curPix = 0
            compress(initCodeSize + 1, os)
            os.write(0)
        }

        private var remaining = 0

        private fun nextPixel(): Int {
            if (remaining == 0) return EOF
            --remaining
            val pix = pixAry[curPix++].toInt() and 0xff
            return pix
        }

        private fun compress(init_bits: Int, outs: OutputStream) {
            var fcode: Int
            var i: Int
            var c: Int
            var ent: Int
            var disp: Int
            val hsize_reg: Int
            var hshift: Int

            g_init_bits = init_bits
            clear_flg = false
            n_bits = g_init_bits
            maxcode = MAXCODE(n_bits)

            ClearCode = 1 shl (init_bits - 1)
            EOFCode = ClearCode + 1
            free_ent = ClearCode + 2

            a_count = 0
            ent = nextPixel()

            hshift = 0
            fcode = hsize
            while (fcode < 65536) {
                ++hshift
                fcode *= 2
            }
            hshift = 8 - hshift
            hsize_reg = hsize
            cl_hash(hsize_reg)

            output(ClearCode, outs)

            outer_loop@ while (nextPixel().also { c = it } != EOF) {
                fcode = (c shl maxbits) + ent
                i = (c shl hshift) xor ent
                if (htab[i] == fcode) {
                    ent = codetab[i]
                    continue
                } else if (htab[i] >= 0) {
                    disp = hsize_reg - i
                    if (i == 0) disp = 1
                    do {
                        i -= disp
                        if (i < 0) i += hsize_reg
                        if (htab[i] == fcode) {
                            ent = codetab[i]
                            continue@outer_loop
                        }
                    } while (htab[i] >= 0)
                }
                output(ent, outs)
                ent = c
                if (free_ent < maxmaxcode) {
                    codetab[i] = free_ent++
                    htab[i] = fcode
                } else {
                    cl_block(outs)
                }
            }
            output(ent, outs)
            output(EOFCode, outs)
        }

        private fun output(code: Int, outs: OutputStream) {
            cur_accum = cur_accum and masks[cur_bits]
            if (cur_bits > 0) cur_accum = cur_accum or (code shl cur_bits)
            else cur_accum = code
            cur_bits += n_bits

            while (cur_bits >= 8) {
                char_out((cur_accum and 0xff).toByte(), outs)
                cur_accum = cur_accum shr 8
                cur_bits -= 8
            }

            if (free_ent > maxcode || clear_flg) {
                if (clear_flg) {
                    maxcode = MAXCODE(n_bits.also { n_bits = g_init_bits })
                    clear_flg = false
                } else {
                    ++n_bits
                    maxcode = if (n_bits == maxbits) maxmaxcode else MAXCODE(n_bits)
                }
            }

            if (code == EOFCode) {
                while (cur_bits > 0) {
                    char_out((cur_accum and 0xff).toByte(), outs)
                    cur_accum = cur_accum shr 8
                    cur_bits -= 8
                }
                flush_char(outs)
            }
        }

        private fun cl_block(outs: OutputStream) {
            cl_hash(hsize)
            free_ent = ClearCode + 2
            clear_flg = true
            output(ClearCode, outs)
        }

        private fun cl_hash(hsize: Int) {
            for (i in 0 until hsize) htab[i] = -1
        }

        private fun char_out(c: Byte, outs: OutputStream) {
            accum[a_count++] = c
            if (a_count >= 254) flush_char(outs)
        }

        private fun flush_char(outs: OutputStream) {
            if (a_count > 0) {
                outs.write(a_count)
                outs.write(accum, 0, a_count)
                a_count = 0
            }
        }

        private fun MAXCODE(n_bits: Int) = (1 shl n_bits) - 1
    }
}
