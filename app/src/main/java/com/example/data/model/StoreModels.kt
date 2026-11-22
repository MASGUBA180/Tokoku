package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class PriceTierType(val label: String, val badgeText: String) {
    DEFAULT("Default (Umum)", "Umum"),
    GROSIR("Grosir", "Grosir"),
    MEMBER_VIP("Member VIP", "VIP");

    companion object {
        fun fromString(raw: String?): PriceTierType =
            entries.firstOrNull { it.name == raw } ?: DEFAULT
    }
}

enum class ReferenceType(val label: String) {
    CATEGORY("Kategori"),
    BRAND("Merek"),
    UNIT("Satuan");

    companion object {
        fun fromString(raw: String?): ReferenceType =
            entries.firstOrNull { it.name == raw } ?: CATEGORY
    }
}

enum class StockMovementType(val label: String) {
    MASUK("Stok Masuk"),
    KELUAR("Penjualan"),
    PENYESUAIAN("Penyesuaian");

    companion object {
        fun fromString(raw: String?): StockMovementType =
            entries.firstOrNull { it.name == raw } ?: PENYESUAIAN
    }
}

enum class PaymentMethod(val label: String) {
    TUNAI("Tunai"),
    QRIS("QRIS"),
    TRANSFER("Transfer");

    companion object {
        fun fromString(raw: String?): PaymentMethod =
            entries.firstOrNull { it.name == raw } ?: TUNAI
    }
}

@Keep
@IgnoreExtraProperties
data class StoreSettings(
    @DocumentId val id: String = "",
    val userId: String = "",
    val storeName: String = "Toko Berkah Jaya",
    val storeAddress: String = "Jl. Raya Niaga No. 18",
    val storePhone: String = "0812-3456-7890",
    val receiptFooter: String = "Terima kasih telah berbelanja!",
    val taxPercent: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StoreReference(
    @DocumentId val id: String = "",
    val userId: String = "",
    val type: String = ReferenceType.CATEGORY.name,
    val name: String = "",
    val defaultQtyPerUnit: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun referenceType(): ReferenceType = ReferenceType.fromString(type)
}

@Keep
@IgnoreExtraProperties
data class PriceUnit(
    val unitName: String = "Satuan",
    val qtyContent: Long = 1L,
    val minBuy: Long = 1L,
    val priceDefault: Long = 0L,
    val priceGrosir: Long = 0L,
    val priceMemberVip: Long = 0L
) {
    fun priceForTier(tier: PriceTierType): Long = when (tier) {
        PriceTierType.DEFAULT -> priceDefault
        PriceTierType.GROSIR -> if (priceGrosir > 0L) priceGrosir else priceDefault
        PriceTierType.MEMBER_VIP -> if (priceMemberVip > 0L) priceMemberVip else priceDefault
    }

    fun toMap(): Map<String, Any> = mapOf(
        "unitName" to unitName.trim().ifEmpty { "Satuan" },
        "qtyContent" to qtyContent.coerceAtLeast(1L),
        "minBuy" to minBuy.coerceAtLeast(1L),
        "priceDefault" to priceDefault.coerceAtLeast(0L),
        "priceGrosir" to priceGrosir.coerceAtLeast(0L),
        "priceMemberVip" to priceMemberVip.coerceAtLeast(0L)
    )
}

@Keep
@IgnoreExtraProperties
data class Product(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sku: String = "",
    val name: String = "",
    val categoryName: String = "",
    val brandName: String = "",
    val baseUnitName: String = "Satuan",
    val costPrice: Long = 0L,
    val initialCostPrice: Long = 0L,
    val costAdjustment: Long = 0L,
    val stockBase: Long = 0L,
    val minStockAlert: Long = 5L,
    val defaultPriceUnitIndex: Long = 0L,
    val defaultPriceTierType: String = PriceTierType.DEFAULT.name,
    val priceUnits: List<PriceUnit> = listOf(PriceUnit()),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun defaultUnit(): PriceUnit {
        val idx = defaultPriceUnitIndex.toInt().coerceIn(0, (priceUnits.size - 1).coerceAtLeast(0))
        return priceUnits.getOrElse(idx) { PriceUnit() }
    }

    fun defaultTier(): PriceTierType = PriceTierType.fromString(defaultPriceTierType)

    fun displayDefaultPrice(): Long = defaultUnit().priceForTier(defaultTier())

    fun stockSummaryLabel(): String {
        val largestUnit = priceUnits.filter { it.qtyContent > 1L }.maxByOrNull { it.qtyContent }
        return if (largestUnit != null && stockBase >= largestUnit.qtyContent) {
            val wholePacks = stockBase / largestUnit.qtyContent
            val rem = stockBase % largestUnit.qtyContent
            if (rem == 0L) "$wholePacks ${largestUnit.unitName} ($stockBase $baseUnitName)"
            else "$wholePacks ${largestUnit.unitName} + $rem $baseUnitName"
        } else {
            "$stockBase $baseUnitName"
        }
    }

    fun lowStockStatus(): Boolean = stockBase <= minStockAlert
}

@Keep
@IgnoreExtraProperties
data class StockCard(
    @DocumentId val id: String = "",
    val userId: String = "",
    val productId: String = "",
    val productName: String = "",
    val sku: String = "",
    val movementType: String = StockMovementType.MASUK.name,
    val qtyChange: Long = 0L,
    val stockBefore: Long = 0L,
    val stockAfter: Long = 0L,
    val unitSummary: String = "",
    val note: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun movement(): StockMovementType = StockMovementType.fromString(movementType)
}

@Keep
@IgnoreExtraProperties
data class SaleItem(
    val productId: String = "",
    val productName: String = "",
    val unitName: String = "Satuan",
    val qty: Long = 1L,
    val qtyContent: Long = 1L,
    val unitPrice: Long = 0L,
    val costPricePerBase: Long = 0L,
    val subtotal: Long = 0L
) {
    fun toMap(): Map<String, Any> = mapOf(
        "productId" to productId,
        "productName" to productName,
        "unitName" to unitName,
        "qty" to qty,
        "qtyContent" to qtyContent,
        "unitPrice" to unitPrice,
        "costPricePerBase" to costPricePerBase,
        "subtotal" to subtotal
    )
}

@Keep
@IgnoreExtraProperties
data class SaleTransaction(
    @DocumentId val id: String = "",
    val userId: String = "",
    val receiptNumber: String = "",
    val customerTier: String = PriceTierType.DEFAULT.name,
    val customerName: String = "Pelanggan Umum",
    val paymentMethod: String = PaymentMethod.TUNAI.name,
    val totalAmount: Long = 0L,
    val totalCost: Long = 0L,
    val totalProfit: Long = 0L,
    val cashPaid: Long = 0L,
    val changeAmount: Long = 0L,
    val itemCount: Long = 0L,
    val items: List<SaleItem> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun tier(): PriceTierType = PriceTierType.fromString(customerTier)
    fun payment(): PaymentMethod = PaymentMethod.fromString(paymentMethod)
}

data class CartLine(
    val product: Product,
    val unitIndex: Int,
    val qty: Long
) {
    val selectedUnit: PriceUnit
        get() = product.priceUnits.getOrElse(unitIndex) { product.priceUnits.firstOrNull() ?: PriceUnit() }

    val baseUnitsUsed: Long
        get() = qty * selectedUnit.qtyContent

    fun unitPriceFor(tier: PriceTierType): Long = selectedUnit.priceForTier(tier)
    fun subtotalFor(tier: PriceTierType): Long = unitPriceFor(tier) * qty
    fun costTotal(): Long = product.costPrice * baseUnitsUsed
}
