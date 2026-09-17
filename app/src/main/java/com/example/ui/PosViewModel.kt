package com.example.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.database.SaleTransaction
import com.example.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CartItem(
    val id: String,
    val nameKey: String, // String resource / translation key
    val displayNameDefault: String,
    val price: Int,
    val quantity: Int = 1,
    val isGeneric: Boolean = false,
    val colorCode: String = "GREEN" // "RED", "GREEN", "BLUE", "YELLOW", "GRAY"
) {
    val subtotal: Int get() = price * quantity
}

typealias BasketItem = CartItem

enum class PosLanguage {
    EN, HI, TA, TE
}

class PosViewModel(private val repository: TransactionRepository) : ViewModel() {

    // Language state
    private val _selectedLanguage = MutableStateFlow(PosLanguage.EN)
    val selectedLanguage: StateFlow<PosLanguage> = _selectedLanguage

    // Online Sync state (Green = Online, Gray = Offline)
    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline

    // Scanner state
    private val _isScannerActive = MutableStateFlow(false)
    val isScannerActive: StateFlow<Boolean> = _isScannerActive

    // Temp Keypad Buffer (Scenario B)
    private val _keypadBuffer = MutableStateFlow("")
    val keypadBuffer: StateFlow<String> = _keypadBuffer

    // Current Running Cart System
    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()
    val basketItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow() // Backwards compatibility

    // Running total price of current cart
    private val _cartTotal = MutableStateFlow(0)
    val cartTotal: StateFlow<Int> = _cartTotal.asStateFlow()

    // Total quantity of items in cart
    private val _cartItemCount = MutableStateFlow(0)
    val cartItemCount: StateFlow<Int> = _cartItemCount.asStateFlow()

    // Empty state indicator
    private val _isCartEmpty = MutableStateFlow(true)
    val isCartEmpty: StateFlow<Boolean> = _isCartEmpty.asStateFlow()

    // Transaction History & Statistics from DB
    val recentTransactions: StateFlow<List<SaleTransaction>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cashTotal: StateFlow<Int> = repository.cashTotal
        .map { it ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val upiTotal: StateFlow<Int> = repository.upiTotal
        .map { it ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val overallTotal: StateFlow<Int> = combine(cashTotal, upiTotal) { cash, upi ->
        cash + upi
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Sync notification helper state
    val syncMessage = MutableStateFlow<String?>(null)

    // Local Scan helper states
    val scanResultMessage = MutableStateFlow<String?>(null)

    // Tone builder for tactile feedback
    private var toneGenerator: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
    } catch (_: Exception) {
        null
    }

    fun playBeep() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        } catch (_: Exception) {}
    }

    fun playDoubleBeep() {
        viewModelScope.launch {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 150)
            } catch (_: Exception) {}
        }
    }

    // Synchronize internal cart state and totals
    private fun updateCartState(newList: List<CartItem>) {
        _cartItems.value = newList
        val total = newList.sumOf { it.price * it.quantity }
        _cartTotal.value = total
        _cartItemCount.value = newList.sumOf { it.quantity }
        _isCartEmpty.value = newList.isEmpty()
    }

    /**
     * Calculate and return the total price of all items currently in the cart.
     */
    fun calculateTotalPrice(): Int {
        val total = _cartItems.value.sumOf { it.price * it.quantity }
        _cartTotal.value = total
        return total
    }

    // Toggle Offline/Online Sync State
    fun toggleOnlineSync() {
        val current = _isOnline.value
        _isOnline.value = !current
        playBeep()
        if (_isOnline.value) {
            // Emulate Cloud Sync Animation & Messaging
            viewModelScope.launch {
                syncMessage.value = "SYNCING"
                kotlinx.coroutines.delay(1200)
                syncMessage.value = "SUCCESS"
                playDoubleBeep()
                kotlinx.coroutines.delay(1800)
                syncMessage.value = null
            }
        }
    }

    // Language Toggle logic
    fun cycleLanguage() {
        val next = when (_selectedLanguage.value) {
            PosLanguage.EN -> PosLanguage.HI
            PosLanguage.HI -> PosLanguage.TA
            PosLanguage.TA -> PosLanguage.TE
            PosLanguage.TE -> PosLanguage.EN
        }
        _selectedLanguage.value = next
        playBeep()
    }

    fun selectLanguage(lang: PosLanguage) {
        _selectedLanguage.value = lang
        playBeep()
    }

    // ==========================================
    // CART OPERATIONS
    // ==========================================

    /**
     * Add a product to the cart with the given properties.
     * If an identical item exists (same nameKey and price), its quantity is increased.
     */
    fun addProduct(
        nameKey: String,
        displayName: String,
        price: Int,
        colorCode: String = "GREEN",
        quantity: Int = 1
    ) {
        val currentList = _cartItems.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.nameKey == nameKey && it.price == price }

        if (existingIndex != -1) {
            val item = currentList[existingIndex]
            currentList[existingIndex] = item.copy(quantity = item.quantity + quantity)
        } else {
            val itemId = "${System.currentTimeMillis()}_${nameKey}_${currentList.size}"
            currentList.add(
                CartItem(
                    id = itemId,
                    nameKey = nameKey,
                    displayNameDefault = displayName,
                    price = price,
                    quantity = quantity,
                    colorCode = colorCode
                )
            )
        }
        updateCartState(currentList)
        playBeep()
    }

    fun addItemToCart(nameKey: String, displayName: String, price: Int, colorCode: String = "GREEN") {
        addProduct(nameKey, displayName, price, colorCode)
    }

    fun addItemToBasket(nameKey: String, displayName: String, price: Int, colorCode: String) {
        addProduct(nameKey, displayName, price, colorCode)
    }

    /**
     * Add a pre-constructed CartItem to the cart.
     */
    fun addToCart(item: CartItem) {
        val currentList = _cartItems.value.toMutableList()
        val existingIndex = currentList.indexOfFirst {
            it.id == item.id || (it.nameKey == item.nameKey && it.price == item.price)
        }

        if (existingIndex != -1) {
            val existing = currentList[existingIndex]
            currentList[existingIndex] = existing.copy(quantity = existing.quantity + item.quantity)
        } else {
            currentList.add(item)
        }
        updateCartState(currentList)
        playBeep()
    }

    /**
     * Increment quantity of an item by itemId.
     */
    fun incrementQuantity(itemId: String) {
        val currentList = _cartItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == itemId }
        if (index != -1) {
            val item = currentList[index]
            currentList[index] = item.copy(quantity = item.quantity + 1)
            updateCartState(currentList)
            playBeep()
        }
    }

    /**
     * Decrement quantity of an item by itemId. If quantity reaches 0, removes the item.
     */
    fun decrementQuantity(itemId: String) {
        val currentList = _cartItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == itemId }
        if (index != -1) {
            val item = currentList[index]
            if (item.quantity > 1) {
                currentList[index] = item.copy(quantity = item.quantity - 1)
            } else {
                currentList.removeAt(index)
            }
            updateCartState(currentList)
            playBeep()
        }
    }

    /**
     * Remove an item completely from the cart regardless of quantity.
     */
    fun removeCartItem(itemId: String) {
        val currentList = _cartItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == itemId }
        if (index != -1) {
            currentList.removeAt(index)
            updateCartState(currentList)
            playBeep()
        }
    }

    fun removeBasketItem(item: BasketItem) {
        decrementQuantity(item.id)
    }

    fun addLooseWaterQuantity(price: Int) {
        addProduct("LOOSE_WATER", "Loose Water ₹$price", price, "BLUE")
    }

    /**
     * Clear all items from the active cart.
     */
    fun clearCart() {
        updateCartState(emptyList())
        playBeep()
    }

    fun clearBasket() {
        clearCart()
    }

    // Permanent keypad handlers (Zone 3)
    fun appendKeypad(char: Char) {
        if (_keypadBuffer.value == "0" || _keypadBuffer.value == "") {
            _keypadBuffer.value = char.toString()
        } else {
            if (_keypadBuffer.value.length < 5) { // Limit to ₹99,999
                _keypadBuffer.value += char
            }
        }
        playBeep()
    }

    fun backspaceKeypad() {
        val current = _keypadBuffer.value
        if (current.isNotEmpty()) {
            _keypadBuffer.value = current.dropLast(1)
        }
        playBeep()
    }

    fun clearKeypad() {
        _keypadBuffer.value = ""
        playBeep()
    }

    // Scenario B: Quick Lighter / Arbitrary input
    fun addKeypadAmountToBill() {
        val inputStr = _keypadBuffer.value
        if (inputStr.isNotEmpty()) {
            val price = inputStr.toIntOrNull() ?: 0
            if (price > 0) {
                // Add as custom generic item
                val currentList = _cartItems.value.toMutableList()
                val itemId = "KEYPAD_" + System.currentTimeMillis()
                currentList.add(
                    CartItem(
                        id = itemId,
                        nameKey = "GENERIC_ITEM",
                        displayNameDefault = "Item ₹$price",
                        price = price,
                        quantity = 1,
                        isGeneric = true,
                        colorCode = "YELLOW"
                    )
                )
                updateCartState(currentList)
                _keypadBuffer.value = ""
                playDoubleBeep()
            }
        }
    }

    // Toggle scanner layer (Scenario C & physical overlay)
    fun toggleScanner() {
        _isScannerActive.value = !_isScannerActive.value
        playBeep()
    }

    // Direct Barcode Scan Callback (Scenario C)
    fun processBarcodeScanned(barcode: String) {
        val (nameKey, nameDefault, price, color) = when (barcode) {
            "8901234500010" -> quadruple("RAJNIGANDHA", "Rajnigandha Pack", 50, "GREEN")
            "8901234500027" -> quadruple("CLASSIC", "Classic Pack", 360, "RED")
            "8901234500034" -> quadruple("DRINK_40", "Sprite/Thums Up", 40, "BLUE")
            "8901234500041" -> quadruple("CHIPS", "Chips Lays", 10, "YELLOW")
            else -> quadruple("SCANNED_ITEM", "Barcoded Item", 15, "GRAY")
        }

        addProduct(nameKey, nameDefault, price, color)

        // Success Flash
        viewModelScope.launch {
            scanResultMessage.value = nameDefault
            playDoubleBeep()
            kotlinx.coroutines.delay(2000)
            scanResultMessage.value = null
        }
        _isScannerActive.value = false
    }

    private fun quadruple(a: String, b: String, c: Int, d: String) = Quadruple(a, b, c, d)

    data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    /**
     * Complete a sale with the given payment method (CASH or UPI).
     * Saves the transaction to database, resets the cart, and gives audio/visual confirmation.
     */
    fun completeSale(paymentType: String, onComplete: (() -> Unit)? = null) {
        val list = _cartItems.value
        val total = calculateTotalPrice()
        if (total <= 0) return

        // Build brief summary string for DB persistence
        val summaryItems = list.joinToString(", ") { item ->
            val name = when (item.nameKey) {
                "LOOSE_WATER" -> "Loose Water ₹${item.price}"
                "GENERIC_ITEM" -> "Direct item ₹${item.price}"
                else -> item.displayNameDefault
            }
            "$name x${item.quantity}"
        }

        viewModelScope.launch {
            val transaction = SaleTransaction(
                amount = total,
                paymentType = paymentType,
                itemsSummary = summaryItems
            )
            repository.insertTransaction(transaction)

            // Success sound & Clear Cart after sale is completed
            playDoubleBeep()
            updateCartState(emptyList())
            _keypadBuffer.value = ""

            // Notify success state
            syncMessage.value = if (paymentType == "CASH") "CASH_OK" else "UPI_OK"
            onComplete?.invoke()
            kotlinx.coroutines.delay(1800)
            syncMessage.value = null
        }
    }

    // Complete Transaction (Tapping CASH or UPI)
    fun collectPayment(paymentType: String) {
        completeSale(paymentType)
    }

    // Empty sales history logs
    fun resetSalesHistory() {
        viewModelScope.launch {
            repository.clearAllTransactions()
            playBeep()
        }
    }

    override fun onCleared() {
        super.onCleared()
        toneGenerator?.release()
    }
}

class PosViewModelFactory(private val repository: TransactionRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PosViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PosViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
