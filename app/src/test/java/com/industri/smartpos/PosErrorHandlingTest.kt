package com.industri.smartpos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test untuk Tugas 1 & Tugas 2 modul pertemuan 12.
 */
class PosErrorHandlingTest {

    // ---------- Tugas 1: batas retry ----------

    @Test
    fun `batas retry adalah tiga kali percobaan`() {
        assertEquals(3, PosActivity.MAX_RETRY_ATTEMPT)
    }

    @Test
    fun `simulasi retry mencapai batas menghasilkan tiga kegagalan berturut-turut`() {
        // Meniru logika tally di tanganiKegagalanJaringan()
        var retryAttempt = 0
        val kronologi = mutableListOf<Boolean>() // true = masih boleh retry

        repeat(5) {
            retryAttempt += 1
            kronologi += retryAttempt < PosActivity.MAX_RETRY_ATTEMPT
        }

        // Percobaan ke-1 dan ke-2 masih menawarkan retry,
        // percobaan ke-3 sudah memicu fallback.
        assertEquals(listOf(true, true, false), kronologi.take(3))
        assertTrue("Fallback aktif sejak percobaan ke-3", !kronologi[2])
    }

    // ---------- Tugas 2: exception otorisasi kasir ----------

    @Test
    fun `total di bawah limit tidak melempar eksepsi`() {
        val limit = 10_000_000.0
        val total = 9_999_999.0

        val hasil = runCatching { requireTotalWithinLimit(total, limit) }

        assertTrue(hasil.isSuccess)
    }

    @Test
    fun `total tepat di limit tidak melempar eksepsi`() {
        val limit = 10_000_000.0
        val total = 10_000_000.0

        val hasil = runCatching { requireTotalWithinLimit(total, limit) }

        assertTrue(hasil.isSuccess)
    }

    @Test
    fun `total di atas limit melempar CashierLimitExceededException`() {
        val limit = 10_000_000.0
        val total = 12_500_000.0

        val hasil = runCatching { requireTotalWithinLimit(total, limit) }

        assertTrue(hasil.isFailure)
        val error = hasil.exceptionOrNull()
        assertEquals("CashierLimitExceededException", error?.javaClass?.simpleName)
        assertEquals(limit, (error as CashierLimitExceededException).maxLimit, 0.0)
    }

    @Test
    fun `pesan eksepsi limit kasir terbaca jelas`() {
        val pesan = CashierLimitExceededException(10_000_000.0).message.orEmpty()

        assertEquals(
            "Total transaksi melebihi limit otorisasi kasir mandiri (Maksimal Rp 10,000,000)",
            pesan
        )
    }

    // ---------- Exception bawaan dosen ----------

    @Test
    fun `eksepsi barcode memuat barcode yang dicari`() {
        val error = ProductBarcodeNotFoundException("8991201")

        assertEquals("8991201", error.barcode)
        assertTrue(
            error.message!!.contains("tidak terdaftar pada katalog sistem")
        )
    }

    @Test
    fun `eksepsi gateway merupakan IOException`() {
        val error = PaymentGatewayTimeoutException("QRIS Bank Settlement")

        assertEquals("java.io.IOException", error.javaClass.superclass.name)
        assertEquals("QRIS Bank Settlement", error.gatewayName)
    }

    // ---------- UiState sealed class ----------

    @Test
    fun `UiState Error menyimpan pesan dan flag retry`() {
        val state = UiState.Error(
            message = "Gateway timeout",
            errorCode = 504,
            canRetry = true
        )

        assertEquals("Gateway timeout", (state as UiState.Error).message)
        assertEquals(504, state.errorCode)
        assertTrue(state.canRetry)
    }

    @Test
    fun `UiState Error tanpa kode berlaku default`() {
        val state = UiState.Error("Barcode tidak ditemukan")

        assertEquals(null, (state as UiState.Error).errorCode)
        assertTrue(state.canRetry)
    }

    @Test
    fun `UiState Success membungkus data transaksi`() {
        val trx = PosTransaction(
            transactionId = "TRX-2026-9901",
            barcode = "8991201",
            productName = "Susu UHT Full Cream 1 Liter",
            totalAmount = 21500.0,
            status = "SETTLED_SUCCESS"
        )

        val state: UiState<PosTransaction> = UiState.Success(trx)

        assertEquals(trx, (state as UiState.Success).data)
    }
}