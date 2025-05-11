package com.muslim.plus

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.text.SimpleDateFormat
import java.util.*

data class MuslimSalatResponse(@SerializedName("items") val items: List<PrayerItem>)
data class PrayerItem(
    @SerializedName("fajr")    val fajr: String,
    @SerializedName("dhuhr")   val dhuhr: String,
    @SerializedName("asr")     val asr: String,
    @SerializedName("maghrib") val maghrib: String,
    @SerializedName("isha")    val isha: String
)

interface MuslimSalatService {
    @GET("{location}.json")
    fun getDailyTimes(@Path(value="location", encoded=true) location: String)
            : retrofit2.Call<MuslimSalatResponse>
}

class PrayerTimeActivity : AppCompatActivity() {

    companion object {
        private const val PERM_REQUEST_CODE = 100
    }

    private lateinit var mapView: MapView
    private lateinit var locOverlay: MyLocationNewOverlay
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var tvStatus: TextView
    private lateinit var tvFajr: TextView
    private lateinit var tvDhuhr: TextView
    private lateinit var tvAsr: TextView
    private lateinit var tvMaghrib: TextView
    private lateinit var tvIsha: TextView
    private lateinit var btnBack: ImageButton
    private lateinit var btnRefresh: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load osmdroid config
        Configuration.getInstance()
            .load(this, PreferenceManager.getDefaultSharedPreferences(this))

        setContentView(R.layout.activity_prayer_time)

        // Inisialisasi views
        mapView     = findViewById(R.id.mapview)
        tvStatus    = findViewById(R.id.tvStatus)
        tvFajr      = findViewById(R.id.tvFajr)
        tvDhuhr     = findViewById(R.id.tvDhuhr)
        tvAsr       = findViewById(R.id.tvAsr)
        tvMaghrib   = findViewById(R.id.tvMaghrib)
        tvIsha      = findViewById(R.id.tvIsha)
        btnBack     = findViewById(R.id.btnBack)
        btnRefresh  = findViewById(R.id.btnRefresh)

        // Setup MapView
        mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
        }

        // Back finishes activity
        btnBack.setOnClickListener { finish() }

        // Refresh: clear last date so fetch will run
        btnRefresh.setOnClickListener {
            PreferenceManager.getDefaultSharedPreferences(this)
                .edit().remove("last_update_date").apply()
            initLocationAndFetch()
        }

        // Cek cache hari ini
        val prefs    = PreferenceManager.getDefaultSharedPreferences(this)
        val today    = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastDate = prefs.getString("last_update_date", "")
        val lastCity = prefs.getString("last_update_raw_city", "")

        if (lastDate == today && !lastCity.isNullOrEmpty()) {
            // Tampilkan cache
            tvStatus.text  = "Jadwal shalat untuk $lastCity"
            tvFajr.text    = prefs.getString("pref_fajr", "Fajr: –")
            tvDhuhr.text   = prefs.getString("pref_dhuhr", "Dhuhr: –")
            tvAsr.text     = prefs.getString("pref_asr", "Asr: –")
            tvMaghrib.text = prefs.getString("pref_maghrib", "Maghrib: –")
            tvIsha.text    = prefs.getString("pref_isha", "Isha: –")
        } else {
            // Perlu fetch baru
            initLocationAndFetch()
        }
    }

    private fun initLocationAndFetch() {
        tvStatus.text = "Menunggu data lokasi…"
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            setupLocationOverlay()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                PERM_REQUEST_CODE
            )
        }
    }

    private fun setupLocationOverlay() {
        locOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), mapView).apply {
            enableMyLocation()
            runOnFirstFix {
                runOnUiThread { fetchCityAndMaybePrayers() }
            }
        }
        mapView.overlays.add(locOverlay)
    }

    private fun fetchCityAndMaybePrayers() {
        val fix = locOverlay.myLocation
        if (fix != null) {
            scope.launch {
                val city = withContext(Dispatchers.IO) {
                    geocodeCity(fix.latitude, fix.longitude)
                }
                if (city != null) handlePrayerTimesForCity(city)
                else tvStatus.text = "Gagal mendapatkan nama kota"
            }
        }
    }

    private fun handlePrayerTimesForCity(rawCity: String) {
        val prefs         = PreferenceManager.getDefaultSharedPreferences(this)
        val today         = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val formattedCity = formatCityName(rawCity)

        tvStatus.text = "Memuat jadwal shalat untuk $rawCity…"
        val api = Retrofit.Builder()
            .baseUrl("https://muslimsalat.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MuslimSalatService::class.java)

        scope.launch {
            val resp = withContext(Dispatchers.IO) {
                api.getDailyTimes(formattedCity).execute()
            }
            withContext(Dispatchers.Main) {
                if (resp.isSuccessful) {
                    resp.body()?.items?.firstOrNull()?.let {
                        // Update UI
                        tvFajr.text    = "Fajr: ${it.fajr}"
                        tvDhuhr.text   = "Dhuhr: ${it.dhuhr}"
                        tvAsr.text     = "Asr: ${it.asr}"
                        tvMaghrib.text = "Maghrib: ${it.maghrib}"
                        tvIsha.text    = "Isha: ${it.isha}"
                        tvStatus.text  = "Jadwal shalat untuk $rawCity"

                        // Simpan ke prefs
                        prefs.edit()
                            .putString("last_update_date", today)
                            .putString("last_update_raw_city", rawCity)
                            .putString("pref_fajr",    tvFajr.text.toString())
                            .putString("pref_dhuhr",   tvDhuhr.text.toString())
                            .putString("pref_asr",     tvAsr.text.toString())
                            .putString("pref_maghrib", tvMaghrib.text.toString())
                            .putString("pref_isha",    tvIsha.text.toString())
                            .apply()
                    } ?: run {
                        tvStatus.text = "Data shalat kosong"
                    }
                } else {
                    tvStatus.text = "Error: ${resp.code()}"
                }
            }
        }
    }

    private fun geocodeCity(lat: Double, lon: Double): String? = try {
        Geocoder(this, Locale.getDefault())
            .getFromLocation(lat, lon, 1)
            ?.firstOrNull()
            ?.locality
    } catch (_: Exception) {
        null
    }

    private fun formatCityName(raw: String) = raw
        .replace(Regex("(?i)^(kecamatan|kabupaten|kota)\\s+"), "")
        .replace("\\s+".toRegex(), "")
        .lowercase(Locale.getDefault())

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERM_REQUEST_CODE &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            setupLocationOverlay()
        } else {
            tvStatus.text = "Izin lokasi ditolak"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        mapView.onDetach()
    }
}