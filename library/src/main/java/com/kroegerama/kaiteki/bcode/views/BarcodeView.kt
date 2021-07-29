package com.kroegerama.kaiteki.bcode.views

import android.content.Context
import android.util.AttributeSet
import android.util.Size
import android.util.SizeF
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.withStyledAttributes
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import com.google.zxing.BarcodeFormat
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.Result
import com.kroegerama.kaiteki.bcode.*
import com.kroegerama.kaiteki.bcode.databinding.BarcodeViewBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class BarcodeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), ResultListener {

    private lateinit var executor: ExecutorService
    private var lifecycleOwner: LifecycleOwner? = null

    private val binding = BarcodeViewBinding.inflate(LayoutInflater.from(context), this)

    private val cameraProvider by lazy { ProcessCameraProvider.getInstance(context).get() }

    private var bufferSize = SizeF(0f, 0f)

    private var listener: BarcodeResultListener? = null

    private val barcodeReader by lazy { MultiFormatReader() }

    private val analyzer by lazy { BarcodeAnalyzer(this, barcodeReader, binding.customViewFinder) }

    private val resultDebouncer = Debouncer(500)

    init {
        keepScreenOn = true

        context.withStyledAttributes(attrs, Style.BarcodeView, defStyleAttr) {
            analyzer.inverted = getBoolean(Style.BarcodeView_barcodeInverted, false)
        }
    }

    override fun onResult(result: Result, imageWidth: Int, imageHeight: Int, imageRotation: Int) {

        val d = resultDebouncer {
            listener?.onBarcodeResult(result)
        }
        if (d == true) {
            // dialog/fragment will be dismissed -> do not send any more events
            listener = null
        }
    }

    override fun onNoResult() = Unit

    fun setBarcodeResultListener(listener: BarcodeResultListener) {
        this.listener = listener
    }

    /**
     * enable scanning of inverted barcodes (e.g. white QR Code on black background)
     */
    fun setBarcodeInverted(inverted: Boolean) {
        analyzer.inverted = inverted
    }

    fun bindToLifecycle(owner: LifecycleOwner) {
        if (lifecycleOwner != owner) {
            lifecycleOwner = owner

            executor = Executors.newSingleThreadExecutor()
            val preview = Preview.Builder()
                .build()
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()

            val analysis = ImageAnalysis.Builder().apply {
                setTargetResolution(Size(640, 480))
                setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            }.build().apply {
                setAnalyzer(executor, analyzer)
            }
            cameraProvider.unbindAll()
            preview.setSurfaceProvider(binding.previewView.surfaceProvider)
            cameraProvider.bindToLifecycle(owner, cameraSelector, preview, analysis)

            owner.lifecycle.addObserver(object : LifecycleObserver {
                @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
                fun onStop() {
                    cameraProvider.unbindAll()
                }

                @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                fun onDestroy() {
                    executor.shutdown()
                    owner.lifecycle.removeObserver(this)
                }
            })
        }
    }

    fun setFormats(formats: List<BarcodeFormat>) = barcodeReader.setHints(
        mapOf(
            DecodeHintType.POSSIBLE_FORMATS to formats
        )
    )
}

private operator fun Size.div(other: Int): Size = Size(width / other, height / other)
