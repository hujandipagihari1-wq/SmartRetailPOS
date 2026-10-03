package com.industri.smartpos

import java.io.IOException
import java.util.Locale

// 1. Eksepsi saat barcode barang tidak ada di basis data toko
class ProductBarcodeNotFoundException(val barcode: String) :
    Exception("Barang dengan barcode [$barcode] tidak terdaftar pada katalog sistem.")

// 2. Eksepsi saat jaringan payment gateway perbankan timeout
class PaymentGatewayTimeoutException(val gatewayName: String) :
    IOException("Layanan pembayaran $gatewayName tidak merespons dalam 15 detik.")

// 3. Eksepsi saat saldo limit kasir tidak mencukupi
class CashierLimitExceededException(val maxLimit: Double) :
    Exception(
        "Total transaksi melebihi limit otorisasi kasir mandiri (Maksimal Rp %,.0f)"
            .format(Locale.US, maxLimit)
    )

/**
 * Aturan threshold limit kasir mandiri (Tugas 2).
 *
 * Sengaja dibuat sebagai fungsi terpisah supaya logika_threshold yang sama
 * dipakai oleh [PosActivity] bisa diuji langsung di JVM unit test tanpa
 * bergantung pada Android framework.
 *
 * @throws CashierLimitExceededException bila total belanja melampaui limit.
 */
fun requireTotalWithinLimit(totalBelanja: Double, limitKasirMandiri: Double) {
    if (totalBelanja > limitKasirMandiri) {
        throw CashierLimitExceededException(limitKasirMandiri)
    }
}