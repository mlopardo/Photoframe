package com.zambiotica.photoframe

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.preference.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Pantalla única del portarretrato.
 *
 * Reglas de convivencia con Home Assistant:
 *  - La pantalla se mantiene encendida SOLO mientras la actividad está al frente,
 *    así HA la puede dormir con `input keyevent 223`.
 *  - Con la pantalla apagada el pase se detiene (onPause), y se retoma al despertar.
 *  - HA recarga las fotos con:
 *      am start -n com.zambiotica.photoframe/.MainActivity --ez reload true
 */
class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RELOAD = "reload"
        private const val CONTROLS_TIMEOUT_MS = 3_000L
        private const val PERMISSION_REQUEST = 101
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var slotA: FrameLayout
    private lateinit var slotB: FrameLayout
    private lateinit var backgroundA: ImageView
    private lateinit var backgroundB: ImageView
    private lateinit var photoA: ImageView
    private lateinit var photoB: ImageView
    private lateinit var clockBox: View
    private lateinit var timeText: TextView
    private lateinit var dateText: TextView
    private lateinit var controls: View
    private lateinit var messageText: TextView
    private lateinit var photoDateText: TextView

    private var photos: List<Uri> = emptyList()
    private var fingerprint: String = ""
    private var bag = ShuffleBag(0)
    private var sequentialIndex = -1
    private var frontIsA = false
    private var paused = false
    private var running = false
    private var bitmapA: Bitmap? = null
    private var bitmapB: Bitmap? = null

    private val advance = Runnable { showNext(forward = true) }
    private val clockTick = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 20_000L)
        }
    }

    private var resumedAtMs = 0L
    private var touchDownMs = 0L
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var longPressHandled = false

    private val openSettings = Runnable {
        longPressHandled = true
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Escribe los valores por defecto la primera vez, para que la pantalla de Ajustes
        // muestre exactamente lo que la app esta usando (antes se veian todos en apagado).
        PreferenceManager.setDefaultValues(this, R.xml.prefs, false)
        Prefs.persistKenBurnsDefault(this)
        setContentView(R.layout.activity_main)

        slotA = findViewById(R.id.slot_a)
        slotB = findViewById(R.id.slot_b)
        backgroundA = findViewById(R.id.background_a)
        backgroundB = findViewById(R.id.background_b)
        photoA = findViewById(R.id.photo_a)
        photoB = findViewById(R.id.photo_b)
        clockBox = findViewById(R.id.clock_box)
        timeText = findViewById(R.id.time_text)
        dateText = findViewById(R.id.date_text)
        controls = findViewById(R.id.controls)
        messageText = findViewById(R.id.message_text)
        photoDateText = findViewById(R.id.photo_date_text)

        findViewById<View>(R.id.button_previous).setOnClickListener { showNext(forward = false) }
        findViewById<View>(R.id.button_next).setOnClickListener { showNext(forward = true) }
        findViewById<View>(R.id.button_play_pause).setOnClickListener { togglePause() }

        findViewById<View>(R.id.root).setOnTouchListener { _, event -> handleTouch(event) }

        handleReload(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleReload(intent)
    }

    private fun handleReload(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_RELOAD, false) == true) {
            fingerprint = ""   // fuerza el reescaneo en el próximo onResume
            if (running) loadPhotos(restart = true)
        }
    }

    override fun onResume() {
        super.onResume()
        running = true
        resumedAtMs = SystemClock.elapsedRealtime()
        applyOrientation()
        applyImmersiveMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyClockSettings()
        handler.post(clockTick)
        ensurePermissionAndLoad()
    }

    override fun onPause() {
        super.onPause()
        running = false
        handler.removeCallbacks(advance)
        handler.removeCallbacks(clockTick)
        handler.removeCallbacks(openSettings)
        // Sin esta bandera, HA puede dormir la pantalla con keyevent 223.
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        bitmapA?.recycle()
        bitmapB?.recycle()
    }

    // --- Fotos -----------------------------------------------------------------

    private fun ensurePermissionAndLoad() {
        val needsLegacyPermission = Build.VERSION.SDK_INT <= 32 && Prefs.folderUri(this) == null
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        if (needsLegacyPermission && !granted) {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), PERMISSION_REQUEST)
            return
        }
        loadPhotos(restart = false)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST) loadPhotos(restart = false)
    }

    private fun loadPhotos(restart: Boolean) {
        scope.launch {
            val result = withContext(Dispatchers.IO) { PhotoScanner.scan(this@MainActivity) }
            val changed = result.fingerprint != fingerprint
            fingerprint = result.fingerprint
            if (changed || restart) {
                photos = result.photos
                bag.resize(photos.size)
                sequentialIndex = -1
                if (photos.isEmpty()) {
                    showEmptyState()
                } else {
                    messageText.visibility = View.GONE
                    showNext(forward = true)
                }
            } else if (photos.isNotEmpty()) {
                scheduleNext()
            }
        }
    }

    private fun showEmptyState() {
        messageText.setText(R.string.msg_no_photos)
        messageText.visibility = View.VISIBLE
        slotA.alpha = 0f
        slotB.alpha = 0f
    }

    private fun nextIndex(forward: Boolean): Int = when (Prefs.order(this)) {
        "random" -> if (photos.isEmpty()) -1 else (photos.indices).random()
        "name" -> {
            if (photos.isEmpty()) -1 else {
                sequentialIndex = if (forward) {
                    (sequentialIndex + 1) % photos.size
                } else {
                    (sequentialIndex - 1 + photos.size) % photos.size
                }
                sequentialIndex
            }
        }
        else -> if (forward) bag.next() else bag.previous()
    }

    private fun showNext(forward: Boolean) {
        handler.removeCallbacks(advance)
        if (photos.isEmpty() || !running) return
        val index = nextIndex(forward)
        if (index !in photos.indices) return
        val uri = photos[index]

        scope.launch {
            val width = resources.displayMetrics.widthPixels
            val height = resources.displayMetrics.heightPixels
            val lowMemory = Prefs.isLowMemoryDevice(this@MainActivity)
            val bitmap = withContext(Dispatchers.IO) {
                PhotoDecoder.decode(this@MainActivity, uri, width, height, lowMemory)
            }
            if (bitmap == null) {
                scheduleNext()
                return@launch
            }
            val takenAt = if (Prefs.photoDate(this@MainActivity)) {
                withContext(Dispatchers.IO) { PhotoDecoder.readTakenAt(this@MainActivity, uri) }
            } else {
                null
            }
            val scaleMode = Prefs.scale(this@MainActivity)
            val background = if (scaleMode == "fit_blur") {
                withContext(Dispatchers.IO) { PhotoDecoder.blurredBackground(bitmap) }
            } else {
                null
            }
            display(bitmap, background, scaleMode, takenAt)
            scheduleNext()
        }
    }

    private fun display(
        bitmap: Bitmap,
        background: Bitmap?,
        scaleMode: String,
        takenAt: ExifDates.TakenAt?
    ) {
        showPhotoDate(takenAt)

        val incomingSlot = if (frontIsA) slotB else slotA
        val incomingPhoto = if (frontIsA) photoB else photoA
        val incomingBackground = if (frontIsA) backgroundB else backgroundA
        val outgoingSlot = if (frontIsA) slotA else slotB

        incomingPhoto.setImageBitmap(bitmap)
        incomingPhoto.scaleX = 1f
        incomingPhoto.scaleY = 1f
        incomingPhoto.translationX = 0f
        incomingPhoto.translationY = 0f

        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        if (scaleMode == "crop") {
            incomingPhoto.scaleType = ImageView.ScaleType.CENTER_CROP
        } else {
            // Encaja la foto, pero sin agrandarla mas alla del tope: una foto de baja
            // resolucion se ve nitida y mas chica, con el fondo difuminado alrededor.
            val scale = ScalingRules.displayScale(bitmap.width, bitmap.height, screenWidth, screenHeight)
            val matrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate(
                    (screenWidth - bitmap.width * scale) / 2f,
                    (screenHeight - bitmap.height * scale) / 2f
                )
            }
            incomingPhoto.scaleType = ImageView.ScaleType.MATRIX
            incomingPhoto.imageMatrix = matrix
        }

        if (background != null) {
            incomingBackground.setImageBitmap(background)
            incomingBackground.visibility = View.VISIBLE
        } else {
            incomingBackground.setImageDrawable(null)
            incomingBackground.visibility = View.GONE
        }

        val fade = Prefs.fadeMs(this)
        incomingSlot.animate().alpha(1f).setDuration(fade).start()
        outgoingSlot.animate().alpha(0f).setDuration(fade).start()

        if (frontIsA) {
            bitmapB?.recycle()
            bitmapB = bitmap
        } else {
            bitmapA?.recycle()
            bitmapA = bitmap
        }
        frontIsA = !frontIsA

        // El zoom solo se aplica a fotos con resolucion de sobra: en una foto chica,
        // agrandarla un 8% mas solo agranda los pixeles.
        val hasPixelsToSpare =
            ScalingRules.allowsKenBurns(bitmap.width, bitmap.height, screenWidth, screenHeight)
        if (Prefs.kenBurns(this) && hasPixelsToSpare) startKenBurns(incomingPhoto)
    }

    /** Zoom y paneo suaves. Se animan las propiedades de la vista: no se vuelve a dibujar el bitmap. */
    private fun startKenBurns(view: ImageView) {
        val duration = Prefs.intervalMs(this) + Prefs.fadeMs(this)
        val drift = resources.displayMetrics.widthPixels * 0.02f
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        view.animate()
            .scaleX(1.08f)
            .scaleY(1.08f)
            .translationX(if ((0..1).random() == 0) drift else -drift)
            .setDuration(duration)
            .withEndAction { view.setLayerType(View.LAYER_TYPE_NONE, null) }
            .start()
    }

    private fun scheduleNext() {
        handler.removeCallbacks(advance)
        if (!paused && running && photos.isNotEmpty()) {
            handler.postDelayed(advance, Prefs.intervalMs(this))
        }
    }

    private fun togglePause() {
        paused = !paused
        if (paused) handler.removeCallbacks(advance) else scheduleNext()
        showControls()
    }

    // --- Interfaz --------------------------------------------------------------

    /**
     * Toque simple = barra de controles. Toque largo y deliberado = Ajustes.
     *
     * Los toques de los primeros segundos tras encender la pantalla se descartan: en la
     * TabZambiótica un toque fantasma al despertar dejó los Ajustes al frente en vez del marco.
     */
    private fun handleTouch(event: MotionEvent): Boolean {
        val now = SystemClock.elapsedRealtime()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (InteractionRules.ignoreTouch(now, resumedAtMs)) return true
                touchDownMs = now
                touchDownX = event.x
                touchDownY = event.y
                longPressHandled = false
                handler.postDelayed(openSettings, InteractionRules.LONG_PRESS_MS)
            }

            MotionEvent.ACTION_MOVE -> {
                if (movedPx(event) > InteractionRules.MOVE_SLOP_PX) {
                    handler.removeCallbacks(openSettings)
                }
            }

            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(openSettings)
                val held = now - touchDownMs
                if (!longPressHandled && InteractionRules.isTap(held, movedPx(event))) {
                    showControls()
                }
            }

            MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(openSettings)
        }
        return true
    }

    private fun movedPx(event: MotionEvent): Int {
        val dx = event.x - touchDownX
        val dy = event.y - touchDownY
        return kotlin.math.sqrt(dx * dx + dy * dy).toInt()
    }

    private fun showControls() {
        controls.visibility = View.VISIBLE
        handler.removeCallbacks(hideControls)
        handler.postDelayed(hideControls, CONTROLS_TIMEOUT_MS)
    }

    private val hideControls = Runnable { controls.visibility = View.GONE }

    /**
     * Fecha de captura, leída del EXIF de la propia foto. Si la foto no la trae —típico de
     * imágenes reenviadas o editadas— no se muestra nada, en vez de inventar una fecha.
     */
    private fun showPhotoDate(takenAt: ExifDates.TakenAt?) {
        if (takenAt == null || !Prefs.photoDate(this)) {
            photoDateText.visibility = View.GONE
            return
        }
        val calendar = Calendar.getInstance().apply {
            clear()
            set(takenAt.year, takenAt.month - 1, takenAt.day)
        }
        photoDateText.text = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(calendar.time)

        val params = photoDateText.layoutParams as FrameLayout.LayoutParams
        params.gravity = when (OverlayRules.photoDateGravity(Prefs.clockPosition(this))) {
            "bottom_end" -> Gravity.BOTTOM or Gravity.END
            else -> Gravity.BOTTOM or Gravity.START
        }
        photoDateText.layoutParams = params
        photoDateText.visibility = View.VISIBLE
    }

    private fun applyClockSettings() {
        if (!Prefs.clock(this)) {
            clockBox.visibility = View.GONE
            return
        }
        clockBox.visibility = View.VISIBLE
        val params = clockBox.layoutParams as FrameLayout.LayoutParams
        params.gravity = when (Prefs.clockPosition(this)) {
            "bottom_start" -> Gravity.BOTTOM or Gravity.START
            "top_end" -> Gravity.TOP or Gravity.END
            "top_start" -> Gravity.TOP or Gravity.START
            else -> Gravity.BOTTOM or Gravity.END
        }
        clockBox.layoutParams = params
        updateClock()
    }

    private fun updateClock() {
        if (!Prefs.clock(this)) return
        val now = Date()
        timeText.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        dateText.text = DateFormat.getDateInstance(DateFormat.LONG, Locale.getDefault()).format(now)
    }

    private fun applyOrientation() {
        requestedOrientation = when (Prefs.orientation(this)) {
            "landscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            "portrait" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
    }

    private fun applyImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
