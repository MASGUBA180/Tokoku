package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.PriceTierType
import com.example.data.model.PriceUnit
import com.example.data.model.Product
import com.example.data.model.ReferenceType
import com.example.data.model.StoreReference
import com.example.ui.components.formatNumberDots
import com.example.ui.theme.AmberGrosir
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.VioletVip
import kotlin.random.Random

private data class EditablePriceUnit(
    val unitName: String,
    val qtyContentText: String,
    val minBuyText: String,
    val priceDefaultText: String,
    val priceGrosirText: String,
    val priceMemberVipText: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductEditorScreen(
    existingProduct: Product?,
    references: List<StoreReference>,
    isBusy: Boolean,
    onBack: () -> Unit,
    onSave: (
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
        defaultUnitIdx: Long,
        defaultTier: PriceTierType,
        priceUnits: List<PriceUnit>
    ) -> Unit
) {
    BackHandler { onBack() }

    val categoryOptions = remember(references) {
        references.filter { it.referenceType() == ReferenceType.CATEGORY }.map { it.name }
    }
    val brandOptions = remember(references) {
        references.filter { it.referenceType() == ReferenceType.BRAND }.map { it.name }
    }
    val unitPresets = remember(references) {
        val fromRefs = references.filter { it.referenceType() == ReferenceType.UNIT }
        if (fromRefs.isNotEmpty()) fromRefs.map { it.name to it.defaultQtyPerUnit }
        else listOf("Satuan" to 1L, "Pak" to 6L, "Lusin" to 12L, "Kardus" to 24L)
    }

    var sku by remember(existingProduct?.id) {
        mutableStateOf(existingProduct?.sku ?: "899${Random.nextInt(100000000, 999999999)}")
    }
    var name by remember(existingProduct?.id) { mutableStateOf(existingProduct?.name ?: "") }
    var categoryName by remember(existingProduct?.id) {
        mutableStateOf(existingProduct?.categoryName ?: categoryOptions.firstOrNull().orEmpty())
    }
    var brandName by remember(existingProduct?.id) {
        mutableStateOf(existingProduct?.brandName ?: brandOptions.firstOrNull().orEmpty())
    }
    var baseUnitName by remember(existingProduct?.id) {
        mutableStateOf(existingProduct?.baseUnitName ?: "Satuan")
    }
    var costPriceText by remember(existingProduct?.id) {
        mutableStateOf((existingProduct?.costPrice ?: 11500L).toString())
    }
    val initialCostPrice = remember(existingProduct?.id) {
        existingProduct?.initialCostPrice ?: (existingProduct?.costPrice ?: 11500L)
    }
    var stockBaseText by remember(existingProduct?.id) {
        mutableStateOf((existingProduct?.stockBase ?: 24L).toString())
    }
    var minStockText by remember(existingProduct?.id) {
        mutableStateOf((existingProduct?.minStockAlert ?: 5L).toString())
    }
    var defaultUnitIdx by remember(existingProduct?.id) {
        mutableIntStateOf(existingProduct?.defaultPriceUnitIndex?.toInt() ?: 0)
    }
    var defaultTier by remember(existingProduct?.id) {
        mutableStateOf(existingProduct?.defaultTier() ?: PriceTierType.DEFAULT)
    }

    val priceRows = remember(existingProduct?.id) {
        mutableStateListOf<EditablePriceUnit>().apply {
            val initialUnits = existingProduct?.priceUnits?.takeIf { it.isNotEmpty() }
                ?: listOf(
                    PriceUnit("Satuan", 1L, 1L, 12600L, 12000L, 12000L),
                    PriceUnit("Kardus", 24L, 1L, 550000L, 520000L, 510000L)
                )
            initialUnits.forEach { u ->
                add(
                    EditablePriceUnit(
                        unitName = u.unitName,
                        qtyContentText = u.qtyContent.toString(),
                        minBuyText = u.minBuy.toString(),
                        priceDefaultText = u.priceDefault.toString(),
                        priceGrosirText = u.priceGrosir.toString(),
                        priceMemberVipText = u.priceMemberVip.toString()
                    )
                )
            }
        }
    }

    val currentCost = costPriceText.toLongOrNull() ?: 0L
    val computedInitialCost = if (existingProduct == null) currentCost else initialCostPrice
    val costAdjustment = currentCost - computedInitialCost

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (existingProduct == null) "Tambah Barang Baru" else "Edit Barang & Harga",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Informasi Barang & Gudang
            item {
                EditorSectionCard(title = "Informasi Barang & Gudang") {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nama Barang *") },
                        placeholder = { Text("Contoh: Minyak Goreng Pouch 1L") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("product_name_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = sku,
                            onValueChange = { sku = it },
                            label = { Text("SKU / Barcode *") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("product_sku_input")
                        )
                        OutlinedButton(
                            onClick = { sku = "899${Random.nextInt(100000000, 999999999)}" },
                            modifier = Modifier.height(54.dp)
                        ) {
                            Icon(Icons.Filled.QrCode2, contentDescription = "Buat SKU acak")
                        }
                    }

                    OutlinedTextField(
                        value = categoryName,
                        onValueChange = { categoryName = it },
                        label = { Text("Kategori") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (categoryOptions.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(categoryOptions) { cat ->
                                FilterChip(
                                    selected = categoryName == cat,
                                    onClick = { categoryName = cat },
                                    label = { Text(cat) }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = brandName,
                        onValueChange = { brandName = it },
                        label = { Text("Merek") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (brandOptions.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(brandOptions) { brand ->
                                FilterChip(
                                    selected = brandName == brand,
                                    onClick = { brandName = brand },
                                    label = { Text(brand) }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = baseUnitName,
                            onValueChange = { baseUnitName = it },
                            label = { Text("Satuan Dasar") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = stockBaseText,
                            onValueChange = { stockBaseText = it.filter(Char::isDigit) },
                            label = { Text("Stok (${baseUnitName.ifEmpty { "Satuan" }})") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = minStockText,
                            onValueChange = { minStockText = it.filter(Char::isDigit) },
                            label = { Text("Min. Alert") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(0.8f)
                        )
                    }
                }
            }

            // Section 2: Harga Pokok (HPP) - matching screenshot!
            item {
                EditorSectionCard(title = "Harga Pokok") {
                    OutlinedTextField(
                        value = costPriceText,
                        onValueChange = { costPriceText = it.filter(Char::isDigit) },
                        label = { Text("Harga Pokok (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("product_cost_input")
                    )
                    Text(
                        text = "Harga awal: ${formatNumberDots(computedInitialCost)}, Adjustment: ${formatNumberDots(costAdjustment)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Section 3: Harga Jual Multi-Satuan - matching screenshot!
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Harga Jual Multi-Satuan",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = {
                            if (priceRows.size < 10) {
                                priceRows.add(
                                    EditablePriceUnit(
                                        unitName = "Lusin",
                                        qtyContentText = "12",
                                        minBuyText = "1",
                                        priceDefaultText = (currentCost * 12 + 12000).toString(),
                                        priceGrosirText = (currentCost * 12 + 8000).toString(),
                                        priceMemberVipText = (currentCost * 12 + 6000).toString()
                                    )
                                )
                            }
                        },
                        enabled = priceRows.size < 10,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("add_price_unit_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tambah Harga")
                    }
                }
            }

            itemsIndexed(priceRows) { index, row ->
                PriceUnitEditorCard(
                    index = index,
                    row = row,
                    unitPresets = unitPresets,
                    canDelete = priceRows.size > 1,
                    defaultUnitIdx = defaultUnitIdx,
                    defaultTier = defaultTier,
                    onUpdate = { updated -> priceRows[index] = updated },
                    onDelete = {
                        priceRows.removeAt(index)
                        if (defaultUnitIdx >= priceRows.size) {
                            defaultUnitIdx = (priceRows.size - 1).coerceAtLeast(0)
                        }
                    },
                    onSelectDefault = { tier ->
                        defaultUnitIdx = index
                        defaultTier = tier
                    }
                )
            }

            // Explanatory note + Action buttons
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "*) Default harga berguna untuk menampilkan harga pada menu kasir dan menampilkan harga pertama kali ketika barang dipilih (menu kasir/penjualan).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                name = ""
                                categoryName = ""
                                brandName = ""
                                costPriceText = "0"
                                stockBaseText = "0"
                                minStockText = "5"
                                priceRows.clear()
                                priceRows.add(
                                    EditablePriceUnit("Satuan", "1", "1", "0", "0", "0")
                                )
                                defaultUnitIdx = 0
                                defaultTier = PriceTierType.DEFAULT
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberGrosir),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("product_clear_form_button")
                        ) {
                            Text("Reset Form")
                        }
                        Button(
                            onClick = {
                                val mappedUnits = priceRows.map { r ->
                                    PriceUnit(
                                        unitName = r.unitName.trim().ifEmpty { "Satuan" },
                                        qtyContent = (r.qtyContentText.toLongOrNull() ?: 1L).coerceAtLeast(1L),
                                        minBuy = (r.minBuyText.toLongOrNull() ?: 1L).coerceAtLeast(1L),
                                        priceDefault = r.priceDefaultText.toLongOrNull() ?: 0L,
                                        priceGrosir = r.priceGrosirText.toLongOrNull() ?: 0L,
                                        priceMemberVip = r.priceMemberVipText.toLongOrNull() ?: 0L
                                    )
                                }
                                onSave(
                                    sku,
                                    name,
                                    categoryName,
                                    brandName,
                                    baseUnitName,
                                    currentCost,
                                    computedInitialCost,
                                    costAdjustment,
                                    stockBaseText.toLongOrNull() ?: 0L,
                                    minStockText.toLongOrNull() ?: 0L,
                                    defaultUnitIdx.toLong(),
                                    defaultTier,
                                    mappedUnits
                                )
                            },
                            enabled = name.isNotBlank() && sku.isNotBlank() && !isBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = CobaltPrimary),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(50.dp)
                                .testTag("product_submit_button")
                        ) {
                            Text("Simpan Barang", fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun EditorSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            content()
        }
    }
}

@Composable
private fun PriceUnitEditorCard(
    index: Int,
    row: EditablePriceUnit,
    unitPresets: List<Pair<String, Long>>,
    canDelete: Boolean,
    defaultUnitIdx: Int,
    defaultTier: PriceTierType,
    onUpdate: (EditablePriceUnit) -> Unit,
    onDelete: () -> Unit,
    onSelectDefault: (PriceTierType) -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (defaultUnitIdx == index) 1.5.dp else 1.dp,
            color = if (defaultUnitIdx == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Satuan #${index + 1}: ${row.unitName.ifEmpty { "Satuan" }}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (canDelete) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Hapus satuan harga",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(unitPresets) { (presetName, presetQty) ->
                    FilterChip(
                        selected = row.unitName == presetName,
                        onClick = {
                            onUpdate(row.copy(unitName = presetName, qtyContentText = presetQty.toString()))
                        },
                        label = { Text("$presetName (×$presetQty)") }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = row.unitName,
                    onValueChange = { onUpdate(row.copy(unitName = it)) },
                    label = { Text("Satuan Jual") },
                    singleLine = true,
                    modifier = Modifier.weight(1.2f)
                )
                OutlinedTextField(
                    value = row.qtyContentText,
                    onValueChange = { onUpdate(row.copy(qtyContentText = it.filter(Char::isDigit))) },
                    label = { Text("Qty Isi") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(0.9f)
                )
                OutlinedTextField(
                    value = row.minBuyText,
                    onValueChange = { onUpdate(row.copy(minBuyText = it.filter(Char::isDigit))) },
                    label = { Text("Min. Beli") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(0.9f)
                )
            }

            HorizontalDivider()

            PriceTierStepperRow(
                label = "Default",
                accent = CobaltPrimary,
                valueText = row.priceDefaultText,
                isDefaultSelected = defaultUnitIdx == index && defaultTier == PriceTierType.DEFAULT,
                onValueChange = { onUpdate(row.copy(priceDefaultText = it)) },
                onSelectDefault = { onSelectDefault(PriceTierType.DEFAULT) }
            )

            PriceTierStepperRow(
                label = "Grosir",
                accent = AmberGrosir,
                valueText = row.priceGrosirText,
                isDefaultSelected = defaultUnitIdx == index && defaultTier == PriceTierType.GROSIR,
                onValueChange = { onUpdate(row.copy(priceGrosirText = it)) },
                onSelectDefault = { onSelectDefault(PriceTierType.GROSIR) }
            )

            PriceTierStepperRow(
                label = "Member VIP",
                accent = VioletVip,
                valueText = row.priceMemberVipText,
                isDefaultSelected = defaultUnitIdx == index && defaultTier == PriceTierType.MEMBER_VIP,
                onValueChange = { onUpdate(row.copy(priceMemberVipText = it)) },
                onSelectDefault = { onSelectDefault(PriceTierType.MEMBER_VIP) }
            )
        }
    }
}

@Composable
private fun PriceTierStepperRow(
    label: String,
    accent: Color,
    valueText: String,
    isDefaultSelected: Boolean,
    onValueChange: (String) -> Unit,
    onSelectDefault: () -> Unit
) {
    val currentVal = valueText.toLongOrNull() ?: 0L
    val step = if (currentVal >= 50000L) 5000L else 500L

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Column(modifier = Modifier.width(84.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Text(
                text = "Default",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        RadioButton(
            selected = isDefaultSelected,
            onClick = onSelectDefault
        )

        IconButton(
            onClick = { onValueChange((currentVal - step).coerceAtLeast(0L).toString()) }
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "Kurangi harga $label")
        }

        OutlinedTextField(
            value = valueText,
            onValueChange = { onValueChange(it.filter(Char::isDigit)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMono),
            modifier = Modifier.weight(1f)
        )

        IconButton(
            onClick = { onValueChange((currentVal + step).toString()) }
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Tambah harga $label")
        }
    }
}
