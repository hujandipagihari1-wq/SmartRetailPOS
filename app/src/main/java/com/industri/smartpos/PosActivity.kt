package com.industri.smartpos

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Locale

class PosActivity : AppCompatActivity() {

    private lateinit var etBarcode: EditText
    private lateinit var etTotalBelanja: EditText
    private lateinit var btnProcessPayment: Button
    private lateinit var pbPosLoading: ProgressBar
    private lateinit var cardPosResult: MaterialCardView
    private lateinit var tvPosStatus: TextView
    private lateinit var tvPosDetails: TextView
    private lateinit var rgSimulation: RadioGroup

    // ================================================================
    // TUGAS 1: Max Retry Threshold
    // Berapa kali tombol "COBA LAGI" di Snackbar boleh ditekan berturut-turut
    // sebelum sistem menyerah dan beralih ke mode pembayaran tunai manual.
    // ================================================================
    private var retryAttempt = 0
    private var barcodeTerakhir: String? = null

    // =============================
    // TUGAS 2: Custom Authorization Exception
    // Limit otorisasi kasir mandiri + PIN supervisor untuk unlocking.
    // =============================
    private val limitKasirMandiri = 10_000_000.0
    private val supervisorPinBenar = "2468"
    private val hargaProdukDefault = 21_500.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pos)

        etBarcode = findViewById(R.id.etBarcode)
        etTotalBelanja = findViewById(R.id.etTotalBelanja)
        btnProcessPayment = findViewById(R.id.btnProcessPayment)
        pbPosLoading = findViewById(R.id.pbPosLoading)
        cardPosResult = findViewById(R.id.cardPosResult)
        tvPosStatus = findViewById(R.id.tvPosStatus)
        tvPosDetails = findViewById(R.id.tvPosDetails)
        rgSimulation = findViewById(R.id.rgSimulation)

        btnProcessPayment.setOnClickListener {
            val barcode = etBarcode.text.toString().trim()
            if (barcode.isEmpty()) {
                Toast.makeText(this, "Barcode wajib diisi atau dipindai", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Pindai barang baru -> urutan percobaan dihitung dari nol kembali.
            if (barcode != barcodeTerakhir) {
                Timber.i("Barcode baru dipindai: %s. Reset penghitung retry.", barcode)
                resetRetryCounter()
                barcodeTerakhir = barcode
            }

            val totalBelanja = etTotalBelanja.text.toString().trim().toDoubleOrNull() ?: 0.0
            eksekusiTransaksi(barcode, totalBelanja)
        }
    }

    private fun eksekusiTransaksi(barcode: String, totalBelanja: Double) {
        pbPosLoading.visibility = View.VISIBLE
        cardPosResult.visibility = View.GONE
        btnProcessPayment.isEnabled = false

        // Coroutine Exception Handler: Pengaman terakhir agar aplikasi tidak force-close
        val coroutineExceptionHandler = CoroutineExceptionHandler { _, exception ->
            Timber.e(exception, "FATAL COROUTINE ERROR TERTANGKAP: %s", exception.message)
            runOnUiThread {
                pbPosLoading.visibility = View.GONE
                btnProcessPayment.isEnabled = true
                tampilkanSnackbarError(
                    "Kesalahan sistem tak terduga: ${exception.localizedMessage}",
                    canRetry = false
                )
            }
        }

        lifecycleScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            Timber.d("Memulai alur pembayaran untuk Barcode: %s", barcode)

            // Menggunakan idiom runCatching untuk menangani logika transaksi perbankan
            val hasil = runCatching {
                delay(1200) // Simulasi latensi jaringan

                // Simulasi kondisi berdasarkan pilihan RadioButton
                when (rgSimulation.checkedRadioButtonId) {
                    R.id.rbBarcodeError -> {
                        throw ProductBarcodeNotFoundException(barcode)
                    }

                    R.id.rbNetworkTimeout -> {
                        throw PaymentGatewayTimeoutException("QRIS Bank Settlement")
                    }

                    // ---------- TUGAS 2: Pemeriksaan limit kasir ----------
                    // Total belanja di atas limit -> lempar Custom Exception
                    // supaya ditangani berjenjang lewat onFailure.
                    R.id.rbLimitExceeded -> {
                        requireTotalWithinLimit(totalBelanja, limitKasirMandiri)
                        buatTransaksi(
                            barcode = barcode,
                            totalAmount = if (totalBelanja > 0.0) totalBelanja else hargaProdukDefault
                        )
                    }

                    else -> {
                        buatTransaksi(
                            barcode = barcode,
                            totalAmount = if (totalBelanja > 0.0) totalBelanja else hargaProdukDefault
                        )
                    }
                }
            }

            withContext(Dispatchers.Main) {
                pbPosLoading.visibility = View.GONE
                btnProcessPayment.isEnabled = true

                hasil.onSuccess { trx ->
                    Timber.i("Transaksi kasir berhasil dicatat: %s", trx.transactionId)

                    // ---------- TUGAS 1: Sukses = chances baru ----------
                    resetRetryCounter()

                    cardPosResult.visibility = View.VISIBLE
                    tvPosStatus.text = "TRANSAKSI BERHASIL (LUNAS)"
                    tvPosDetails.text = """
                        ID Transaksi: ${trx.transactionId}
                        Produk      : ${trx.productName}
                        Total Bayar : Rp %,.2f
                        Waktu       : Baru Saja
                    """.trimIndent().format(trx.totalAmount)
                }.onFailure { err ->
                    Timber.w("Transaksi kasir ditolak / gagal: %s", err.message)
                    when (err) {
                        // ---------- TUGAS 2: tangkap eksepsi otorisasi ----------
                        is CashierLimitExceededException -> {
                            Timber.w(
                                err,
                                "Otorisasi supervisor dibutuhkan. Total=%,.2f, Limit=%,.2f",
                                totalBelanja,
                                limitKasirMandiri
                            )
                            tampilkanDialogSupervisor(totalBelanja)
                        }

                        is ProductBarcodeNotFoundException -> {
                            tampilkanSnackbarError(
                                err.message ?: "Barcode tidak ditemukan",
                                canRetry = false
                            )
                        }

                        is PaymentGatewayTimeoutException -> {
                            tanganiKegagalanJaringan(err.message, totalBelanja)
                        }

                        else -> {
                            tampilkanSnackbarError(
                                "Kegagalan operasional: ${err.message}",
                                canRetry = retryAttempt < MAX_RETRY_ATTEMPT
                            )
                        }
                    }
                }
            }
        }
    }

    private fun buatTransaksi(barcode: String, totalAmount: Double) = PosTransaction(
        transactionId = "TRX-2026-9901",
        barcode = barcode,
        productName = "Susu UHT Full Cream 1 Liter",
        totalAmount = totalAmount,
        status = "SETTLED_SUCCESS"
    )

    // ================================================================
    // TUGAS 1: Retry throttle + fallback mode
    // ================================================================

    /**
     * Setiap error jaringan baru menaikkan penghitung. Selama belum mencapai
     * batas, Snackbar tetap menyediakan tombol "COBA LAGI". Begitu
     * [retryAttempt] menyentuh batas, retry dihentikan dan kasir diarahkan ke
     * pembayaran tunai manual lewat dialog fallback.
     */
    private fun tanganiKegagalanJaringan(pesanGateway: String?, totalBelanja: Double) {
        if (retryAttempt >= MAX_RETRY_ATTEMPT) {
            Timber.e(
                "Batas percobaan %d tercapai. Retry dihentikan, beralih ke mode fallback tunai.",
                MAX_RETRY_ATTEMPT
            )
            tampilkanDialogFallbackTunai(pesanGateway)
        } else {
            Timber.i("Kegagalan jaringan ke-%d dari %d. Retry masih diizinkan.", retryAttempt, MAX_RETRY_ATTEMPT)
            tampilkanSnackbarError(
                pesanGateway ?: "Koneksi gateway terputus",
                canRetry = true,
                totalBelanja = totalBelanja
            )
        }
    }

    private fun resetRetryCounter() {
        retryAttempt = 0
        val tvCounter = findViewById<TextView>(R.id.tvRetryCounter)
        tvCounter.visibility = View.GONE
        Timber.d("Penghitung retry direset ke 0.")
    }

    private fun naikkanRetryCounter() {
        retryAttempt += 1
        val tvCounter = findViewById<TextView>(R.id.tvRetryCounter)
        tvCounter.visibility = View.VISIBLE
        tvCounter.text = "Percobaan koneksi: $retryAttempt dari $MAX_RETRY_ATTEMPT"
        Timber.i("Percobaan ulang ke-%d dari %d", retryAttempt, MAX_RETRY_ATTEMPT)
    }

    private fun tampilkanSnackbarError(
        pesan: String,
        canRetry: Boolean,
        totalBelanja: Double = 0.0
    ) {
        val root = findViewById<View>(R.id.coordinatorLayout)
        val snackbar = Snackbar.make(root, pesan, Snackbar.LENGTH_LONG)
        snackbar.setBackgroundTint(Color.parseColor("#991B1B")) // Merah gelap
        snackbar.setTextColor(Color.WHITE)

        if (canRetry) {
            snackbar.setAction("COBA LAGI") {
                val barcode = etBarcode.text.toString().trim()
                if (barcode.isNotEmpty()) {
                    naikkanRetryCounter()
                    eksekusiTransaksi(barcode, totalBelanja)
                }
            }
            snackbar.setActionTextColor(Color.parseColor("#FEF08A")) // Kuning
        }
        snackbar.show()
    }

    /**
     * Fallback mode: dialog ini hanya muncul ketika retry sudah habis.
     * Kasir diberi rekomendasi tegas untuk mengalihkan ke pembayaran tunai manual.
     */
    private fun tampilkanDialogFallbackTunai(pesanGateway: String?) {
        val builder = AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_fallback_title))
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setMessage(
                "Payment gateway tidak dapat dihubungi setelah $MAX_RETRY_ATTEMPT percobaan berturut-turut.\n\n" +
                    "Pesan sistem: ${pesanGateway ?: "tidak diketahui"}\n\n" +
                    "Rekomendasi: hentikan percobaan otomatis, alihkan pelanggan ke PEMBAYARAN TUNAI MANUAL, " +
                    "lalu lakukan rekonsiliasi transaksi dengan supervisor pada akhir shift."
            )
            .setPositiveButton("ALIHKAN KE TUNAI") { dialog, _ ->
                Timber.i("Kasir menyetujui fallback ke pembayaran tunai manual.")
                dialog.dismiss()
                cardPosResult.visibility = View.VISIBLE
                tvPosStatus.text = "TRANSAKSI TUNAI MANUAL"
                tvPosDetails.text = "Status : Menunggu input nominal tunai\nSistem: Modul pembayaran elektronik dinonaktifkan sementara"
                Snackbar.make(
                    findViewById(R.id.coordinatorLayout),
                    "Pembayaran tunai manual diaktifkan. Jangan tutup aplikasi.",
                    Snackbar.LENGTH_LONG
                ).show()
            }
            .setNegativeButton("TUTUP", null)

        builder.create().show()
    }

    // ================================================================
    // TUGAS 2: Dialog PIN Supervisor untuk otorisasi transaksi besar
    // ================================================================

    private fun tampilkanDialogSupervisor(totalBelanja: Double) {
        val inputPin = EditText(this).apply {
            hint = getString(R.string.dialog_supervisor_pin)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            isSingleLine = true
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_supervisor_title))
            .setIcon(android.R.drawable.ic_lock_lock)
            .setMessage(
                getString(R.string.dialog_supervisor_message) +
                    "\n\nTotal transaksi  : Rp %,.2f".format(Locale.US, totalBelanja) +
                    "\nLimit kasir       : Rp %,.2f".format(Locale.US, limitKasirMandiri)
            )
            .setView(inputPin)
            .setPositiveButton(getString(R.string.dialog_supervisor_confirm), null)
            .setNegativeButton(getString(R.string.dialog_supervisor_cancel)) { dialog, _ ->
                Timber.w("Otorisasi supervisor dibatalkan oleh kasir.")
                dialog.dismiss()
            }
            .create()

        dialog.show()

        // Listener dipasang setelah show() supaya klik tombol tidak menutup dialog
        // sebelum PIN sempat diperiksa.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val pin = inputPin.text.toString().trim()
            if (pin == supervisorPinBenar) {
                Timber.i("PIN Supervisor valid. Otorisasi transaksi dicairkan.")
                dialog.dismiss()
                tampilkanHasilOtorisasi(totalBelanja)
            } else {
                Timber.e("PIN Supervisor tidak valid untuk total %,2f", totalBelanja)
                inputPin.text.clear()
                inputPin.error = getString(R.string.dialog_supervisor_pin_error)
                Snackbar.make(
                    findViewById(R.id.coordinatorLayout),
                    getString(R.string.dialog_supervisor_pin_error),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun tampilkanHasilOtorisasi(totalBelanja: Double) {
        cardPosResult.visibility = View.VISIBLE
        tvPosStatus.text = "TRANSAKSI BERHASIL (LUNAS)"
        tvPosDetails.text = """
            ID Transaksi : TRX-2026-9901
            Produk       : Susu UHT Full Cream 1 Liter
            Total Bayar  : Rp %,.2f
            Otorisasi    : Disetujui Supervisor (PIN)
            Waktu        : Baru Saja
        """.trimIndent().format(totalBelanja)
    }

    companion object {
        /** Batas maksimal retry berturut-turut sebelum mode fallback aktif. */
        const val MAX_RETRY_ATTEMPT = 3
    }
}