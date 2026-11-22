package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.model.Product
import com.example.data.model.StockMovementType
import com.example.ui.components.BarcodePreviewDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorBannerCard
import com.example.ui.components.LoadingCenter
import com.example.ui.components.StockStatusPill
import com.example.ui.components.formatRupiah
import com.example.ui.theme.AmberGrosir
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.VioletVip
import com.example.ui.viewmodel.UiState

@Composable
fun InventoryScreen(
    productsState: UiState<List<Product>>,
    isBusy: Boolean,
    onAddProduct: () -> Unit,
    onEditProduct: (Product) -> Unit,
    onDeleteProduct: (Product) -> Unit,
    onAdjustStock: (Product, StockMovementType, Long, String, Long, String) -> Unit,
    onOpenStockCards: () -> Unit,
    onOpenReferences: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf("Semua") }
    var barcodeProduct by remember { mutableStateOf<Product?>(null) }
    var stockDialogProduct by remember { mutableStateOf<Product?>(null) }
    var deleteCandidate by remember { mutableStateOf<Product?>(null) }

    val products = (productsState as? UiState.Success)?.data.orEmpty()
    val filters = remember(products) {
        listOf("Semua", "Stok Menipis") +
            products.mapNotNull { it.categoryName.takeIf(String::isNotBlank) }.distinct().sorted()
    }

    val filtered = remember(products, searchQuery, filterMode) {
        products.filter { p ->
            val matchesFilter = when (filterMode) {
                "Semua" -> true
                "Stok Menipis" -> p.lowStockStatus()
                else -> p.categoryName == filterMode
            }
            val q = searchQuery.trim().lowercase()
            val matchesQ = q.isEmpty() ||
                p.name.lowercase().contains(q) ||
                p.sku.lowercase().contains(q) ||
                p.brandName.lowercase().contains(q)
            matchesFilter && matchesQ
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddProduct,
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("inventory_add_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Tambah Barang")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Barang & Gudang",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = onOpenStockCards,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("inventory_stock_cards_button")
                            ) {
                                Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Kartu Stok", style = MaterialTheme.typography.labelMedium)
                            }
                            OutlinedButton(
                                onClick = onOpenReferences,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("inventory_references_button")
                            ) {
                                Icon(Icons.Filled.Category, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Referensi", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Cari nama barang, merek, atau SKU…") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Hapus pencarian")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("inventory_search_input")
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filters) { item ->
                            FilterChip(
                                selected = filterMode == item,
                                onClick = { filterMode = item },
                                label = { Text(item) }
                            )
                        }
                    }
                }
            }

            when (productsState) {
                is UiState.Loading -> LoadingCenter("Memuat daftar barang…")
                is UiState.Error -> Box(Modifier.padding(16.dp)) { ErrorBannerCard(productsState.message) }
                is UiState.Success -> {
                    if (filtered.isEmpty()) {
                        Box(Modifier.padding(16.dp)) {
                            EmptyStateCard(
                                icon = Icons.Filled.Inventory2,
                                title = "Belum Ada Barang",
                                subtitle = "Tambahkan barang pertama Anda lengkap dengan Harga Pokok dan Harga Jual multi-satuan.",
                                actionLabel = "Tambah Barang",
                                onAction = onAddProduct
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filtered, key = { it.id }) { product ->
                                ProductInventoryCard(
                                    product = product,
                                    onEdit = { onEditProduct(product) },
                                    onStock = { stockDialogProduct = product },
                                    onBarcode = { barcodeProduct = product },
                                    onDelete = { deleteCandidate = product }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    barcodeProduct?.let { prod ->
        BarcodePreviewDialog(
            product = prod,
            onDismiss = { barcodeProduct = null }
        )
    }

    stockDialogProduct?.let { prod ->
        QuickStockAdjustmentDialog(
            product = prod,
            isBusy = isBusy,
            onDismiss = { stockDialogProduct = null },
            onSubmit = { type, qty, unitName, qtyPerUnit, note ->
                onAdjustStock(prod, type, qty, unitName, qtyPerUnit, note)
                stockDialogProduct = null
            }
        )
    }

    deleteCandidate?.let { prod ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Hapus Barang?") },
            text = { Text("Barang \"${prod.name}\" akan dihapus dari katalog toko. Riwayat transaksi lama tetap tersimpan.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteProduct(prod)
                        deleteCandidate = null
                    }
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
private fun ProductInventoryCard(
    product: Product,
    onEdit: () -> Unit,
    onStock: () -> Unit,
    onBarcode: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append("SKU ${product.sku}")
                            if (product.categoryName.isNotBlank()) append(" · ${product.categoryName}")
                            if (product.brandName.isNotBlank()) append(" · ${product.brandName}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StockStatusPill(product = product)
            }

            // HPP & Multi-Unit Preview
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Harga Pokok (HPP)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatRupiah(product.costPrice),
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    HorizontalDivider()
                    product.priceUnits.forEach { unit ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${unit.unitName} (Isi ${unit.qtyContent})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "Def ${formatRupiah(unit.priceDefault)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Grs ${formatRupiah(unit.priceGrosir)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                color = AmberGrosir
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onEdit,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = onStock,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stok", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Row {
                    IconButton(onClick = onBarcode) {
                        Icon(
                            imageVector = Icons.Filled.QrCode2,
                            contentDescription = "Cetak Barcode",
                            tint = VioletVip
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Hapus Barang",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickStockAdjustmentDialog(
    product: Product,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (StockMovementType, Long, String, Long, String) -> Unit
) {
    var movementType by remember { mutableStateOf(StockMovementType.MASUK) }
    var selectedUnitIdx by remember { mutableStateOf(0) }
    var qtyText by remember { mutableStateOf("1") }
    var noteText by remember { mutableStateOf("") }

    val selectedUnit = product.priceUnits.getOrElse(selectedUnitIdx) { product.priceUnits.first() }
    val qtyVal = qtyText.toLongOrNull() ?: 0L

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Atur Stok Gudang",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${product.name} · Saat ini: ${product.stockBase} ${product.baseUnitName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StockMovementType.entries.forEach { type ->
                        FilterChip(
                            selected = movementType == type,
                            onClick = { movementType = type },
                            label = {
                                Text(
                                    when (type) {
                                        StockMovementType.MASUK -> "Masuk"
                                        StockMovementType.KELUAR -> "Keluar"
                                        StockMovementType.PENYESUAIAN -> "Opname"
                                    }
                                )
                            }
                        )
                    }
                }

                if (product.priceUnits.size > 1 && movementType != StockMovementType.PENYESUAIAN) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(product.priceUnits.indices.toList()) { idx ->
                            val u = product.priceUnits[idx]
                            FilterChip(
                                selected = selectedUnitIdx == idx,
                                onClick = { selectedUnitIdx = idx },
                                label = { Text("${u.unitName} (×${u.qtyContent})") }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it.filter(Char::isDigit) },
                    label = {
                        Text(
                            if (movementType == StockMovementType.PENYESUAIAN)
                                "Stok Fisik Akhir (${product.baseUnitName})"
                            else
                                "Jumlah (${selectedUnit.unitName})"
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Catatan (contoh: Restock Supplier)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Batal")
                    }
                    Button(
                        onClick = {
                            val uName = if (movementType == StockMovementType.PENYESUAIAN) product.baseUnitName else selectedUnit.unitName
                            val uQty = if (movementType == StockMovementType.PENYESUAIAN) 1L else selectedUnit.qtyContent
                            onSubmit(movementType, qtyVal, uName, uQty, noteText)
                        },
                        enabled = qtyVal > 0L && !isBusy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Simpan")
                    }
                }
            }
        }
    }
}
