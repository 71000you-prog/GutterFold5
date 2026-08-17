package com.example.gutterpdf

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintDocumentAdapter.LayoutResultCallback
import android.print.PrintDocumentAdapter.WriteResultCallback
import android.print.PrintManager
import android.text.InputType
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.CancellationException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Suppress("DEPRECATION")
class MainActivity : Activity() {

    private companion object {
        const val REQUEST_OPEN_PDF = 1
    }

    private var inputUri: Uri? = null
    private var isWorking = false

    private lateinit var contentPanel: LinearLayout
    private lateinit var modeInfo: TextView

    private lateinit var innerInput: EditText
    private lateinit var outerInput: EditText
    private lateinit var topInput: EditText
    private lateinit var bottomInput: EditText

    private lateinit var radioLong: RadioButton
    private lateinit var radioShort: RadioButton
    private lateinit var swapOddEven: CheckBox
    private lateinit var keepAspectCheck: CheckBox

    private lateinit var printButton: Button
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isFillViewport = true
            fitsSystemWindows = true
        }

        contentPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp(24f), dp(24f), dp(24f), dp(24f))
        }

        scrollView.addView(contentPanel)
        setContentView(scrollView)

        val title = TextView(this).apply {
            text = "A4 Custom Margin Printer"
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val subtitle = TextView(this).apply {
            text = "For Samsung Galaxy Z Fold 5"
            textSize = 13f
        }

        radioLong = RadioButton(this).apply {
            id = View.generateViewId()
            text = "Duplex long edge"
            minimumHeight = dp(48f)
        }

        radioShort = RadioButton(this).apply {
            id = View.generateViewId()
            text = "Duplex short edge"
            minimumHeight = dp(48f)
        }

        val duplexGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            addView(radioLong)
            addView(radioShort)
        }

        duplexGroup.check(radioLong.id)

        modeInfo = TextView(this).apply {
            textSize = 13f
        }

        duplexGroup.setOnCheckedChangeListener { _, _ ->
            updateModeInfo()
        }

        val innerLabel = TextView(this).apply {
            text = "Inner margin in mm, negative allowed"
        }

        innerInput = createMarginInput("10")

        val outerLabel = TextView(this).apply {
            text = "Outer margin in mm, negative allowed"
        }

        outerInput = createMarginInput("0")

        val topLabel = TextView(this).apply {
            text = "Top margin in mm, negative allowed"
        }

        topInput = createMarginInput("0")

        val bottomLabel = TextView(this).apply {
            text = "Bottom margin in mm, negative allowed"
        }

        bottomInput = createMarginInput("0")

        keepAspectCheck = CheckBox(this).apply {
            text = "Keep aspect ratio, recommended"
            isChecked = true
            minimumHeight = dp(48f)
        }

        swapOddEven = CheckBox(this).apply {
            text = "Swap odd/even sides"
            minimumHeight = dp(48f)
        }

        val chooseButton = Button(this).apply {
            text = "Choose input PDF"
            minimumHeight = dp(52f)
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/pdf"
                }
                startActivityForResult(intent, REQUEST_OPEN_PDF)
            }
        }

        printButton = Button(this).apply {
            text = "Print directly"
            minimumHeight = dp(52f)
            setOnClickListener {
                onPrintClicked()
            }
        }

        statusText = TextView(this).apply {
            text = "Choose an A4 PDF"
        }

        add(title)
        add(subtitle, 8f)
        add(duplexGroup, 16f)
        add(modeInfo, 8f)

        add(innerLabel, 24f)
        add(innerInput, 4f)

        add(outerLabel, 12f)
        add(outerInput, 4f)

        add(topLabel, 12f)
        add(topInput, 4f)

        add(bottomLabel, 12f)
        add(bottomInput, 4f)

        add(keepAspectCheck, 16f)
        add(swapOddEven, 8f)
        add(chooseButton, 24f)
        add(printButton, 8f)
        add(statusText, 16f)

        applyFoldLayout(resources.configuration)
        updateModeInfo()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyFoldLayout(newConfig)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != RESULT_OK) return

        if (requestCode == REQUEST_OPEN_PDF) {
            val uri = data?.data ?: return
            inputUri = uri

            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Ignore. Temporary permission is usually enough.
            }

            statusText.text = "Selected PDF: ${uri.lastPathSegment ?: uri.toString()}"
        }
    }

    private fun updateModeInfo() {
        modeInfo.text = if (radioLong.isChecked) {
            "Long edge: Inner/Outer are left/right. Top/Bottom are top/bottom."
        } else {
            "Short edge: Inner/Outer are added to Top/Bottom. Left/Right are not changed."
        }
    }

    private fun createMarginInput(defaultValue: String): EditText {
        return EditText(this).apply {
            setText(defaultValue)
            inputType = InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_FLAG_DECIMAL or
                InputType.TYPE_NUMBER_FLAG_SIGNED
            minimumHeight = dp(48f)
        }
    }

    private fun parseMargin(editText: EditText): Float? {
        return editText.text.toString()
            .trim()
            .replace(" ", "")
            .replace("−", "-")
            .replace(",", ".")
            .toFloatOrNull()
    }

    private fun onPrintClicked() {
        if (isWorking) {
            toast("Please wait")
            return
        }

        val input = inputUri
        if (input == null) {
            toast("Choose an input PDF first")
            return
        }

        val inner = parseMargin(innerInput)
        val outer = parseMargin(outerInput)
        val top = parseMargin(topInput)
        val bottom = parseMargin(bottomInput)

        if (inner == null || inner !in -100f..100f) {
            toast("Inner margin must be between -100 and 100 mm")
            return
        }

        if (outer == null || outer !in -100f..100f) {
            toast("Outer margin must be between -100 and 100 mm")
            return
        }

        if (top == null || top !in -100f..100f) {
            toast("Top margin must be between -100 and 100 mm")
            return
        }

        if (bottom == null || bottom !in -100f..100f) {
            toast("Bottom margin must be between -100 and 100 mm")
            return
        }

        val edge = if (radioLong.isChecked) {
            DuplexEdge.LONG_EDGE
        } else {
            DuplexEdge.SHORT_EDGE
        }

        val options = MarginOptions(
            innerMm = inner,
            outerMm = outer,
            topMm = top,
            bottomMm = bottom,
            edge = edge,
            swapOddEven = swapOddEven.isChecked,
            keepAspectRatio = keepAspectCheck.isChecked,
            renderDpi = 300
        )

        isWorking = true
        printButton.isEnabled = false
        statusText.text = "Checking PDF..."

        val appContext = applicationContext

        Thread {
            try {
                val pageCount = PdfGutterProcessor.validateA4AndGetPageCount(
                    context = appContext,
                    inputUri = input
                )

                runOnUiThread {
                    try {
                        statusText.text = "Starting print..."
                        startPrint(input, options, pageCount)
                    } catch (e: Throwable) {
                        val message = e.message ?: e.javaClass.simpleName
                        statusText.text = "Error: $message"
                        toast("Error: $message")
                    } finally {
                        isWorking = false
                        printButton.isEnabled = true
                    }
                }

            } catch (e: Throwable) {
                val message = e.message ?: e.javaClass.simpleName
                runOnUiThread {
                    statusText.text = "Error: $message"
                    toast("Error: $message")
                    isWorking = false
                    printButton.isEnabled = true
                }
            }
        }.start()
    }

    private fun startPrint(
        input: Uri,
        options: MarginOptions,
        pageCount: Int
    ) {
        val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager

        val adapter = MarginPrintDocumentAdapter(
            context = applicationContext,
            inputUri = input,
            options = options,
            pageCount = pageCount
        )

        val duplexMode = if (options.edge == DuplexEdge.LONG_EDGE) {
            PrintAttributes.DUPLEX_MODE_LONG_EDGE
        } else {
            PrintAttributes.DUPLEX_MODE_SHORT_EDGE
        }

        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setDuplex(duplexMode)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .setResolution(
                PrintAttributes.Resolution(
                    "300dpi",
                    "300 dpi",
                    300,
                    300
                )
            )
            .build()

        printManager.print("A4 Custom Margin PDF", adapter, attributes)

        statusText.text = "Print dialog opened"
    }

    private fun applyFoldLayout(config: Configuration) {
        val isLargeScreen = config.smallestScreenWidthDp >= 600

        val paddingDp = if (isLargeScreen) {
            48f
        } else {
            24f
        }

        val paddingPx = dp(paddingDp)

        contentPanel.setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
    }

    private fun add(view: View, topDp: Float = 0f) {
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.topMargin = dp(topDp)
        contentPanel.addView(view, params)
    }

    private fun dp(value: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            resources.displayMetrics
        ).toInt()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

enum class DuplexEdge {
    LONG_EDGE,
    SHORT_EDGE
}

data class MarginOptions(
    val innerMm: Float,
    val outerMm: Float,
    val topMm: Float,
    val bottomMm: Float,
    val edge: DuplexEdge,
    val swapOddEven: Boolean = false,
    val keepAspectRatio: Boolean = true,
    val renderDpi: Int = 300
)

object PdfGutterProcessor {

    private const val MM_TO_PT = 72f / 25.4f

    private val A4_WIDTH_PT = 595f
    private val A4_HEIGHT_PT = 842f

    private const val A4_TOLERANCE_PT = 3f

    private data class PageMargins(
        val leftMm: Float,
        val rightMm: Float,
        val topMm: Float,
        val bottomMm: Float
    )

    fun validateA4AndGetPageCount(
        context: Context,
        inputUri: Uri
    ): Int {
        val resolver = context.contentResolver

        val pfd = resolver.openFileDescriptor(inputUri, "r")
            ?: throw IOException("Could not open input PDF")

        return pfd.use { fd ->
            val renderer = PdfRenderer(fd)

            try {
                val count = renderer.pageCount

                if (count == 0) {
                    throw IOException("PDF has no pages")
                }

                for (pageIndex in 0 until count) {
                    val page = renderer.openPage(pageIndex)
                    try {
                        assertA4Portrait(
                            width = page.width.toFloat(),
                            height = page.height.toFloat(),
                            pageNumber = pageIndex + 1
                        )
                    } finally {
                        page.close()
                    }
                }

                count
            } finally {
                renderer.close()
            }
        }
    }

    fun writeMarginatedPdf(
        context: Context,
        inputUri: Uri,
        outputStream: OutputStream,
        options: MarginOptions,
        shouldIncludePage: (Int) -> Boolean = { true },
        isCancelled: () -> Boolean = { false }
    ): Int {
        require(options.renderDpi >= 72 && options.renderDpi <= 600) {
            "Render DPI must be between 72 and 600"
        }

        val resolver = context.contentResolver

        val inputPfd = resolver.openFileDescriptor(inputUri, "r")
            ?: throw IOException("Could not open input PDF")

        return inputPfd.use { pfd ->
            val renderer = PdfRenderer(pfd)

            try {
                if (renderer.pageCount == 0) {
                    throw IOException("PDF has no pages")
                }

                val pdfDocument = PdfDocument()
                var writtenPages = 0

                try {
                    for (pageIndex in 0 until renderer.pageCount) {
                        if (isCancelled()) {
                            throw CancellationException("Print cancelled")
                        }

                        if (!shouldIncludePage(pageIndex)) {
                            continue
                        }

                        val rendererPage = renderer.openPage(pageIndex)

                        try {
                            val srcWidth = rendererPage.width.toFloat()
                            val srcHeight = rendererPage.height.toFloat()

                            assertA4Portrait(
                                width = srcWidth,
                                height = srcHeight,
                                pageNumber = pageIndex + 1
                            )

                            val margins = marginsFor(pageIndex, options)

                            val destination = destinationRectWithScale(
                                srcWidth = srcWidth,
                                srcHeight = srcHeight,
                                margins = margins,
                                keepAspectRatio = options.keepAspectRatio
                            )

                            val destRect = destination.first
                            val qualityScale = destination.second

                            val effectiveDpi = (options.renderDpi * max(1f, qualityScale))
                                .toInt()
                                .coerceAtMost(450)

                            val bitmap = renderPageToBitmap(
                                page = rendererPage,
                                dpi = effectiveDpi
                            )

                            writtenPages++

                            val pageInfo = PdfDocument.PageInfo.Builder(
                                Rect(
                                    0,
                                    0,
                                    A4_WIDTH_PT.toInt(),
                                    A4_HEIGHT_PT.toInt()
                                ),
                                writtenPages
                            ).create()

                            val page = pdfDocument.startPage(pageInfo)
                            val canvas = page.canvas

                            canvas.drawColor(Color.WHITE)

                            val paint = Paint(
                                Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG
                            )

                            val srcRect = Rect(
                                0,
                                0,
                                bitmap.width,
                                bitmap.height
                            )

                            canvas.drawBitmap(
                                bitmap,
                                srcRect,
                                destRect,
                                paint
                            )

                            pdfDocument.finishPage(page)
                            bitmap.recycle()

                        } finally {
                            rendererPage.close()
                        }
                    }

                    if (writtenPages == 0) {
                        throw IOException("No pages selected")
                    }

                    pdfDocument.writeTo(outputStream)
                    writtenPages

                } finally {
                    pdfDocument.close()
                }

            } finally {
                renderer.close()
            }
        }
    }

    private fun assertA4Portrait(width: Float, height: Float, pageNumber: Int) {
        val okWidth = abs(width - A4_WIDTH_PT) <= A4_TOLERANCE_PT
        val okHeight = abs(height - A4_HEIGHT_PT) <= A4_TOLERANCE_PT

        if (!okWidth || !okHeight) {
            throw IllegalArgumentException(
                "Page $pageNumber is not A4 portrait. Found ${width} x ${height} pt."
            )
        }
    }

    private fun marginsFor(
        pageIndex: Int,
        options: MarginOptions
    ): PageMargins {
        val firstSide = (pageIndex % 2 == 0) != options.swapOddEven

        return when (options.edge) {
            DuplexEdge.LONG_EDGE -> {
                val left = if (firstSide) options.innerMm else options.outerMm
                val right = if (firstSide) options.outerMm else options.innerMm

                PageMargins(
                    leftMm = left,
                    rightMm = right,
                    topMm = options.topMm,
                    bottomMm = options.bottomMm
                )
            }

            DuplexEdge.SHORT_EDGE -> {
                val topExtra = if (firstSide) options.innerMm else options.outerMm
                val bottomExtra = if (firstSide) options.outerMm else options.innerMm

                PageMargins(
                    leftMm = 0f,
                    rightMm = 0f,
                    topMm = options.topMm + topExtra,
                    bottomMm = options.bottomMm + bottomExtra
                )
            }
        }
    }

    private fun destinationRectWithScale(
        srcWidth: Float,
        srcHeight: Float,
        margins: PageMargins,
        keepAspectRatio: Boolean
    ): Pair<RectF, Float> {
        val leftPt = margins.leftMm * MM_TO_PT
        val topPt = margins.topMm * MM_TO_PT
        val rightPt = margins.rightMm * MM_TO_PT
        val bottomPt = margins.bottomMm * MM_TO_PT

        val target = RectF(
            leftPt,
            topPt,
            A4_WIDTH_PT - rightPt,
            A4_HEIGHT_PT - bottomPt
        )

        if (target.width() <= 1f || target.height() <= 1f) {
            throw IllegalArgumentException("Margins leave no visible page area")
        }

        if (!keepAspectRatio) {
            val scale = max(
                target.width() / srcWidth,
                target.height() / srcHeight
            )
            return target to scale
        }

        val scale = min(
            target.width() / srcWidth,
            target.height() / srcHeight
        )

        if (scale <= 0f) {
            throw IllegalArgumentException("Invalid margin scale")
        }

        val drawWidth = srcWidth * scale
        val drawHeight = srcHeight * scale

        val left = target.left + (target.width() - drawWidth) / 2f
        val top = target.top + (target.height() - drawHeight) / 2f

        val rect = RectF(
            left,
            top,
            left + drawWidth,
            top + drawHeight
        )

        return rect to scale
    }

    private fun renderPageToBitmap(
        page: PdfRenderer.Page,
        dpi: Int
    ): Bitmap {
        val scale = dpi / 72f

        val width = (page.width * scale).toInt().coerceAtLeast(1)
        val height = (page.height * scale).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        page.render(
            bitmap,
            null,
            null,
            PdfRenderer.Page.RENDER_MODE_FOR_PRINT
        )

        return bitmap
    }
}

class MarginPrintDocumentAdapter(
    private val context: Context,
    private val inputUri: Uri,
    private val options: MarginOptions,
    private val pageCount: Int
) : PrintDocumentAdapter() {

    @Volatile
    private var effectiveOptions = options

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }

        effectiveOptions = when (newAttributes?.duplex) {
            PrintAttributes.DUPLEX_MODE_LONG_EDGE -> {
                options.copy(edge = DuplexEdge.LONG_EDGE)
            }
            PrintAttributes.DUPLEX_MODE_SHORT_EDGE -> {
                options.copy(edge = DuplexEdge.SHORT_EDGE)
            }
            else -> options
        }

        val changed = newAttributes != oldAttributes

        val info = PrintDocumentInfo.Builder("custom-margin-a4.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(pageCount)
            .build()

        callback?.onLayoutFinished(info, changed)
    }

    override fun onWrite(
        pages: Array<PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback
    ) {
        if (cancellationSignal.isCanceled) {
            callback.onWriteCancelled()
            return
        }

        Thread {
            try {
                val out = FileOutputStream(destination.fileDescriptor)

                val written = PdfGutterProcessor.writeMarginatedPdf(
                    context = context,
                    inputUri = inputUri,
                    outputStream = out,
                    options = effectiveOptions,
                    shouldIncludePage = { pageIndex ->
                        isPageRequested(pageIndex, pages)
                    },
                    isCancelled = {
                        cancellationSignal.isCanceled
                    }
                )

                out.flush()

                if (cancellationSignal.isCanceled) {
                    callback.onWriteCancelled()
                } else {
                    if (written <= 0) {
                        callback.onWriteFailed("No pages to print")
                    } else {
                        val resultRanges = if (pages.isEmpty()) {
                            arrayOf(PageRange.ALL_PAGES)
                        } else {
                            pages.map { it }.toTypedArray()
                        }

                        callback.onWriteFinished(resultRanges)
                    }
                }

            } catch (e: CancellationException) {
                callback.onWriteCancelled()
            } catch (e: Throwable) {
                callback.onWriteFailed(e.message ?: "Print failed")
            }
        }.start()
    }

    private fun isPageRequested(
        pageIndex: Int,
        ranges: Array<PageRange>
    ): Boolean {
        if (ranges.isEmpty()) return true

        return ranges.any { range ->
            pageIndex >= range.start && pageIndex <= range.end
        }
    }
}
