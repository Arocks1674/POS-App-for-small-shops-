package com.example.ui

import android.Manifest
import android.annotation.SuppressLint
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.data.database.SaleTransaction
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.text.SimpleDateFormat
import java.util.*

// Vibrant high contrast AMOLED optimized colors & High Density Theme
val AmoledBlack = Color(0xFF000000)
val DarkGrayBg = Color(0xFF09090B) // Zinc 950
val CardGray = Color(0xFF27272A) // Zinc 800

val CigaretteRed = Color(0xFF7F1D1D) // Red 900
val CigaretteRedLight = Color(0xFF450A0A) // Red 950
val PanGreen = Color(0xFF064E3B) // Emerald 950
val PanGreenLight = Color(0xFF065F46) // Emerald 900
val DrinkBlue = Color(0xFF0C4A6E) // Sky 900
val DrinkBlueLight = Color(0xFF075985) // Sky 800 / Active Accent
val SnackYellow = Color(0xFFF59E0B) // Amber 500 (High contrast text)
val SnackOrange = Color(0xFFD97706) // Amber 600 (High contrast text)

val CashGreen = Color(0xFF16A34A) // Green 600
val UpiBlue = Color(0xFF2563EB) // Blue 600

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainPosScreen(viewModel: PosViewModel) {
    val language by viewModel.selectedLanguage.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val keypadBuffer by viewModel.keypadBuffer.collectAsState()
    val cartItems by viewModel.cartItems.collectAsState()
    val cartTotal by viewModel.cartTotal.collectAsState()
    val cartItemCount by viewModel.cartItemCount.collectAsState()
    val isScannerActive by viewModel.isScannerActive.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()
    val scanMessage by viewModel.scanResultMessage.collectAsState()

    val cashTotalDb by viewModel.cashTotal.collectAsState()
    val upiTotalDb by viewModel.upiTotal.collectAsState()
    val recentSales by viewModel.recentTransactions.collectAsState()

    var isHistoryDrawerOpen by remember { mutableStateOf(false) }
    var isCartDrawerOpen by remember { mutableStateOf(false) }

    // Floating Water Bubble expansion state
    var showWaterBubbles by remember { mutableStateOf(false) }
    var waterBubbleTimerJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = AmoledBlack
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // ZONE 1: Top Bar (10% height)
                TopBar(
                    isOnline = isOnline,
                    language = language,
                    cartTotal = cartTotal,
                    cartItemCount = cartItemCount,
                    onLanguageClick = { viewModel.cycleLanguage() },
                    onSyncClick = { viewModel.toggleOnlineSync() },
                    onScannerClick = { viewModel.toggleScanner() },
                    onCartClick = { isCartDrawerOpen = true }
                )

                HorizontalDivider(color = Color(0xFF27272A), thickness = 1.dp)

                // ZONE 2: 80/20 Tiles Grid (50% height)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.53f)
                        .background(AmoledBlack)
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    ProductGrid(
                        language = language,
                        onAddProduct = { key, name, price, color ->
                            viewModel.addItemToBasket(key, name, price, color)
                        },
                        onWaterLooseClick = {
                            viewModel.playBeep()
                            // Toggle Expandable Water bubbles
                            showWaterBubbles = !showWaterBubbles
                        },
                        showWaterBubbles = showWaterBubbles,
                        onSelectWaterValue = { price ->
                            viewModel.addLooseWaterQuantity(price)
                            showWaterBubbles = false
                        }
                    )
                }

                HorizontalDivider(color = Color(0xFF27272A), thickness = 1.dp)

                // ZONE 3 & Action Bar (40% height)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.37f)
                        .background(DarkGrayBg)
                ) {
                    KeypadAndPaymentArea(
                        language = language,
                        keypadBuffer = keypadBuffer,
                        cartTotal = cartTotal,
                        cartItemCount = cartItemCount,
                        onKeypadPress = { viewModel.appendKeypad(it) },
                        onBackspace = { viewModel.backspaceKeypad() },
                        onAddGeneric = { viewModel.addKeypadAmountToBill() },
                        onPaymentClick = { type -> viewModel.collectPayment(type) },
                        onOpenLogs = { isHistoryDrawerOpen = true },
                        onClearCart = { viewModel.clearCart() },
                        onOpenCart = { isCartDrawerOpen = true }
                    )
                }
            }

            // ACTIVE CART DRAWER OVERLAY
            if (isCartDrawerOpen) {
                ActiveCartDrawer(
                    language = language,
                    cartItems = cartItems,
                    cartTotal = cartTotal,
                    onIncrement = { viewModel.incrementQuantity(it) },
                    onDecrement = { viewModel.decrementQuantity(it) },
                    onRemoveItem = { viewModel.removeCartItem(it) },
                    onClearCart = { viewModel.clearCart() },
                    onCheckoutCash = {
                        viewModel.completeSale("CASH") {
                            isCartDrawerOpen = false
                        }
                    },
                    onCheckoutUpi = {
                        viewModel.completeSale("UPI") {
                            isCartDrawerOpen = false
                        }
                    },
                    onClose = { isCartDrawerOpen = false }
                )
            }

            // BARCODE CAMERA VIEWER OVERLAY
            if (isScannerActive) {
                BarcodeScannerOverlay(
                    language = language,
                    onDismiss = { viewModel.toggleScanner() },
                    onMockScan = { barcode -> viewModel.processBarcodeScanned(barcode) }
                )
            }

            // TRANSACTION SYNC / PROGRESS DIALOG STICKERS
            syncMessage?.let { status ->
                SyncOverlayMessage(status = status, language = language)
            }

            // DIRECT SCAN FLOATING TOAST
            scanMessage?.let { itemScanned ->
                FloatingScanIndicator(itemLabel = itemScanned, language = language)
            }

            // SLIDE-OUT TRANSACTION LOGS DRAWER
            if (isHistoryDrawerOpen) {
                TransactionsHistoryDrawer(
                    language = language,
                    recentSales = recentSales,
                    cashTotal = cashTotalDb,
                    upiTotal = upiTotalDb,
                    onClose = { isHistoryDrawerOpen = false },
                    onResetLogs = { viewModel.resetSalesHistory() }
                )
            }
        }
    }
}

// ==========================================
// ZONE 1: TOP BAR COMPONENT
// ==========================================
@Composable
fun TopBar(
    isOnline: Boolean,
    language: PosLanguage,
    cartTotal: Int,
    cartItemCount: Int,
    onLanguageClick: () -> Unit,
    onSyncClick: () -> Unit,
    onScannerClick: () -> Unit,
    onCartClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(Color(0xFF09090B)) // Zinc-950 background
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Side: Dot Indicator + Network status styled with glowing/high-contrast density
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onSyncClick)
                .background(Color(0xFF18181B)) // Zinc-900
                .border(1.dp, Color(0xFF27272A), RoundedCornerShape(8.dp)) // Zinc-800
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .testTag("network_sync_pill"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (isOnline) Color(0xFF22C55E) else Color(0xFF71717A)) // Emerald 500
                    .drawBehind {
                        if (isOnline) {
                            drawCircle(
                                color = Color(0xFF22C55E).copy(alpha = 0.4f),
                                radius = size.minDimension * 0.95f
                            )
                        }
                    }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = Localization.get(if (isOnline) "ONLINE" else "OFFLINE", language),
                color = Color(0xFFA1A1AA), // Zinc-400
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp
            )
        }

        // Center Area: MASSIVE RUNNING TOTAL (Muscle Memory focal point & cart trigger)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onCartClick)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = Localization.get("TOTAL", language).uppercase(),
                    color = Color(0xFF71717A), // Zinc-500
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                if (cartItemCount > 0) {
                    Text(
                        text = "• $cartItemCount",
                        color = Color(0xFFFBBF24),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            Text(
                text = "₹ $cartTotal",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("running_total_display")
            )
        }

        // Right side: Active Actions (Cart, Language & Scanner)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Active Cart button with Badge
            Box(
                modifier = Modifier
                    .size(width = 50.dp, height = 42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (cartItemCount > 0) Color(0xFF15803D) else Color(0xFF18181B)) // Green-700 when active
                    .border(1.dp, if (cartItemCount > 0) Color(0xFF22C55E) else Color(0xFF27272A), RoundedCornerShape(8.dp))
                    .clickable(onClick = onCartClick)
                    .testTag("cart_trigger_button"),
                contentAlignment = Alignment.Center
            ) {
                BadgedBox(
                    badge = {
                        if (cartItemCount > 0) {
                            Badge(
                                containerColor = Color(0xFFEF4444),
                                contentColor = Color.White
                            ) {
                                Text("$cartItemCount", fontSize = 9.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.ShoppingCart,
                        contentDescription = "Active Cart",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Dynamic Language Selector Pill
            Box(
                modifier = Modifier
                    .size(width = 46.dp, height = 42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF18181B)) // Zinc-900
                    .border(1.dp, Color(0xFF27272A), RoundedCornerShape(8.dp))
                    .clickable(onClick = onLanguageClick),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = language.name,
                    color = Color(0xFFF59E0B), // Amber-500
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    modifier = Modifier.testTag("language_toggle")
                )
            }

            // Compact Camera Barcode Scanner trigger
            Box(
                modifier = Modifier
                    .size(width = 46.dp, height = 42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2563EB)) // Blue-600 active trigger
                    .clickable(onClick = onScannerClick)
                    .testTag("scanner_trigger_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.QrCodeScanner,
                    contentDescription = "Scan Barcode",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}// ==========================================
// ZONE 2: THE 80/20 PRODUCT GRID
// ==========================================
@Composable
fun ProductGrid(
    language: PosLanguage,
    onAddProduct: (String, String, Int, String) -> Unit,
    onWaterLooseClick: () -> Unit,
    showWaterBubbles: Boolean,
    onSelectWaterValue: (Int) -> Unit
) {
    // High Density dense grid with 6dp spacing to maximize visual scanning speed and layout cohesion
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // TILE 1: Cigarette - Gold Flake Split Tile (Horizontal Split Red)
        item {
            SplitTileCompact(
                leftLabel = Localization.get("GOLD_FLAKE", language) + "\n(" + Localization.get("LOOSE", language) + ")",
                leftPrice = 15,
                rightLabel = Localization.get("PACK", language),
                rightPrice = 300,
                colorMain = CigaretteRed,
                colorAccent = CigaretteRedLight,
                onLeftClick = { onAddProduct("GOLD_FLAKE_L", "Gold Flake (Loose)", 15, "RED") },
                onRightClick = { onAddProduct("GOLD_FLAKE_P", "Gold Flake (Pack)", 300, "RED") }
            )
        }

        // TILE 2: Cigarette - Classic Split Tile (Horizontal Split Red)
        item {
            SplitTileCompact(
                leftLabel = Localization.get("CLASSIC", language) + "\n(" + Localization.get("LOOSE", language) + ")",
                leftPrice = 18,
                rightLabel = Localization.get("PACK", language),
                rightPrice = 360,
                colorMain = CigaretteRed,
                colorAccent = CigaretteRedLight,
                onLeftClick = { onAddProduct("CLASSIC_L", "Classic (Loose)", 18, "RED") },
                onRightClick = { onAddProduct("CLASSIC_P", "Classic (Pack)", 360, "RED") }
            )
        }

        // TILE 3: Meetha Pan (Deep Green Tile)
        item {
            ProductTileSingle(
                title = Localization.get("MEETHA_PAN", language),
                price = 30,
                color = PanGreen,
                onClick = { onAddProduct("MEETHA_PAN", "Meetha Pan", 30, "GREEN") }
            )
        }

        // TILE 4: Sada Pan (Deep Green Tile)
        item {
            ProductTileSingle(
                title = Localization.get("SADA_PAN", language),
                price = 20,
                color = PanGreen,
                onClick = { onAddProduct("SADA_PAN", "Sada Pan", 20, "GREEN") }
            )
        }

        // TILE 5: Zarda Pan (Deep Green Tile / Maroon accent)
        item {
            ProductTileSingle(
                title = Localization.get("ZARDA_PAN", language),
                price = 25,
                color = PanGreen,
                onClick = { onAddProduct("ZARDA_PAN", "Zarda Pan", 25, "GREEN") }
            )
        }

        // TILE 6: Special Pan (Deep Green Tile)
        item {
            ProductTileSingle(
                title = Localization.get("SPL_PAN", language),
                price = 50,
                color = PanGreenLight,
                onClick = { onAddProduct("SPL_PAN", "Special Pan", 50, "GREEN") }
            )
        }

        // TILE 7: Packaged Water Split-Tile (Bright Blue)
        item {
            Box(modifier = Modifier.fillMaxSize()) {
                SplitTileCompact(
                    leftLabel = Localization.get("LOOSE_WATER", language),
                    leftPrice = 0, // Click to expand price bubble of 3 & 5
                    rightLabel = Localization.get("WATER", language),
                    rightPrice = 20,
                    colorMain = DrinkBlue,
                    colorAccent = DrinkBlueLight,
                    onLeftClick = onWaterLooseClick,
                    onRightClick = { onAddProduct("WATER_1L", "Water Bottle 1L", 20, "BLUE") }
                )

                // Expanded Floating Action Bubbles for water values
                if (showWaterBubbles) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // ₹3 Bubble
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF59E0B)) // Amber 500
                                    .clickable { onSelectWaterValue(3) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("₹3", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 16.sp)
                            }

                            // ₹5 Bubble
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF06B6D4)) // Cyan 500
                                    .clickable { onSelectWaterValue(5) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("₹5", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }

        // TILE 8: Packaged Coca-Cola / Thums Up Blue Drink
        item {
            ProductTileSingle(
                title = Localization.get("CHIPS", language),
                nameSub = "Lays/Kurkure",
                price = 10,
                color = SnackOrange,
                onClick = { onAddProduct("CHIPS_10", "Chips Packet", 10, "YELLOW") }
            )
        }

        // TILE 9: Generic Food Namkeen ₹5 (Solid Yellow)
        item {
            GenericTileLargeTextPrice(
                label = Localization.get("NAMKEEN", language),
                priceVal = 5,
                color = SnackYellow,
                onClick = { onAddProduct("NAMKEEN_5", "Namkeen ₹5", 5, "YELLOW") }
            )
        }

        // TILE 10: Generic Food Namkeen ₹10 (Solid Yellow)
        item {
            GenericTileLargeTextPrice(
                label = Localization.get("NAMKEEN", language),
                priceVal = 10,
                color = SnackYellow,
                onClick = { onAddProduct("NAMKEEN_10", "Namkeen ₹10", 10, "YELLOW") }
            )
        }

        // TILE 11: Dry Fruits Snack Generic ₹20
        item {
            GenericTileLargeTextPrice(
                label = Localization.get("DRY_FRUITS", language),
                priceVal = 20,
                color = SnackOrange,
                onClick = { onAddProduct("DRY_FRUITS_20", "Dry Fruits ₹20", 20, "YELLOW") }
            )
        }

        // TILE 12: Soda / Cool Drink Can (Blue)
        item {
            ProductTileSingle(
                title = "Thums Up / Sprite",
                price = 40,
                color = DrinkBlue,
                onClick = { onAddProduct("DRINK_40", "Sprite/Thums Up", 40, "BLUE") }
            )
        }
    }
}

// Single Rectangular Tile Component (Standard)
@Composable
fun ProductTileSingle(
    title: String,
    nameSub: String? = null,
    price: Int,
    color: Color,
    onClick: () -> Unit
) {
    val isSnackColor = color == SnackYellow || color == SnackOrange
    val fontColor = if (isSnackColor) Color.Black else Color.White
    val subTextColor = if (isSnackColor) Color.Black.copy(alpha = 0.65f) else Color.LightGray

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = fontColor,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            nameSub?.let {
                Text(
                    text = it,
                    color = subTextColor,
                    fontSize = 9.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
            Text(
                text = "₹$price",
                color = fontColor,
                fontSize = 19.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

// Split Horizontal 70/30 or 50/50 Dual Action Item
@Composable
fun SplitTileCompact(
    leftLabel: String,
    leftPrice: Int,
    rightLabel: String,
    rightPrice: Int,
    colorMain: Color,
    colorAccent: Color,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF27272A)) // Zinc-800
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left Action side (Holds Loose or Standard description. Weight 65%)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(0.65f)
                    .background(colorMain)
                    .clickable(onClick = onLeftClick)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = leftLabel,
                        color = Color.White,
                        fontSize = 11.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    if (leftPrice > 0) {
                        Text(
                            text = "₹$leftPrice",
                            color = Color(0xFFFBBF24), // Amber 400
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            // High contrast Divider Line
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(Color.Black.copy(alpha = 0.25f))
            )

            // Right Action side (Holds Pack description. Weight 35%)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(0.35f)
                    .background(colorAccent)
                    .clickable(onClick = onRightClick)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = rightLabel,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "₹$rightPrice",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

// Generic price point focusing entirely on the PRICE (Muscle Memory)
@Composable
fun GenericTileLargeTextPrice(
    label: String,
    priceVal: Int,
    color: Color,
    onClick: () -> Unit
) {
    val isSnackColor = color == SnackYellow || color == SnackOrange
    val fontColor = if (isSnackColor) Color.Black else Color.White
    val labelColor = if (isSnackColor) Color.Black.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.85f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "₹$priceVal",
                    color = fontColor,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = label,
                    color = labelColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}


// ==========================================
// ZONE 3: KEYPAD & ACTION BOTTOM ROW
// ==========================================
@Composable
fun KeypadAndPaymentArea(
    language: PosLanguage,
    keypadBuffer: String,
    cartTotal: Int,
    cartItemCount: Int,
    onKeypadPress: (Char) -> Unit,
    onBackspace: () -> Unit,
    onAddGeneric: () -> Unit,
    onPaymentClick: (String) -> Unit,
    onOpenLogs: () -> Unit,
    onClearCart: () -> Unit,
    onOpenCart: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Upper section: Quick permanent keypad + buffer view + Add to Bill Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.70f),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // KEYPAD (3 Columns x 4 Rows) with high-density spacing (2dp) and full black tiles
            Column(
                modifier = Modifier
                    .weight(0.72f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                listOf(
                    listOf('1', '2', '3'),
                    listOf('4', '5', '6'),
                    listOf('7', '8', '9'),
                    listOf('0', '0', '⌫') // Tapping ⌫ backspaces
                ).forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        row.forEach { char ->
                            val isBack = char == '⌫'
                            Box(
                                modifier = Modifier
                                    .weight(if (char == '0' && row.count { it == '0' } == 2) 1f else 1f) // keeps Grid Cells equal
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isBack) Color(0xFF18181B) else Color.Black)
                                    .border(1.dp, Color(0xFF27272A), RoundedCornerShape(4.dp)) // Zinc-800 grid lines
                                    .clickable {
                                        if (isBack) onBackspace() else onKeypadPress(char)
                                    }
                                    .testTag("keypad_$char"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = char.toString(),
                                    color = if (isBack) Color(0xFFEF4444) else Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // ADD TO BILL PANEL: Buffer display + high-voltage ADD operation button
            Column(
                modifier = Modifier
                    .weight(0.28f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Keypad input display
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black)
                        .border(1.dp, Color(0xFF27272A), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = if (keypadBuffer.isEmpty()) "₹ 0" else "₹ $keypadBuffer",
                        color = Color(0xFFFBBF24), // Amber 400
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.testTag("keypad_display_total")
                    )
                }

                // Add to Bill Tactile Trigger: White-zinc high contrast selector button
                Button(
                    onClick = onAddGeneric,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 2.dp)
                        .testTag("add_to_bill_button"),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF4F4F5), // Zinc-100 fallback to White-zinc representation
                        contentColor = Color.Black
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    val localizedAdd = Localization.get("ADD_TO_BILL", language)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (language == PosLanguage.EN) {
                            Text(
                                text = "ADD TO",
                                color = Color(0xFF71717A), // Zinc-500
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp,
                                lineHeight = 11.sp
                            )
                            Text(
                                text = "BILL",
                                color = Color.Black,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Black,
                                lineHeight = 21.sp
                            )
                        } else {
                            Text(
                                text = localizedAdd.uppercase(),
                                color = Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Center,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }

                // Quick Clear Cart Action Button (when cart has items)
                if (cartItemCount > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF7F1D1D).copy(alpha = 0.5f))
                            .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .clickable(onClick = onClearCart)
                            .testTag("clear_cart_keypad_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.DeleteSweep,
                                contentDescription = "Clear",
                                tint = Color(0xFFFCA5A5),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = Localization.get("CLEAR_CART", language),
                                color = Color(0xFFFCA5A5),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Stats logs slide trigger
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF18181B))
                        .border(1.dp, Color(0xFF27272A), RoundedCornerShape(4.dp))
                        .clickable(onClick = onOpenLogs),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.History, contentDescription = "History", tint = Color(0xFFA1A1AA), modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = Localization.get("RECENT_SALES", language),
                            color = Color(0xFFA1A1AA),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Bottom Edge: SPLIT 50/50 COLLECT ₹ [TOTAL] BUTTON (Highly tactile 74dp height, no rounded clips)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(74.dp)
                .background(Color.Black),
            horizontalArrangement = Arrangement.spacedBy(0.dp) // Flat tight border split
        ) {
            // CASH OPTION (Vibrant Green-600, Weight 0.5)
            Box(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight()
                    .background(Color(0xFF16A34A)) // Green 600
                    .clickable { onPaymentClick("CASH") }
                    .testTag("collect_cash_button"),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "COLLECT",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    Row(
                        modifier = Modifier.padding(top = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = Localization.get("CASH", language).uppercase(),
                            color = Color.White,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "₹$cartTotal",
                            color = Color(0xFFFEF08A), // Yellow 200
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            // High contrast vertical black divider
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(Color.Black)
            )

            // UPI OPTION (Vibrant Blue-600, Weight 0.5)
            Box(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight()
                    .background(Color(0xFF2563EB)) // Blue 600
                    .clickable { onPaymentClick("UPI") }
                    .testTag("collect_upi_button"),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "COLLECT",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    Row(
                        modifier = Modifier.padding(top = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = Localization.get("UPI", language).uppercase(),
                            color = Color.White,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "₹$cartTotal",
                            color = Color(0xFF67E8F9), // Cyan 300
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}


// ==========================================
// CAMERA VIEWER BARCODE SCANNER OVERLAY (Scenario C)
// ==========================================
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun BarcodeScannerOverlay(
    language: PosLanguage,
    onDismiss: () -> Unit,
    onMockScan: (String) -> Unit
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    // Automatically trigger permission check
    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .clickable(enabled = true, onClick = onDismiss) // Click outside to close
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Scanner Title Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BARCODE SCANNER",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Close", tint = Color.Red)
                }
            }

            // Viewfinder Grid Block
            Box(
                modifier = Modifier
                    .size(width = 280.dp, height = 240.dp)
                    .border(2.dp, Color.Green, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                if (cameraPermissionState.status.isGranted) {
                    // Start actual camera viewfinder bound to Compose lifecycle!
                    CameraPreviewView()
                } else {
                    Icon(
                        imageVector = Icons.Filled.VideocamOff,
                        contentDescription = "No Camera Access",
                        tint = Color.Gray,
                        modifier = Modifier.size(48.dp)
                    )
                }

                // Pulsing red laser scanning focus guide line
                val infiniteTransition = rememberInfiniteTransition()
                val targetOffset by infiniteTransition.animateFloat(
                    initialValue = -100f,
                    targetValue = 100f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1400, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .offset(y = targetOffset.dp)
                        .background(Color.Red)
                )
            }

            // Dynamic scan alignment guidance label
            Text(
                text = Localization.get("ALIGN_BARCODE", language),
                color = Color.Green,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            // SIMULATED MOCK SCAN SELECTION PORTALS (Essential for Emulators / Scenario C testings)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "⚡ " + Localization.get("MOCK_SCAN", language) + " (Click to trigger)",
                    color = Color.Yellow,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item {
                        MockScanButton(
                            label = "Rajnigandha Pack (₹50)",
                            barcode = "8901234500010",
                            onClick = onMockScan
                        )
                    }
                    item {
                        MockScanButton(
                            label = "Classic Gold Pack (₹360)",
                            barcode = "8901234500027",
                            onClick = onMockScan
                        )
                    }
                    item {
                        MockScanButton(
                            label = "Sprite Cool Drink (₹40)",
                            barcode = "8901234500034",
                            onClick = onMockScan
                        )
                    }
                    item {
                        MockScanButton(
                            label = "Chips Lays (₹10)",
                            barcode = "8901234500041",
                            onClick = onMockScan
                        )
                    }
                }
            }
        }
    }
}

// CameraX Viewfinder integration helper Composable
@SuppressLint("UnrememberedMutableState")
@Composable
fun CameraPreviewView() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { previewView ->
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview
                    )
                } catch (exc: Exception) {
                    exc.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(context))
        }
    )
}

// Interactive simulated scan row item
@Composable
fun MockScanButton(
    label: String,
    barcode: String,
    onClick: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(CardGray)
            .border(1.dp, Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .clickable { onClick(barcode) }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("BC: $barcode", color = Color.LightGray, fontSize = 9.sp)
        }
    }
}


// ==========================================
// STATUS NOTIFIERS OVERLAYS (Sync and Collect)
// ==========================================
@Composable
fun SyncOverlayMessage(status: String, language: PosLanguage) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .width(260.dp)
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardGray),
            border = BorderStroke(1.dp, Color.Green)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (status) {
                    "SYNCING" -> {
                        CircularProgressIndicator(color = Color.Green, modifier = Modifier.size(36.dp))
                        Text(
                            text = Localization.get("SYNC_CLOUD", language),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    "SUCCESS" -> {
                        Icon(Icons.Filled.CheckCircle, contentDescription = "Done", tint = Color.Green, modifier = Modifier.size(48.dp))
                        Text(
                            text = Localization.get("SYNC_SUCCESS", language),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    "CASH_OK" -> {
                        Icon(Icons.Filled.Payments, contentDescription = "Cash Done", tint = Color.Green, modifier = Modifier.size(54.dp))
                        Text(
                            text = Localization.get("SUCCESS_COLLECT", language),
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp
                        )
                        Text("CASH RECEIVED", color = Color.Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    "UPI_OK" -> {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = "UPI Done", tint = Color.Cyan, modifier = Modifier.size(54.dp))
                        Text(
                            text = Localization.get("SUCCESS_COLLECT", language),
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp
                        )
                        Text("UPI RECEIVED", color = Color.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// Scanned Float Bubble toast
@Composable
fun FloatingScanIndicator(itemLabel: String, language: PosLanguage) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 90.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Green),
            shape = RoundedCornerShape(30.dp),
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.QrCode, contentDescription = "Scanned", tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ADD: $itemLabel",
                    color = Color.Black,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp
                )
            }
        }
    }
}


// ==========================================
// TRANSACTIONS LOGS / HISTORY BOTTOM-DRAWER
// ==========================================
@Composable
fun TransactionsHistoryDrawer(
    language: PosLanguage,
    recentSales: List<SaleTransaction>,
    cashTotal: Int,
    upiTotal: Int,
    onClose: () -> Unit,
    onResetLogs: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .clickable { onClose() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .align(Alignment.BottomCenter)
                .background(DarkGrayBg, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .border(1.dp, Color.DarkGray, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .clickable(enabled = false) {} // block click propagation
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header of Drawer with close & empty triggers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = Localization.get("RECENT_SALES", language).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = onResetLogs,
                            modifier = Modifier.background(Color.Red.copy(alpha = 0.15f), CircleShape)
                        ) {
                            Icon(Icons.Filled.DeleteForever, contentDescription = "Clear Logs", tint = Color.Red)
                        }

                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.background(Color.LightGray.copy(alpha = 0.12f), CircleShape)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Stats summaries panel
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = Localization.get("CASH_SALES", language),
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "₹$cashTotal",
                            color = Color.Green,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = Localization.get("UPI_SALES", language),
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "₹$upiTotal",
                            color = Color.Cyan,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // List of items
                if (recentSales.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = Localization.get("NO_SALES_TODAY", language),
                            color = Color.Gray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(recentSales) { sale ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CardGray)
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(0.7f)) {
                                        Text(
                                            text = sale.itemsSummary,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = timeFormat.format(Date(sale.timestamp)) + " | " + sale.paymentType,
                                            color = Color.Gray,
                                            fontSize = 9.sp
                                        )
                                    }
                                    Text(
                                        text = "₹${sale.amount}",
                                        color = if (sale.paymentType == "CASH") Color.Green else Color.Cyan,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.weight(0.3f),
                                        textAlign = TextAlign.End
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

// ==========================================
// ACTIVE CART BOTTOM DRAWER COMPONENT
// ==========================================
@Composable
fun ActiveCartDrawer(
    language: PosLanguage,
    cartItems: List<CartItem>,
    cartTotal: Int,
    onIncrement: (String) -> Unit,
    onDecrement: (String) -> Unit,
    onRemoveItem: (String) -> Unit,
    onClearCart: () -> Unit,
    onCheckoutCash: () -> Unit,
    onCheckoutUpi: () -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .clickable { onClose() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.78f)
                .align(Alignment.BottomCenter)
                .background(DarkGrayBg, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .border(1.dp, Color(0xFF3F3F46), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .clickable(enabled = false) {}
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Drawer Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.ShoppingCart,
                            contentDescription = "Cart",
                            tint = Color(0xFF22C55E),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Localization.get("CART", language).uppercase() + " (${cartItems.sumOf { it.quantity }})",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (cartItems.isNotEmpty()) {
                            TextButton(
                                onClick = onClearCart,
                                modifier = Modifier.testTag("clear_cart_drawer_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteSweep,
                                    contentDescription = "Clear Cart",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = Localization.get("CLEAR_CART", language),
                                    color = Color(0xFFEF4444),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.background(Color.White.copy(alpha = 0.1f), CircleShape)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                }

                HorizontalDivider(
                    color = Color(0xFF27272A),
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 10.dp)
                )

                // Drawer Body
                if (cartItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.RemoveShoppingCart,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = Localization.get("EMPTY_CART", language),
                                color = Color.Gray,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(cartItems, key = { it.id }) { item ->
                            val itemDisplayName = when (item.nameKey) {
                                "LOOSE_WATER" -> Localization.get("LOOSE_WATER", language)
                                "GENERIC_ITEM" -> Localization.get("ITEMS", language) + " ₹${item.price}"
                                else -> Localization.get(item.nameKey, language).ifEmpty { item.displayNameDefault }
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = CardGray),
                                border = BorderStroke(1.dp, Color(0xFF3F3F46))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(0.4f)) {
                                        Text(
                                            text = itemDisplayName,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "₹${item.price} each",
                                            color = Color.LightGray,
                                            fontSize = 10.sp
                                        )
                                    }

                                    // Stepper quantity controls
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color.Black)
                                            .border(1.dp, Color(0xFF52525B), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clickable { onDecrement(item.id) }
                                                .testTag("decrement_${item.id}"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("-", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Text(
                                            text = "${item.quantity}",
                                            color = Color(0xFFFBBF24),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier
                                                .padding(horizontal = 8.dp)
                                                .testTag("quantity_${item.id}")
                                        )

                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clickable { onIncrement(item.id) }
                                                .testTag("increment_${item.id}"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("+", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    // Subtotal
                                    Text(
                                        text = "₹${item.subtotal}",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier
                                            .padding(start = 6.dp)
                                            .testTag("subtotal_${item.id}")
                                    )

                                    // Remove button
                                    IconButton(
                                        onClick = { onRemoveItem(item.id) },
                                        modifier = Modifier
                                            .size(28.dp)
                                            .testTag("remove_${item.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Remove Item",
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Total Calculation Panel
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.Black),
                        border = BorderStroke(1.dp, Color(0xFF3F3F46)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = Localization.get("TOTAL", language).uppercase(),
                                color = Color(0xFFA1A1AA),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "₹$cartTotal",
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.testTag("cart_drawer_total_display")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Checkout Buttons: Complete Sale & Clear Cart
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onCheckoutCash,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("cart_drawer_pay_cash"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CashGreen,
                                contentColor = Color.White
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(
                                    text = Localization.get("PAY_CASH", language),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Button(
                            onClick = onCheckoutUpi,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("cart_drawer_pay_upi"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = UpiBlue,
                                contentColor = Color.White
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(
                                    text = Localization.get("PAY_UPI", language),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
