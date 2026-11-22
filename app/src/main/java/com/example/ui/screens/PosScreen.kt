package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.model.CartLine
import com.example.data.model.PaymentMethod
import com.example.data.model.PriceTierType
import com.example.data.model.Product
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorBannerCard
import com.example.ui.components.LoadingCenter
import com.example.ui.components.StockStatusPill
import com.example.ui.components.TierBadge
import com.example.ui.components.formatRupiah
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.JetBrainsMono
import com.example.ui.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosScreen(
    productsState: UiState<List<Product>>,
    cartLines: List<CartLine>,
    activeTier: PriceTierType,
    isBusy: Boolean,
    onSelectTier: (PriceTierType) -> Unit,
    onAddToCart: (Product) -> Unit,
    onUpdateQty: (String, Int, Long) -> Unit,
    onChangeUnit: (String, Int, Int) -> Unit,
    onClearCart: () -> Unit,
    onCheckout: (String, PaymentMethod, Long) -> Unit,
    onAddProduct: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Semua") }
    var showCartSheet by remember { mutableStateOf(false) }
    var showCheckoutDialog by remember { mutableStateOf(false) }

    val products = (productsState as? UiState.Success)?.data.orEmpty()
    val categories = remember(products) {
        listOf("Semua") + products.mapNotNull { it.categoryName.takeIf(String::isNotBlank) }.distinct().sorted()
    }
    val filteredProducts = remember(products, searchQuery, selectedCategory) {
        products.filter { p ->
            val matchesCat = selectedCategory == "Semua" || p.categoryName == selectedCategory
            val q = searchQuery.trim().lowercase()
            val matchesQ = q.isEmpty() || p.name.lowercase().contains(q) || p.sku.lowercase().contains(q)
            matchesCat && matchesQ
        }
    }

    val totalAmount = remember(cartLines, activeTier) {
        cartLines.sumOf { it.subtotalFor(activeTier) }
    }
    val totalQty = remember(cartLines) {
        cartLines.sumOf { it.qty }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isWide = maxWidth >= 720.dp

        if (isWide) {
            Row(modifier = Modifier.fillMaxSize()) {
                PosCatalogPane(
                    productsState = productsState,
                    filteredProducts = filteredProducts,
                    categories = categories,
                    selectedCategory = selectedCategory,
                    searchQuery = searchQuery,
                    activeTier = activeTier,
                    onSearchChange = { searchQuery = it },
                    onCategorySelect = { selectedCategory = it },
                    onSelectTier = onSelectTier,
                    onAddToCart = onAddToCart,
                    onAddProduct = onAddProduct,
                    modifier = Modifier
                        .weight(1.3f)
                        .fillMaxHeight()
                )
                VerticalCartPane(
                    cartLines = cartLines,
                    activeTier = activeTier,
                    totalAmount = totalAmount,
                    isBusy = isBusy,
                    onUpdateQty = onUpdateQty,
                    onChangeUnit = onChangeUnit,
                    onClearCart = onClearCart,
                    onOpenCheckout = { showCheckoutDialog = true },
                    modifier = Modifier
                        .weight(0.95f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface)
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                PosCatalogPane(
                    productsState = productsState,
                    filteredProducts = filteredProducts,
                    categories = categories,
                    selectedCategory = selectedCategory,
                    searchQuery = searchQuery,
                    activeTier = activeTier,
                    onSearchChange = { searchQuery = it },
                    onCategorySelect = { selectedCategory = it },
                    onSelectTier = onSelectTier,
                    onAddToCart = onAddToCart,
                    onAddProduct = onAddProduct,
                    modifier = Modifier.weight(1f)
                )

                // Sticky Cart Bar on Mobile
                if (cartLines.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCartSheet = true }
                            .testTag("pos_sticky_cart_bar")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ShoppingCart,
                                    contentDescription = "Keranjang",
                                    tint = Color.White
                                )
                                Column {
                                    Text(
                                        text = "$totalQty item (${activeTier.badgeText})",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Ketuk untuk lihat keranjang & satuan",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = formatRupiah(totalAmount),
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Button(
                                    onClick = { showCheckoutDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmeraldAccent,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("pos_quick_pay_button")
                                ) {
                                    Text("Bayar")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCartSheet) {
        ModalBottomSheet(onDismissRequest = { showCartSheet = false }) {
            VerticalCartPane(
                cartLines = cartLines,
                activeTier = activeTier,
                totalAmount = totalAmount,
                isBusy = isBusy,
                onUpdateQty = onUpdateQty,
                onChangeUnit = onChangeUnit,
                onClearCart = {
                    onClearCart()
                    showCartSheet = false
                },
                onOpenCheckout = {
                    showCartSheet = false
                    showCheckoutDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(520.dp)
            )
        }
    }

    if (showCheckoutDialog) {
        CheckoutDialog(
            totalAmount = totalAmount,
            activeTier = activeTier,
            isBusy = isBusy,
            onDismiss = { showCheckoutDialog = false },
            onConfirm = { customerName, paymentMethod, cashPaid ->
                onCheckout(customerName, paymentMethod, cashPaid)
                showCheckoutDialog = false
            }
        )
    }
}

@Composable
private fun PosCatalogPane(
    productsState: UiState<List<Product>>,
    filteredProducts: List<Product>,
    categories: List<String>,
    selectedCategory: String,
    searchQuery: String,
    activeTier: PriceTierType,
    onSearchChange: (String) -> Unit,
    onCategorySelect: (String) -> Unit,
    onSelectTier: (PriceTierType) -> Unit,
    onAddToCart: (Product) -> Unit,
    onAddProduct: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Tier & Search header
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PriceTierType.entries.forEachIndexed { idx, tier ->
                        SegmentedButton(
                            selected = activeTier == tier,
                            onClick = { onSelectTier(tier) },
                            shape = SegmentedButtonDefaults.itemShape(idx, PriceTierType.entries.size),
                            modifier = Modifier.testTag("tier_selector_${tier.name.lowercase()}")
                        ) {
                            Text(
                                text = tier.badgeText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Cari nama barang atau scan/ketik SKU…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Hapus pencarian")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pos_search_input")
                )

                if (categories.size > 1) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(categories) { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { onCategorySelect(cat) },
                                label = { Text(cat) }
                            )
                        }
                    }
                }
            }
        }

        when (productsState) {
            is UiState.Loading -> LoadingCenter("Memuat katalog kasir…")
            is UiState.Error -> Box(Modifier.padding(16.dp)) { ErrorBannerCard(productsState.message) }
            is UiState.Success -> {
                if (filteredProducts.isEmpty()) {
                    Box(Modifier.padding(16.dp)) {
                        EmptyStateCard(
                            icon = Icons.Filled.Inventory2,
                            title = "Barang Tidak Ditemukan",
                            subtitle = "Tambah barang baru terlebih dahulu atau ubah kata kunci pencarian.",
                            actionLabel = "Tambah Barang",
                            onAction = onAddProduct
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredProducts, key = { it.id }) { product ->
                            val defaultUnit = product.defaultUnit()
                            val tierPrice = defaultUnit.priceForTier(activeTier)
                            Card(
                                onClick = { onAddToCart(product) },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pos_product_card_${product.sku}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = product.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "SKU ${product.sku}" + if (product.categoryName.isNotBlank()) " · ${product.categoryName}" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            StockStatusPill(product = product)
                                            if (product.priceUnits.size > 1) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Text(
                                                        text = "${product.priceUnits.size} satuan",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = formatRupiah(tierPrice),
                                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono),
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "per ${defaultUnit.unitName}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Add,
                                                    contentDescription = "Tambah ke keranjang",
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = "Tambah",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VerticalCartPane(
    cartLines: List<CartLine>,
    activeTier: PriceTierType,
    totalAmount: Long,
    isBusy: Boolean,
    onUpdateQty: (String, Int, Long) -> Unit,
    onChangeUnit: (String, Int, Int) -> Unit,
    onClearCart: () -> Unit,
    onOpenCheckout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Keranjang Kasir",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TierBadge(tier = activeTier)
            }
            if (cartLines.isNotEmpty()) {
                TextButton(onClick = onClearCart) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Kosongkan")
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        if (cartLines.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Ketuk barang di katalog untuk menambahkan ke keranjang.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(cartLines, key = { "${it.product.id}_${it.unitIndex}" }) { line ->
                    CartLineCard(
                        line = line,
                        activeTier = activeTier,
                        onUpdateQty = { delta -> onUpdateQty(line.product.id, line.unitIndex, delta) },
                        onChangeUnit = { newIdx -> onChangeUnit(line.product.id, line.unitIndex, newIdx) }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total Bayar",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatRupiah(totalAmount),
                    style = MaterialTheme.typography.headlineMedium.copy(fontFamily = JetBrainsMono),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onOpenCheckout,
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("pos_checkout_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent)
            ) {
                Icon(Icons.Filled.Payments, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Proses Pembayaran", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CartLineCard(
    line: CartLine,
    activeTier: PriceTierType,
    onUpdateQty: (Long) -> Unit,
    onChangeUnit: (Int) -> Unit
) {
    var unitMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = line.product.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatRupiah(line.subtotalFor(activeTier)),
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMono),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Unit Selector dropdown
                Box {
                    OutlinedButton(
                        onClick = { if (line.product.priceUnits.size > 1) unitMenuExpanded = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${line.selectedUnit.unitName} (${formatRupiah(line.unitPriceFor(activeTier))})",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    DropdownMenu(
                        expanded = unitMenuExpanded,
                        onDismissRequest = { unitMenuExpanded = false }
                    ) {
                        line.product.priceUnits.forEachIndexed { idx, unit ->
                            DropdownMenuItem(
                                text = {
                                    Text("${unit.unitName} (Isi ${unit.qtyContent}) · ${formatRupiah(unit.priceForTier(activeTier))}")
                                },
                                onClick = {
                                    unitMenuExpanded = false
                                    onChangeUnit(idx)
                                }
                            )
                        }
                    }
                }

                // Stepper
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onUpdateQty(-1L) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.Remove, contentDescription = "Kurangi jumlah")
                    }
                    Text(
                        text = "${line.qty}",
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                    IconButton(
                        onClick = { onUpdateQty(1L) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Tambah jumlah")
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckoutDialog(
    totalAmount: Long,
    activeTier: PriceTierType,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, PaymentMethod, Long) -> Unit
) {
    var customerName by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf(PaymentMethod.TUNAI) }
    var cashPaidText by remember { mutableStateOf(totalAmount.toString()) }

    val cashPaid = cashPaidText.toLongOrNull() ?: 0L
    val change = (cashPaid - totalAmount).coerceAtLeast(0L)
    val canPay = paymentMethod != PaymentMethod.TUNAI || cashPaid >= totalAmount

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pembayaran Kasir",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    TierBadge(tier = activeTier)
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Total Tagihan",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = formatRupiah(totalAmount),
                            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = JetBrainsMono),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                OutlinedTextField(
                    value = customerName,
                    onValueChange = { customerName = it },
                    label = { Text("Nama Pelanggan (Opsional)") },
                    placeholder = { Text("Pelanggan Umum") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Metode Pembayaran",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PaymentMethod.entries.forEach { method ->
                        FilterChip(
                            selected = paymentMethod == method,
                            onClick = { paymentMethod = method },
                            label = { Text(method.label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (paymentMethod == PaymentMethod.TUNAI) {
                    OutlinedTextField(
                        value = cashPaidText,
                        onValueChange = { cashPaidText = it.filter(Char::isDigit) },
                        label = { Text("Uang Tunai Diterima (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("checkout_cash_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { cashPaidText = totalAmount.toString() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Pas", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { cashPaidText = "50000" },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("50rb", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { cashPaidText = "100000" },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("100rb", maxLines = 1)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Kembalian",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = if (cashPaid >= totalAmount) formatRupiah(change) else "Kurang ${formatRupiah(totalAmount - cashPaid)}",
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMono),
                            fontWeight = FontWeight.Bold,
                            color = if (cashPaid >= totalAmount) EmeraldAccent else MaterialTheme.colorScheme.error
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Batal")
                    }
                    Button(
                        onClick = { onConfirm(customerName, paymentMethod, cashPaid) },
                        enabled = canPay && !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("confirm_checkout_button")
                    ) {
                        Text("Selesaikan")
                    }
                }
            }
        }
    }
}
