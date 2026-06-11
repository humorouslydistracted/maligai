package com.maligai.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.maligai.app.localization.AppStrings
import com.maligai.app.localization.StringKey
import com.maligai.app.localization.UiLocales
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

data class PrinterDevice(val name: String, val mac: String)

data class PrintResult(val ok: Boolean, val message: String)

@Singleton
class PrinterManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private var socket: BluetoothSocket? = null
    private var output: OutputStream? = null
    private var connectedMac: String? = null

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val dtFmt = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US)

    private fun adapter(): BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    @SuppressLint("MissingPermission")
    fun pairedDevices(): List<PrinterDevice> = try {
        adapter()?.bondedDevices?.map { PrinterDevice(it.name ?: "Unknown", it.address) } ?: emptyList()
    } catch (e: SecurityException) {
        emptyList()
    }

    fun bluetoothEnabled(): Boolean = adapter()?.isEnabled == true

    @SuppressLint("MissingPermission")
    suspend fun connect(mac: String, localeTag: String = UiLocales.DEFAULT_TAG): PrintResult = withContext(Dispatchers.IO) {
        try {
            val device: BluetoothDevice = adapter()?.getRemoteDevice(mac)
                ?: return@withContext PrintResult(
                    false,
                    AppStrings.get(StringKey.BluetoothNotAvailable, localeTag)
                )
            disconnect()
            val s = device.createRfcommSocketToServiceRecord(sppUuid)
            adapter()?.cancelDiscovery()
            s.connect()
            socket = s
            output = s.outputStream
            connectedMac = mac
            _connected.value = true
            PrintResult(true, AppStrings.get(StringKey.PrinterConnected, localeTag))
        } catch (e: Exception) {
            _connected.value = false
            PrintResult(
                false,
                e.message ?: AppStrings.get(StringKey.PrinterConnectionFailed, localeTag)
            )
        }
    }

    suspend fun reconnect(mac: String): Boolean {
        if (mac.isBlank()) return false
        if (_connected.value && connectedMac == mac) return true
        return connect(mac).ok
    }

    fun disconnect() {
        try { output?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        output = null
        socket = null
        connectedMac = null
        _connected.value = false
    }

    private fun dotsForWidth(mm: Int): Int = if (mm <= 58) 384 else 576
    private fun charsForWidth(mm: Int): Int = if (mm <= 58) 32 else 48

    suspend fun printReceipt(
        bill: Bill,
        items: List<BillItem>,
        settings: AppSettings,
        fields: List<ReceiptField>,
        localeTag: String
    ): PrintResult = withContext(Dispatchers.IO) {
        val out = output ?: return@withContext PrintResult(
            false,
            AppStrings.get(StringKey.PrinterNotConnected, localeTag)
        )
        try {
            out.write(ESC_INIT)
            if (settings.rupeeFix) out.write(CODEPAGE_WPC1252)

            if (settings.shouldPrintLocalScriptReceipt()) {
                val bmp = renderReceiptBitmap(
                    bill,
                    items,
                    settings,
                    fields,
                    dotsForWidth(settings.paperWidthMm),
                    localeTag
                )
                out.write(rasterize(bmp))
            } else {
                writeTextReceipt(out, bill, items, settings, fields, localeTag)
            }
            out.write(byteArrayOf(0x0A, 0x0A, 0x0A))
            out.write(CUT)
            out.flush()
            PrintResult(true, AppStrings.get(StringKey.PrinterPrinted, localeTag))
        } catch (e: Exception) {
            _connected.value = false
            PrintResult(false, e.message ?: AppStrings.get(StringKey.PrinterPrintFailed, localeTag))
        }
    }

    private fun writeTextReceipt(
        out: OutputStream,
        bill: Bill,
        items: List<BillItem>,
        settings: AppSettings,
        fields: List<ReceiptField>,
        localeTag: String
    ) {
        val width = charsForWidth(settings.paperWidthMm)

        out.write(ALIGN_CENTER)
        repeat(settings.receiptDotsTop.coerceIn(0, 10)) { out.write(SPACER) }
        out.write(BOLD_ON)
        fields.filter { it.enabled && it.value.isNotBlank() }.forEach {
            out.write(line(it.value))
        }
        out.write(BOLD_OFF)
        out.write(line(sep(width)))
        out.write(ALIGN_LEFT)

        out.write(line(AppStrings.get(StringKey.ReceiptBill, localeTag, bill.name)))
        out.write(line(AppStrings.get(StringKey.ReceiptDate, localeTag, dtFmt.format(Date(bill.completedAt ?: bill.createdAt)))))
        out.write(line(sep(width)))

        items.forEach { item ->
            val name = item.itemNameLatin.ifBlank { item.itemName }
            val amount = "Rs${trimQty(item.lineTotal)}"
            if (item.showsQtyBreakdown()) {
                val qty = item.receiptQuantityLabel()
                out.write(line(name))
                out.write(line(twoCol("  $qty x ${trimQty(item.unitPrice)}", amount, width)))
            } else {
                out.write(line(twoCol(name, amount, width)))
            }
        }
        out.write(line(sep(width)))

        if (settings.gstEnabled && (bill.cgst + bill.sgst) > 0) {
            out.write(line(twoCol(AppStrings.get(StringKey.ReceiptSubtotal, localeTag), "Rs${trimQty(bill.subtotal)}", width)))
            out.write(line(twoCol(AppStrings.get(StringKey.ReceiptCgst, localeTag), "Rs${trimQty(bill.cgst)}", width)))
            out.write(line(twoCol(AppStrings.get(StringKey.ReceiptSgst, localeTag), "Rs${trimQty(bill.sgst)}", width)))
        }
        out.write(BOLD_ON)
        out.write(line(twoCol(AppStrings.get(StringKey.ReceiptTotal, localeTag), "Rs${trimQty(bill.total)}", width)))
        out.write(BOLD_OFF)

        if (bill.isLoan) {
            out.write(line(sep(width)))
            out.write(line("** CREDIT / KADAN **"))
        }

        out.write(line(sep(width)))
        out.write(ALIGN_CENTER)
        if (settings.footerText.isNotBlank()) out.write(line(settings.footerText))
        repeat(settings.receiptDotsBottom.coerceIn(0, 10)) { out.write(SPACER) }
        out.write(ALIGN_LEFT)
    }

    private fun renderReceiptBitmap(
        bill: Bill,
        items: List<BillItem>,
        settings: AppSettings,
        fields: List<ReceiptField>,
        widthDots: Int,
        localeTag: String
    ): Bitmap {
        val lines = mutableListOf<Pair<String, Boolean>>() // text, bold
        repeat(settings.receiptDotsTop.coerceIn(0, 10)) { lines.add(" " to false) }
        fields.filter { it.enabled && it.value.isNotBlank() }.forEach { lines.add(it.value to true) }
        lines.add("------------------------------" to false)
        lines.add(AppStrings.get(StringKey.ReceiptBill, localeTag, bill.name) to false)
        lines.add(AppStrings.get(StringKey.ReceiptDate, localeTag, dtFmt.format(Date(bill.completedAt ?: bill.createdAt))) to false)
        lines.add("------------------------------" to false)
        items.forEach { item ->
            val name = item.itemName.ifBlank { item.itemNameLatin }
            if (item.showsQtyBreakdown()) {
                lines.add(name to false)
                lines.add(
                    "  ${item.receiptQuantityLabel(localeTag)} x ${trimQty(item.unitPrice)} = Rs${trimQty(item.lineTotal)}" to false
                )
            } else {
                lines.add("$name  Rs${trimQty(item.lineTotal)}" to false)
            }
        }
        lines.add("------------------------------" to false)
        if (settings.gstEnabled && (bill.cgst + bill.sgst) > 0) {
            lines.add("${AppStrings.get(StringKey.ReceiptSubtotal, localeTag)}: Rs${trimQty(bill.subtotal)}" to false)
            lines.add("${AppStrings.get(StringKey.ReceiptCgst, localeTag)}: Rs${trimQty(bill.cgst)}" to false)
            lines.add("${AppStrings.get(StringKey.ReceiptSgst, localeTag)}: Rs${trimQty(bill.sgst)}" to false)
        }
        lines.add("${AppStrings.get(StringKey.ReceiptTotal, localeTag)}: Rs${trimQty(bill.total)}" to true)
        if (bill.isLoan) lines.add("** CREDIT / KADAN **" to true)
        if (settings.footerText.isNotBlank()) lines.add(settings.footerText to false)
        repeat(settings.receiptDotsBottom.coerceIn(0, 10)) { lines.add(" " to false) }

        val textSize = 26f
        val lineHeight = ceil(textSize * 1.4f).toInt()
        val padding = 8
        val maxTextWidth = (widthDots - padding * 2).toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            this.textSize = textSize
        }
        val rendered = mutableListOf<Pair<String, Boolean>>()
        for ((text, bold) in lines) {
            paint.typeface = receiptTypeface(bold)
            wrapReceiptText(text, paint, maxTextWidth).forEach { rendered.add(it to bold) }
        }
        val height = padding * 2 + lineHeight * rendered.size
        val bmp = Bitmap.createBitmap(widthDots, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        var y = padding + textSize
        for ((text, bold) in rendered) {
            paint.typeface = receiptTypeface(bold)
            canvas.drawText(text, padding.toFloat(), y, paint)
            y += lineHeight
        }
        return bmp
    }

    /** Sans-serif uses device Noto fonts, which cover Indic scripts on typical Android devices. */
    private fun receiptTypeface(bold: Boolean): Typeface =
        Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)

    /** Character-wise wrap so long native-script item names fit thermal paper width. */
    private fun wrapReceiptText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        if (paint.measureText(text) <= maxWidth) return listOf(text)
        val out = mutableListOf<String>()
        var line = StringBuilder()
        for (ch in text) {
            val next = line.toString() + ch
            if (paint.measureText(next) > maxWidth && line.isNotEmpty()) {
                out.add(line.toString())
                line = StringBuilder().append(ch)
            } else {
                line.append(ch)
            }
        }
        if (line.isNotEmpty()) out.add(line.toString())
        return out
    }

    /** Converts a bitmap into ESC/POS GS v 0 raster bytes (monochrome). */
    private fun rasterize(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val bytesPerRow = (width + 7) / 8
        val data = ByteArray(bytesPerRow * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val luminance = (r * 0.299 + g * 0.587 + b * 0.114)
                if (luminance < 128) {
                    data[y * bytesPerRow + (x / 8)] =
                        (data[y * bytesPerRow + (x / 8)].toInt() or (0x80 shr (x % 8))).toByte()
                }
            }
        }
        val header = byteArrayOf(
            0x1D, 0x76, 0x30, 0x00,
            (bytesPerRow and 0xFF).toByte(), ((bytesPerRow shr 8) and 0xFF).toByte(),
            (height and 0xFF).toByte(), ((height shr 8) and 0xFF).toByte()
        )
        return header + data
    }

    private fun line(s: String): ByteArray = (s + "\n").toByteArray(Charsets.ISO_8859_1)
    private fun sep(width: Int): String = "-".repeat(width)

    private fun twoCol(left: String, right: String, width: Int): String {
        val space = width - left.length - right.length
        return if (space > 0) left + " ".repeat(space) + right
        else (left.take(width - right.length - 1)) + " " + right
    }

    private fun trimQty(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else String.format(Locale.US, "%.2f", d)

    companion object {
        private val ESC_INIT = byteArrayOf(0x1B, 0x40)
        private val ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
        private val ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
        private val BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
        private val BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
        private val CUT = byteArrayOf(0x1D, 0x56, 0x42, 0x00)
        private val SPACER = ".\n".toByteArray()
        private val CODEPAGE_WPC1252 = byteArrayOf(0x1B, 0x74, 0x42)
    }
}
