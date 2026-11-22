package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.CartLine
import com.example.data.model.PaymentMethod
import com.example.data.model.PriceTierType
import com.example.data.model.PriceUnit
import com.example.data.model.Product
import com.example.data.model.ReferenceType
import com.example.data.model.SaleItem
import com.example.data.model.SaleTransaction
import com.example.data.model.StockCard
import com.example.data.model.StockMovementType
import com.example.data.model.StoreReference
import com.example.data.model.StoreSettings
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Pengguna harus masuk dengan Google terlebih dahulu.")
    }

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    fun observeSettings(userId: String): Flow<StoreSettings> = flow {
        val path = "users/$userId/store_settings/default"
        emitAll(
            userDoc(userId).collection("store_settings").document("default")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        StoreSettings::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: StoreSettings(id = "default", userId = userId)
                }
                .catch { error ->
                    logStreamError(error, OperationType.GET, path, userId)
                    throw error
                }
        )
    }

    fun observeProducts(userId: String): Flow<List<Product>> = flow {
        val path = "users/$userId/products"
        emitAll(
            userDoc(userId).collection("products")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Product::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.name.lowercase(Locale.getDefault()) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observeReferences(userId: String): Flow<List<StoreReference>> = flow {
        val path = "users/$userId/references"
        emitAll(
            userDoc(userId).collection("references")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        StoreReference::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.name.lowercase(Locale.getDefault()) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observeStockCards(userId: String): Flow<List<StockCard>> = flow {
        val path = "users/$userId/stock_cards"
        emitAll(
            userDoc(userId).collection("stock_cards")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        StockCard::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observeTransactions(userId: String): Flow<List<SaleTransaction>> = flow {
        val path = "users/$userId/transactions"
        emitAll(
            userDoc(userId).collection("transactions")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SaleTransaction::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    private fun logStreamError(
        error: Throwable,
        operationType: OperationType,
        path: String,
        expectedUid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }

    suspend fun saveSettings(
        storeName: String,
        storeAddress: String,
        storePhone: String,
        receiptFooter: String,
        taxPercent: Double,
        isExisting: Boolean
    ): Result<Unit> {
        var docPath = "users/unknown/store_settings/default"
        return try {
            val uid = requireUserId()
            val cleanName = storeName.trim().take(80)
            require(cleanName.isNotEmpty()) { "Nama toko tidak boleh kosong." }
            val cleanAddress = storeAddress.trim().take(200)
            val cleanPhone = storePhone.trim().take(30)
            val cleanFooter = receiptFooter.trim().take(200)
            val safeTax = taxPercent.coerceIn(0.0, 100.0)

            val docRef = userDoc(uid).collection("store_settings").document("default")
            docPath = docRef.path

            val existingSnap = docRef.get().await()
            if (existingSnap.exists() || isExisting) {
                if (existingSnap.exists()) {
                    docRef.update(
                        mapOf(
                            "storeName" to cleanName,
                            "storeAddress" to cleanAddress,
                            "storePhone" to cleanPhone,
                            "receiptFooter" to cleanFooter,
                            "taxPercent" to safeTax,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    docRef.set(
                        mapOf(
                            "userId" to uid,
                            "storeName" to cleanName,
                            "storeAddress" to cleanAddress,
                            "storePhone" to cleanPhone,
                            "receiptFooter" to cleanFooter,
                            "taxPercent" to safeTax,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "storeName" to cleanName,
                        "storeAddress" to cleanAddress,
                        "storePhone" to cleanPhone,
                        "receiptFooter" to cleanFooter,
                        "taxPercent" to safeTax,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addReference(
        type: ReferenceType,
        name: String,
        defaultQtyPerUnit: Long = 1L
    ): Result<Unit> {
        var docPath = "users/unknown/references"
        return try {
            val uid = requireUserId()
            val cleanName = name.trim().take(60)
            require(cleanName.isNotEmpty()) { "Nama referensi tidak boleh kosong." }
            val safeQty = defaultQtyPerUnit.coerceIn(1L, 100000L)

            val docRef = userDoc(uid).collection("references").document()
            docPath = docRef.path
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "type" to type.name,
                    "name" to cleanName,
                    "defaultQtyPerUnit" to safeQty,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteReference(refId: String): Result<Unit> {
        var docPath = "users/unknown/references/$refId"
        return try {
            val uid = requireUserId()
            require(refId.isNotBlank()) { "ID referensi tidak valid." }
            val docRef = userDoc(uid).collection("references").document(refId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveProduct(
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
        priceUnits: List<PriceUnit>
    ): Result<Unit> {
        var docPath = "users/unknown/products"
        return try {
            val uid = requireUserId()
            val cleanSku = sku.trim().take(50)
            val cleanName = name.trim().take(120)
            require(cleanSku.isNotEmpty()) { "SKU / Barcode tidak boleh kosong." }
            require(cleanName.isNotEmpty()) { "Nama barang tidak boleh kosong." }
            require(priceUnits.isNotEmpty() && priceUnits.size <= 10) {
                "Minimal 1 dan maksimal 10 satuan harga jual."
            }

            val cleanBaseUnit = baseUnitName.trim().ifEmpty { "Satuan" }.take(30)
            val safeStock = stockBase.coerceAtLeast(0L)
            val safeMinStock = minStockAlert.coerceAtLeast(0L)
            val safeCost = costPrice.coerceAtLeast(0L)
            val safeInitialCost = initialCostPrice.coerceAtLeast(0L)
            val safeDefaultIdx = defaultPriceUnitIndex.coerceIn(0L, (priceUnits.size - 1).toLong())
            val unitMaps = priceUnits.map { it.toMap() }

            val batch = db.batch()
            if (existingProduct == null || existingProduct.id.isBlank()) {
                val prodRef = userDoc(uid).collection("products").document()
                docPath = prodRef.path
                batch.set(
                    prodRef,
                    mapOf(
                        "userId" to uid,
                        "sku" to cleanSku,
                        "name" to cleanName,
                        "categoryName" to categoryName.trim().take(60),
                        "brandName" to brandName.trim().take(60),
                        "baseUnitName" to cleanBaseUnit,
                        "costPrice" to safeCost,
                        "initialCostPrice" to safeInitialCost,
                        "costAdjustment" to costAdjustment,
                        "stockBase" to safeStock,
                        "minStockAlert" to safeMinStock,
                        "defaultPriceUnitIndex" to safeDefaultIdx,
                        "defaultPriceTierType" to defaultPriceTierType.name,
                        "priceUnits" to unitMaps,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                if (safeStock > 0L) {
                    val cardRef = userDoc(uid).collection("stock_cards").document()
                    batch.set(
                        cardRef,
                        mapOf(
                            "userId" to uid,
                            "productId" to prodRef.id,
                            "productName" to cleanName,
                            "sku" to cleanSku,
                            "movementType" to StockMovementType.MASUK.name,
                            "qtyChange" to safeStock,
                            "stockBefore" to 0L,
                            "stockAfter" to safeStock,
                            "unitSummary" to "$safeStock $cleanBaseUnit",
                            "note" to "Stok awal barang baru",
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            } else {
                val prodRef = userDoc(uid).collection("products").document(existingProduct.id)
                docPath = prodRef.path
                batch.update(
                    prodRef,
                    mapOf(
                        "sku" to cleanSku,
                        "name" to cleanName,
                        "categoryName" to categoryName.trim().take(60),
                        "brandName" to brandName.trim().take(60),
                        "baseUnitName" to cleanBaseUnit,
                        "costPrice" to safeCost,
                        "initialCostPrice" to safeInitialCost,
                        "costAdjustment" to costAdjustment,
                        "stockBase" to safeStock,
                        "minStockAlert" to safeMinStock,
                        "defaultPriceUnitIndex" to safeDefaultIdx,
                        "defaultPriceTierType" to defaultPriceTierType.name,
                        "priceUnits" to unitMaps,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                val delta = safeStock - existingProduct.stockBase
                if (delta != 0L) {
                    val cardRef = userDoc(uid).collection("stock_cards").document()
                    batch.set(
                        cardRef,
                        mapOf(
                            "userId" to uid,
                            "productId" to existingProduct.id,
                            "productName" to cleanName,
                            "sku" to cleanSku,
                            "movementType" to StockMovementType.PENYESUAIAN.name,
                            "qtyChange" to delta,
                            "stockBefore" to existingProduct.stockBase,
                            "stockAfter" to safeStock,
                            "unitSummary" to "${if (delta > 0) "+$delta" else "$delta"} $cleanBaseUnit",
                            "note" to "Penyesuaian dari edit barang",
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteProduct(productId: String): Result<Unit> {
        var docPath = "users/unknown/products/$productId"
        return try {
            val uid = requireUserId()
            require(productId.isNotBlank()) { "ID barang tidak valid." }
            val docRef = userDoc(uid).collection("products").document(productId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun adjustStock(
        product: Product,
        movementType: StockMovementType,
        qtyInUnit: Long,
        unitName: String,
        qtyPerUnit: Long,
        note: String
    ): Result<Unit> {
        var docPath = "users/unknown/products/${product.id}"
        return try {
            val uid = requireUserId()
            require(product.id.isNotBlank()) { "ID barang tidak valid." }
            require(qtyInUnit > 0L) { "Jumlah harus lebih dari 0." }

            val baseDelta = qtyInUnit * qtyPerUnit.coerceAtLeast(1L)
            val signedDelta: Long
            val newStock: Long
            when (movementType) {
                StockMovementType.MASUK -> {
                    signedDelta = baseDelta
                    newStock = product.stockBase + baseDelta
                }
                StockMovementType.KELUAR -> {
                    require(product.stockBase >= baseDelta) {
                        "Stok tidak mencukupi (tersedia ${product.stockBase} ${product.baseUnitName})."
                    }
                    signedDelta = -baseDelta
                    newStock = product.stockBase - baseDelta
                }
                StockMovementType.PENYESUAIAN -> {
                    // For PENYESUAIAN, qtyInUnit * qtyPerUnit represents the exact physical count
                    newStock = baseDelta
                    signedDelta = newStock - product.stockBase
                }
            }

            val batch = db.batch()
            val prodRef = userDoc(uid).collection("products").document(product.id)
            docPath = prodRef.path
            batch.update(
                prodRef,
                mapOf(
                    "stockBase" to newStock,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val cardRef = userDoc(uid).collection("stock_cards").document()
            val summaryText = if (movementType == StockMovementType.PENYESUAIAN) {
                "Opname -> $newStock ${product.baseUnitName}"
            } else {
                "$qtyInUnit $unitName ($baseDelta ${product.baseUnitName})"
            }
            batch.set(
                cardRef,
                mapOf(
                    "userId" to uid,
                    "productId" to product.id,
                    "productName" to product.name,
                    "sku" to product.sku,
                    "movementType" to movementType.name,
                    "qtyChange" to signedDelta,
                    "stockBefore" to product.stockBase,
                    "stockAfter" to newStock,
                    "unitSummary" to summaryText.take(80),
                    "note" to note.trim().ifEmpty { movementType.label }.take(200),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun completeSale(
        cartLines: List<CartLine>,
        tier: PriceTierType,
        customerName: String,
        paymentMethod: PaymentMethod,
        cashPaid: Long
    ): Result<SaleTransaction> {
        var docPath = "users/unknown/transactions"
        return try {
            val uid = requireUserId()
            require(cartLines.isNotEmpty() && cartLines.size <= 100) {
                "Keranjang belanja kosong atau melebihi batas."
            }

            // Aggregate base units per product ID in case the same product appears in two different units
            val requiredByProduct = cartLines.groupBy { it.product.id }
                .mapValues { (_, lines) -> lines.sumOf { it.baseUnitsUsed } }

            for ((_, lines) in cartLines.groupBy { it.product.id }) {
                val product = lines.first().product
                val totalNeeded = requiredByProduct[product.id] ?: 0L
                require(product.stockBase >= totalNeeded) {
                    "Stok ${product.name} tidak cukup (sisa ${product.stockBase} ${product.baseUnitName}, diminta $totalNeeded)."
                }
            }

            val saleItems = cartLines.map { line ->
                val price = line.unitPriceFor(tier)
                SaleItem(
                    productId = line.product.id,
                    productName = line.product.name,
                    unitName = line.selectedUnit.unitName,
                    qty = line.qty,
                    qtyContent = line.selectedUnit.qtyContent,
                    unitPrice = price,
                    costPricePerBase = line.product.costPrice,
                    subtotal = price * line.qty
                )
            }

            val totalAmount = saleItems.sumOf { it.subtotal }
            val totalCost = cartLines.sumOf { it.costTotal() }
            val totalProfit = totalAmount - totalCost
            val effectiveCashPaid = if (paymentMethod == PaymentMethod.TUNAI) {
                require(cashPaid >= totalAmount) { "Nominal bayar tunai kurang dari total belanja." }
                cashPaid
            } else {
                totalAmount
            }
            val changeAmount = (effectiveCashPaid - totalAmount).coerceAtLeast(0L)
            val receiptNumber = "TRX-${SimpleDateFormat("yyMMdd-HHmmss", Locale.US).format(Date())}"
            val cleanCustomerName = customerName.trim().ifEmpty {
                when (tier) {
                    PriceTierType.DEFAULT -> "Pelanggan Umum"
                    PriceTierType.GROSIR -> "Pelanggan Grosir"
                    PriceTierType.MEMBER_VIP -> "Member VIP"
                }
            }.take(80)

            val batch = db.batch()
            val trxRef = userDoc(uid).collection("transactions").document()
            docPath = trxRef.path

            batch.set(
                trxRef,
                mapOf(
                    "userId" to uid,
                    "receiptNumber" to receiptNumber,
                    "customerTier" to tier.name,
                    "customerName" to cleanCustomerName,
                    "paymentMethod" to paymentMethod.name,
                    "totalAmount" to totalAmount,
                    "totalCost" to totalCost,
                    "totalProfit" to totalProfit,
                    "cashPaid" to effectiveCashPaid,
                    "changeAmount" to changeAmount,
                    "itemCount" to saleItems.size.toLong(),
                    "items" to saleItems.map { it.toMap() },
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )

            for ((productId, lines) in cartLines.groupBy { it.product.id }) {
                val product = lines.first().product
                val usedBase = lines.sumOf { it.baseUnitsUsed }
                val newStock = (product.stockBase - usedBase).coerceAtLeast(0L)
                val prodRef = userDoc(uid).collection("products").document(productId)
                batch.update(
                    prodRef,
                    mapOf(
                        "stockBase" to newStock,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                val unitSummary = lines.joinToString(" + ") { "${it.qty} ${it.selectedUnit.unitName}" }
                val cardRef = userDoc(uid).collection("stock_cards").document()
                batch.set(
                    cardRef,
                    mapOf(
                        "userId" to uid,
                        "productId" to productId,
                        "productName" to product.name,
                        "sku" to product.sku,
                        "movementType" to StockMovementType.KELUAR.name,
                        "qtyChange" to -usedBase,
                        "stockBefore" to product.stockBase,
                        "stockAfter" to newStock,
                        "unitSummary" to unitSummary.take(80),
                        "note" to "Penjualan $receiptNumber (${tier.badgeText})".take(200),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.commit().await()

            Result.success(
                SaleTransaction(
                    id = trxRef.id,
                    userId = uid,
                    receiptNumber = receiptNumber,
                    customerTier = tier.name,
                    customerName = cleanCustomerName,
                    paymentMethod = paymentMethod.name,
                    totalAmount = totalAmount,
                    totalCost = totalCost,
                    totalProfit = totalProfit,
                    cashPaid = effectiveCashPaid,
                    changeAmount = changeAmount,
                    itemCount = saleItems.size.toLong(),
                    items = saleItems,
                    createdAt = Timestamp.now()
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun seedStarterStore(): Result<Unit> {
        var docPath = "users/unknown/products"
        return try {
            val uid = requireUserId()
            val batch = db.batch()

            // Default store settings if missing
            val settingsRef = userDoc(uid).collection("store_settings").document("default")
            docPath = settingsRef.path
            batch.set(
                settingsRef,
                mapOf(
                    "userId" to uid,
                    "storeName" to "Toko Berkah Mandiri",
                    "storeAddress" to "Jl. Pasar Baru No. 45, Jakarta",
                    "storePhone" to "0812-8899-7766",
                    "receiptFooter" to "Barang yang sudah dibeli tidak dapat ditukar. Terima kasih!",
                    "taxPercent" to 0.0,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            // Default References
            val refs = listOf(
                Triple(ReferenceType.CATEGORY, "Sembako", 1L),
                Triple(ReferenceType.CATEGORY, "Minuman", 1L),
                Triple(ReferenceType.CATEGORY, "Makanan Ringan", 1L),
                Triple(ReferenceType.BRAND, "Indofood", 1L),
                Triple(ReferenceType.BRAND, "Bimoli", 1L),
                Triple(ReferenceType.BRAND, "Aqua", 1L),
                Triple(ReferenceType.UNIT, "Satuan", 1L),
                Triple(ReferenceType.UNIT, "Pak", 6L),
                Triple(ReferenceType.UNIT, "Lusin", 12L),
                Triple(ReferenceType.UNIT, "Kardus", 24L)
            )
            refs.forEach { (type, name, qty) ->
                val refDoc = userDoc(uid).collection("references").document()
                batch.set(
                    refDoc,
                    mapOf(
                        "userId" to uid,
                        "type" to type.name,
                        "name" to name,
                        "defaultQtyPerUnit" to qty,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            val starterProducts = listOf(
                mapOf(
                    "sku" to "8991002101122",
                    "name" to "Minyak Goreng Pouch 1L",
                    "categoryName" to "Sembako",
                    "brandName" to "Bimoli",
                    "baseUnitName" to "Satuan",
                    "costPrice" to 11500L,
                    "initialCostPrice" to 11500L,
                    "costAdjustment" to 0L,
                    "stockBase" to 96L,
                    "minStockAlert" to 12L,
                    "defaultPriceUnitIndex" to 0L,
                    "defaultPriceTierType" to PriceTierType.DEFAULT.name,
                    "priceUnits" to listOf(
                        PriceUnit("Satuan", 1L, 1L, 12600L, 12000L, 12000L).toMap(),
                        PriceUnit("Kardus", 24L, 1L, 550000L, 520000L, 510000L).toMap()
                    )
                ),
                mapOf(
                    "sku" to "8998866200311",
                    "name" to "Mie Instan Goreng Original",
                    "categoryName" to "Sembako",
                    "brandName" to "Indofood",
                    "baseUnitName" to "Satuan",
                    "costPrice" to 2650L,
                    "initialCostPrice" to 2600L,
                    "costAdjustment" to 50L,
                    "stockBase" to 200L,
                    "minStockAlert" to 40L,
                    "defaultPriceUnitIndex" to 0L,
                    "defaultPriceTierType" to PriceTierType.DEFAULT.name,
                    "priceUnits" to listOf(
                        PriceUnit("Satuan", 1L, 1L, 3500L, 3200L, 3100L).toMap(),
                        PriceUnit("Kardus", 40L, 1L, 122000L, 118000L, 115000L).toMap()
                    )
                ),
                mapOf(
                    "sku" to "8996001301014",
                    "name" to "Air Mineral Botol 600ml",
                    "categoryName" to "Minuman",
                    "brandName" to "Aqua",
                    "baseUnitName" to "Satuan",
                    "costPrice" to 2100L,
                    "initialCostPrice" to 2100L,
                    "costAdjustment" to 0L,
                    "stockBase" to 72L,
                    "minStockAlert" to 24L,
                    "defaultPriceUnitIndex" to 0L,
                    "defaultPriceTierType" to PriceTierType.DEFAULT.name,
                    "priceUnits" to listOf(
                        PriceUnit("Satuan", 1L, 1L, 3500L, 3000L, 2800L).toMap(),
                        PriceUnit("Kardus", 24L, 1L, 62000L, 58000L, 56000L).toMap()
                    )
                ),
                mapOf(
                    "sku" to "8992761135027",
                    "name" to "Gula Pasir Kristal Putih 1kg",
                    "categoryName" to "Sembako",
                    "brandName" to "Gulaku",
                    "baseUnitName" to "Satuan",
                    "costPrice" to 14500L,
                    "initialCostPrice" to 14500L,
                    "costAdjustment" to 0L,
                    "stockBase" to 8L,
                    "minStockAlert" to 10L,
                    "defaultPriceUnitIndex" to 0L,
                    "defaultPriceTierType" to PriceTierType.DEFAULT.name,
                    "priceUnits" to listOf(
                        PriceUnit("Satuan", 1L, 1L, 17500L, 16800L, 16500L).toMap(),
                        PriceUnit("Pak", 6L, 1L, 102000L, 98000L, 96000L).toMap()
                    )
                )
            )

            starterProducts.forEach { prodMap ->
                val prodRef = userDoc(uid).collection("products").document()
                val fullMap = prodMap.toMutableMap()
                fullMap["userId"] = uid
                fullMap["createdAt"] = FieldValue.serverTimestamp()
                fullMap["updatedAt"] = FieldValue.serverTimestamp()
                batch.set(prodRef, fullMap)

                val stockVal = prodMap["stockBase"] as Long
                val cardRef = userDoc(uid).collection("stock_cards").document()
                batch.set(
                    cardRef,
                    mapOf(
                        "userId" to uid,
                        "productId" to prodRef.id,
                        "productName" to (prodMap["name"] as String),
                        "sku" to (prodMap["sku"] as String),
                        "movementType" to StockMovementType.MASUK.name,
                        "qtyChange" to stockVal,
                        "stockBefore" to 0L,
                        "stockAfter" to stockVal,
                        "unitSummary" to "$stockVal Satuan",
                        "note" to "Stok awal paket toko",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }
}
