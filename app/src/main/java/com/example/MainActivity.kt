package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.model.Product
import com.example.data.model.SaleTransaction
import com.example.data.model.StoreSettings
import com.example.data.repository.StoreRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOut
import com.example.ui.components.ReceiptDialog
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.InventoryScreen
import com.example.ui.screens.PosScreen
import com.example.ui.screens.ProductEditorScreen
import com.example.ui.screens.ReferencesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StockCardScreen
import com.example.ui.theme.TokoKitaTheme
import com.example.ui.viewmodel.StoreViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_POS = "pos"
const val ROUTE_INVENTORY = "inventory"
const val ROUTE_HISTORY = "history"
const val ROUTE_PRODUCT_EDITOR = "product_editor"
const val ROUTE_STOCK_CARDS = "stock_cards"
const val ROUTE_REFERENCES = "references"
const val ROUTE_SETTINGS = "settings"

private data class TopNavItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val topNavItems = listOf(
    TopNavItem(
        route = ROUTE_DASHBOARD,
        labelRes = R.string.nav_dashboard,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "nav_dashboard"
    ),
    TopNavItem(
        route = ROUTE_POS,
        labelRes = R.string.nav_pos,
        selectedIcon = Icons.Filled.PointOfSale,
        unselectedIcon = Icons.Outlined.PointOfSale,
        testTag = "nav_pos"
    ),
    TopNavItem(
        route = ROUTE_INVENTORY,
        labelRes = R.string.nav_inventory,
        selectedIcon = Icons.Filled.Inventory2,
        unselectedIcon = Icons.Outlined.Inventory2,
        testTag = "nav_inventory"
    ),
    TopNavItem(
        route = ROUTE_HISTORY,
        labelRes = R.string.nav_history,
        selectedIcon = Icons.AutoMirrored.Filled.ReceiptLong,
        unselectedIcon = Icons.AutoMirrored.Outlined.ReceiptLong,
        testTag = "nav_history"
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TokoKitaTheme {
                AppContent()
            }
        }
    }
}

@Composable
fun AppContent() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = { currentUser = Firebase.auth.currentUser }
        )
    } else {
        key(user.uid) {
            TokoKitaMainShell(
                currentUserId = user.uid,
                userEmail = user.email.orEmpty(),
                userDisplayName = user.displayName.orEmpty()
            )
        }
    }
}

@Composable
private fun TokoKitaMainShell(
    currentUserId: String,
    userEmail: String,
    userDisplayName: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: StoreViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                StoreViewModel(StoreRepository(db), currentUserId)
            }
        }
    )

    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
    val productsState by viewModel.productsState.collectAsStateWithLifecycle()
    val referencesState by viewModel.referencesState.collectAsStateWithLifecycle()
    val stockCardsState by viewModel.stockCardsState.collectAsStateWithLifecycle()
    val transactionsState by viewModel.transactionsState.collectAsStateWithLifecycle()
    val cartLines by viewModel.cartLines.collectAsStateWithLifecycle()
    val activeTier by viewModel.activeTier.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val feedbackMessage by viewModel.feedbackMessage.collectAsStateWithLifecycle()
    val lastCompletedReceipt by viewModel.lastCompletedReceipt.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_DASHBOARD
    val isTopLevelRoute = topNavItems.any { it.route == currentRoute }

    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var inspectingReceipt by remember { mutableStateOf<SaleTransaction?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(feedbackMessage) {
        val msg = feedbackMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    val navigateTopLevel: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(ROUTE_DASHBOARD) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val cartBadgeCount = cartLines.sumOf { it.qty }.toInt()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val showNavRail = maxWidth >= 720.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!showNavRail && isTopLevelRoute) {
                    NavigationBar {
                        topNavItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    if (item.route == ROUTE_POS && cartBadgeCount > 0) {
                                        BadgedBox(badge = { Badge { Text("$cartBadgeCount") } }) {
                                            Icon(
                                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                contentDescription = stringResource(item.labelRes)
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = stringResource(item.labelRes)
                                        )
                                    }
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showNavRail && isTopLevelRoute) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxHeight()
                            .windowInsetsPadding(WindowInsets(0))
                    ) {
                        topNavItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    if (item.route == ROUTE_POS && cartBadgeCount > 0) {
                                        BadgedBox(badge = { Badge { Text("$cartBadgeCount") } }) {
                                            Icon(
                                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                contentDescription = stringResource(item.labelRes)
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = stringResource(item.labelRes)
                                        )
                                    }
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_DASHBOARD
                    ) {
                        composable(ROUTE_DASHBOARD) {
                            DashboardScreen(
                                settingsState = settingsState,
                                productsState = productsState,
                                transactionsState = transactionsState,
                                isBusy = isBusy,
                                onOpenPos = { navigateTopLevel(ROUTE_POS) },
                                onAddProduct = {
                                    editingProduct = null
                                    navController.navigate(ROUTE_PRODUCT_EDITOR)
                                },
                                onOpenStockCards = { navController.navigate(ROUTE_STOCK_CARDS) },
                                onOpenReferences = { navController.navigate(ROUTE_REFERENCES) },
                                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                                onSeedStarter = { viewModel.seedStarterStore() },
                                onSelectTransaction = { inspectingReceipt = it }
                            )
                        }

                        composable(ROUTE_POS) {
                            PosScreen(
                                productsState = productsState,
                                cartLines = cartLines,
                                activeTier = activeTier,
                                isBusy = isBusy,
                                onSelectTier = viewModel::setActiveTier,
                                onAddToCart = viewModel::addToCart,
                                onUpdateQty = viewModel::updateCartQty,
                                onChangeUnit = viewModel::changeCartUnit,
                                onClearCart = viewModel::clearCart,
                                onCheckout = viewModel::checkout,
                                onAddProduct = {
                                    editingProduct = null
                                    navController.navigate(ROUTE_PRODUCT_EDITOR)
                                }
                            )
                        }

                        composable(ROUTE_INVENTORY) {
                            InventoryScreen(
                                productsState = productsState,
                                isBusy = isBusy,
                                onAddProduct = {
                                    editingProduct = null
                                    navController.navigate(ROUTE_PRODUCT_EDITOR)
                                },
                                onEditProduct = { prod ->
                                    editingProduct = prod
                                    navController.navigate(ROUTE_PRODUCT_EDITOR)
                                },
                                onDeleteProduct = viewModel::deleteProduct,
                                onAdjustStock = { prod, type, qty, unitName, qtyPerUnit, note ->
                                    viewModel.adjustStock(prod, type, qty, unitName, qtyPerUnit, note)
                                },
                                onOpenStockCards = { navController.navigate(ROUTE_STOCK_CARDS) },
                                onOpenReferences = { navController.navigate(ROUTE_REFERENCES) }
                            )
                        }

                        composable(ROUTE_HISTORY) {
                            HistoryScreen(
                                transactionsState = transactionsState,
                                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                                onSelectTransaction = { inspectingReceipt = it }
                            )
                        }

                        composable(ROUTE_PRODUCT_EDITOR) {
                            val refs = (referencesState as? UiState.Success)?.data.orEmpty()
                            ProductEditorScreen(
                                existingProduct = editingProduct,
                                references = refs,
                                isBusy = isBusy,
                                onBack = { navController.popBackStack() },
                                onSave = { sku, name, cat, brand, baseUnit, cost, initCost, adj, stock, minAlert, defIdx, defTier, units ->
                                    viewModel.saveProduct(
                                        existingProduct = editingProduct,
                                        sku = sku,
                                        name = name,
                                        categoryName = cat,
                                        brandName = brand,
                                        baseUnitName = baseUnit,
                                        costPrice = cost,
                                        initialCostPrice = initCost,
                                        costAdjustment = adj,
                                        stockBase = stock,
                                        minStockAlert = minAlert,
                                        defaultPriceUnitIndex = defIdx,
                                        defaultPriceTierType = defTier,
                                        priceUnits = units,
                                        onSaved = { navController.popBackStack() }
                                    )
                                }
                            )
                        }

                        composable(ROUTE_STOCK_CARDS) {
                            StockCardScreen(
                                stockCardsState = stockCardsState,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(ROUTE_REFERENCES) {
                            ReferencesScreen(
                                referencesState = referencesState,
                                isBusy = isBusy,
                                onAddReference = viewModel::addReference,
                                onDeleteReference = viewModel::deleteReference,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(ROUTE_SETTINGS) {
                            SettingsScreen(
                                userEmail = userEmail,
                                userDisplayName = userDisplayName,
                                settingsState = settingsState,
                                isBusy = isBusy,
                                onSaveSettings = viewModel::saveSettings,
                                onSignOut = {
                                    signOut(
                                        credentialManager = credentialManager,
                                        scope = scope
                                    )
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    val currentSettings = (settingsState as? UiState.Success)?.data ?: StoreSettings()

    lastCompletedReceipt?.let { trx ->
        ReceiptDialog(
            transaction = trx,
            settings = currentSettings,
            onDismiss = viewModel::dismissCompletedReceipt
        )
    }

    inspectingReceipt?.let { trx ->
        ReceiptDialog(
            transaction = trx,
            settings = currentSettings,
            onDismiss = { inspectingReceipt = null }
        )
    }
}
