package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CartLine
import com.example.data.model.PaymentMethod
import com.example.data.model.PriceTierType
import com.example.data.model.PriceUnit
import com.example.data.model.Product
import com.example.data.model.ReferenceType
import com.example.data.model.SaleTransaction
import com.example.data.model.StockCard
import com.example.data.model.StockMovementType
import com.example.data.model.StoreReference
import com.example.data.model.StoreSettings
import com.example.data.repository.StoreRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class StoreViewModel(
    private val repository: StoreRepository,
    private val currentUserId: String
) : ViewModel() {

    private fun <T> Flow<T>.toUiStateFlow(
        initial: UiState<T> = UiState.Loading,
        errorLabel: String
    ): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "$errorLabel: ${rootError.message}", rootError)
                emit(UiState.Error(rootError.message ?: errorLabel))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = initial
            )

    val settingsState: StateFlow<UiState<StoreSettings>> =
        repository.observeSettings(currentUserId)
            .toUiStateFlow(errorLabel = "Gagal memuat pengaturan toko")

    val productsState: StateFlow<UiState<List<Product>>> =
        repository.observeProducts(currentUserId)
            .toUiStateFlow(errorLabel = "Gagal memuat daftar barang")

    val referencesState: StateFlow<UiState<List<StoreReference>>> =
        repository.observeReferences(currentUserId)
            .toUiStateFlow(errorLabel = "Gagal memuat referensi toko")

    val stockCardsState: StateFlow<UiState<List<StockCard>>> =
        repository.observeStockCards(currentUserId)
            .toUiStateFlow(errorLabel = "Gagal memuat kartu stok")

    val transactionsState: StateFlow<UiState<List<SaleTransaction>>> =
        repository.observeTransactions(currentUserId)
            .toUiStateFlow(errorLabel = "Gagal memuat riwayat transaksi")

    private val _cartLines = MutableStateFlow<List<CartLine>>(emptyList())
    val cartLines: StateFlow<List<CartLine>> = _cartLines.asStateFlow()

    private val _activeTier = MutableStateFlow(PriceTierType.DEFAULT)
    val activeTier: StateFlow<PriceTierType> = _activeTier.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    private val _lastCompletedReceipt = MutableStateFlow<SaleTransaction?>(null)
    val lastCompletedReceipt: StateFlow<SaleTransaction?> = _lastCompletedReceipt.asStateFlow()

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    fun dismissCompletedReceipt() {
        _lastCompletedReceipt.value = null
    }

    fun setActiveTier(tier: PriceTierType) {
        _activeTier.value = tier
    }

    fun addToCart(product: Product) {
        if (product.stockBase <= 0L) {
            _feedbackMessage.value = "Stok ${product.name} sedang habis."
            return
        }
        val defaultIdx = product.defaultPriceUnitIndex.toInt()
            .coerceIn(0, (product.priceUnits.size - 1).coerceAtLeast(0))
        val unit = product.priceUnits.getOrElse(defaultIdx) { PriceUnit() }
        val minStep = unit.minBuy.coerceAtLeast(1L)

        _cartLines.update { current ->
            val existingIdx = current.indexOfFirst {
                it.product.id == product.id && it.unitIndex == defaultIdx
            }
            val currentUsedForProduct = current.filter { it.product.id == product.id }
                .sumOf { it.baseUnitsUsed }

            if (existingIdx >= 0) {
                val existing = current[existingIdx]
                val additionalBase = unit.qtyContent
                if (currentUsedForProduct + additionalBase > product.stockBase) {
                    _feedbackMessage.value = "Stok ${product.name} tidak mencukupi (tersisa ${product.stockBase} ${product.baseUnitName})."
                    return@update current
                }
                current.toMutableList().apply {
                    this[existingIdx] = existing.copy(qty = existing.qty + 1L)
                }
            } else {
                val initialQty = if (minStep * unit.qtyContent <= product.stockBase) minStep else 1L
                val neededBase = initialQty * unit.qtyContent
                if (currentUsedForProduct + neededBase > product.stockBase) {
                    // Fallback to base unit index 0 if larger default unit exceeds remaining stock
                    val fallbackUnit = product.priceUnits.firstOrNull() ?: unit
                    if (currentUsedForProduct + fallbackUnit.qtyContent > product.stockBase) {
                        _feedbackMessage.value = "Stok ${product.name} tidak mencukupi."
                        return@update current
                    }
                    val fallbackLineIdx = current.indexOfFirst { it.product.id == product.id && it.unitIndex == 0 }
                    if (fallbackLineIdx >= 0) {
                        return@update current.toMutableList().apply {
                            val ex = this[fallbackLineIdx]
                            this[fallbackLineIdx] = ex.copy(qty = ex.qty + 1L)
                        }
                    }
                    return@update current + CartLine(product = product, unitIndex = 0, qty = 1L)
                }
                current + CartLine(product = product, unitIndex = defaultIdx, qty = initialQty)
            }
        }
    }

    fun updateCartQty(productId: String, unitIndex: Int, delta: Long) {
        _cartLines.update { current ->
            val idx = current.indexOfFirst { it.product.id == productId && it.unitIndex == unitIndex }
            if (idx < 0) return@update current
            val line = current[idx]
            val nextQty = line.qty + delta
            if (nextQty <= 0L) {
                return@update current.filterIndexed { i, _ -> i != idx }
            }
            val otherBase = current.filterIndexed { i, item -> i != idx && item.product.id == productId }
                .sumOf { it.baseUnitsUsed }
            val nextBase = nextQty * line.selectedUnit.qtyContent
            if (otherBase + nextBase > line.product.stockBase) {
                _feedbackMessage.value = "Maksimal stok ${line.product.name} tercapai (${line.product.stockBase} ${line.product.baseUnitName})."
                return@update current
            }
            current.toMutableList().apply {
                this[idx] = line.copy(qty = nextQty)
            }
        }
    }

    fun changeCartUnit(productId: String, oldUnitIndex: Int, newUnitIndex: Int) {
        if (oldUnitIndex == newUnitIndex) return
        _cartLines.update { current ->
            val idx = current.indexOfFirst { it.product.id == productId && it.unitIndex == oldUnitIndex }
            if (idx < 0) return@update current
            val line = current[idx]
            val targetUnit = line.product.priceUnits.getOrElse(newUnitIndex) { return@update current }
            val targetQty = targetUnit.minBuy.coerceAtLeast(1L)
            val otherBase = current.filterIndexed { i, item -> i != idx && item.product.id == productId }
                .sumOf { it.baseUnitsUsed }
            if (otherBase + targetQty * targetUnit.qtyContent > line.product.stockBase) {
                _feedbackMessage.value = "Stok tidak cukup untuk satuan ${targetUnit.unitName}."
                return@update current
            }
            val mergedIdx = current.indexOfFirst { it.product.id == productId && it.unitIndex == newUnitIndex }
            if (mergedIdx >= 0) {
                val existingTarget = current[mergedIdx]
                current.toMutableList().apply {
                    this[mergedIdx] = existingTarget.copy(qty = existingTarget.qty + targetQty)
                    removeAt(idx)
                }
            } else {
                current.toMutableList().apply {
                    this[idx] = line.copy(unitIndex = newUnitIndex, qty = targetQty)
                }
            }
        }
    }

    fun clearCart() {
        _cartLines.value = emptyList()
    }

    fun checkout(
        customerName: String,
        paymentMethod: PaymentMethod,
        cashPaid: Long
    ) {
        val lines = _cartLines.value
        if (lines.isEmpty()) {
            _feedbackMessage.value = "Keranjang masih kosong."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.completeSale(
                cartLines = lines,
                tier = _activeTier.value,
                customerName = customerName,
                paymentMethod = paymentMethod,
                cashPaid = cashPaid
            )
            _isBusy.value = false
            res.onSuccess { trx ->
                _cartLines.value = emptyList()
                _lastCompletedReceipt.value = trx
                _feedbackMessage.value = "Transaksi ${trx.receiptNumber} berhasil disimpan."
            }.onFailure { err ->
                _feedbackMessage.value = err.message ?: "Gagal memproses pembayaran."
            }
        }
    }

    fun saveProduct(
        existingProduct: Product?,
        sku: String,
        name: String,
        categoryName: String,
        brandName: String,
        baseUnitName: String,
        costPrice: Long,
        initialCostPrice: Long,
        costAdjustment: Long,
        stockBase: Long,
        minStockAlert: Long,
        defaultPriceUnitIndex: Long,
        defaultPriceTierType: PriceTierType,
        priceUnits: List<PriceUnit>,
        onSaved: () -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.saveProduct(
                existingProduct = existingProduct,
                sku = sku,
                name = name,
                categoryName = categoryName,
                brandName = brandName,
                baseUnitName = baseUnitName,
                costPrice = costPrice,
                initialCostPrice = initialCostPrice,
                costAdjustment = costAdjustment,
                stockBase = stockBase,
                minStockAlert = minStockAlert,
                defaultPriceUnitIndex = defaultPriceUnitIndex,
                defaultPriceTierType = defaultPriceTierType,
                priceUnits = priceUnits
            )
            _isBusy.value = false
            res.onSuccess {
                _feedbackMessage.value = if (existingProduct == null) {
                    "Barang \"$name\" berhasil ditambahkan."
                } else {
                    "Perubahan barang \"$name\" disimpan."
                }
                onSaved()
            }.onFailure { err ->
                _feedbackMessage.value = err.message ?: "Gagal menyimpan barang."
            }
        }
    }

    fun deleteProduct(product: Product) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.deleteProduct(product.id)
            _isBusy.value = false
            res.onSuccess {
                _cartLines.update { list -> list.filterNot { it.product.id == product.id } }
                _feedbackMessage.value = "Barang \"${product.name}\" dihapus."
            }.onFailure { err ->
                _feedbackMessage.value = err.message ?: "Gagal menghapus barang."
            }
        }
    }

    fun adjustStock(
        product: Product,
        movementType: StockMovementType,
        qtyInUnit: Long,
        unitName: String,
        qtyPerUnit: Long,
        note: String,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.adjustStock(
                product = product,
                movementType = movementType,
                qtyInUnit = qtyInUnit,
                unitName = unitName,
                qtyPerUnit = qtyPerUnit,
                note = note
            )
            _isBusy.value = false
            res.onSuccess {
                _feedbackMessage.value = "Kartu stok ${product.name} diperbarui."
                onDone()
            }.onFailure { err ->
                _feedbackMessage.value = err.message ?: "Gagal memperbarui stok."
            }
        }
    }

    fun addReference(type: ReferenceType, name: String, defaultQty: Long = 1L) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.addReference(type, name, defaultQty)
            _isBusy.value = false
            res.onSuccess {
                _feedbackMessage.value = "${type.label} \"$name\" ditambahkan."
            }.onFailure { err ->
                _feedbackMessage.value = err.message ?: "Gagal menambahkan referensi."
            }
        }
    }

    fun deleteReference(ref: StoreReference) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.deleteReference(ref.id)
            _isBusy.value = false
            res.onSuccess {
                _feedbackMessage.value = "\"${ref.name}\" dihapus."
            }.onFailure { err ->
                _feedbackMessage.value = err.message ?: "Gagal menghapus referensi."
            }
        }
    }

    fun saveSettings(
        storeName: String,
        storeAddress: String,
        storePhone: String,
        receiptFooter: String,
        taxPercent: Double,
        isExisting: Boolean
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.saveSettings(
                storeName = storeName,
                storeAddress = storeAddress,
                storePhone = storePhone,
                receiptFooter = receiptFooter,
                taxPercent = taxPercent,
                isExisting = isExisting
            )
            _isBusy.value = false
            res.onSuccess {
                _feedbackMessage.value = "Pengaturan toko disimpan."
            }.onFailure { err ->
                _feedbackMessage.value = err.message ?: "Gagal menyimpan pengaturan."
            }
        }
    }

    fun seedStarterStore() {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.seedStarterStore()
            _isBusy.value = false
            res.onSuccess {
                _feedbackMessage.value = "Data contoh toko berhasil dimuat."
            }.onFailure { err ->
                _feedbackMessage.value = err.message ?: "Gagal memuat data contoh."
            }
        }
    }

    private companion object {
        const val TAG = "StoreVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
