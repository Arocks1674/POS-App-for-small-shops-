package com.example

import com.example.data.database.SaleTransaction
import com.example.data.database.TransactionDao
import com.example.data.repository.TransactionRepository
import com.example.ui.CartItem
import com.example.ui.PosViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeTransactionDao : TransactionDao {
    val transactions = mutableListOf<SaleTransaction>()
    private val _flow = MutableStateFlow<List<SaleTransaction>>(emptyList())
    private val _cashFlow = MutableStateFlow<Int?>(0)
    private val _upiFlow = MutableStateFlow<Int?>(0)

    override fun getAllTransactions(): Flow<List<SaleTransaction>> = _flow
    override fun getCashTotal(): Flow<Int?> = _cashFlow
    override fun getUpiTotal(): Flow<Int?> = _upiFlow

    override suspend fun insertTransaction(transaction: SaleTransaction) {
        transactions.add(transaction)
        _flow.value = transactions.toList()
        if (transaction.paymentType == "CASH") {
            _cashFlow.value = (_cashFlow.value ?: 0) + transaction.amount
        } else {
            _upiFlow.value = (_upiFlow.value ?: 0) + transaction.amount
        }
    }

    override suspend fun clearAllTransactions() {
        transactions.clear()
        _flow.value = emptyList()
        _cashFlow.value = 0
        _upiFlow.value = 0
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ExampleUnitTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeDao: FakeTransactionDao
    private lateinit var repository: TransactionRepository
    private lateinit var viewModel: PosViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeDao = FakeTransactionDao()
        repository = TransactionRepository(fakeDao)
        viewModel = PosViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testCartInitiallyEmpty() {
        assertTrue(viewModel.isCartEmpty.value)
        assertEquals(0, viewModel.cartTotal.value)
        assertEquals(0, viewModel.cartItemCount.value)
        assertEquals(0, viewModel.cartItems.value.size)
    }

    @Test
    fun testAddMultipleProductsAndCalculateTotal() {
        // Add 1 Gold Flake Loose (₹15)
        viewModel.addProduct("GOLD_FLAKE", "Gold Flake (Loose)", 15, "RED")
        // Add 1 Classic Pack (₹360)
        viewModel.addProduct("CLASSIC", "Classic Pack", 360, "RED")
        // Add 2 Thums Up (₹40 each)
        viewModel.addProduct("THUMS_UP", "Thums Up", 40, "BLUE", quantity = 2)

        assertFalse(viewModel.isCartEmpty.value)
        assertEquals(3, viewModel.cartItems.value.size) // 3 distinct products
        assertEquals(4, viewModel.cartItemCount.value) // 1 + 1 + 2 = 4 total quantity

        // 15 + 360 + (40 * 2) = 455
        assertEquals(455, viewModel.calculateTotalPrice())
        assertEquals(455, viewModel.cartTotal.value)
    }

    @Test
    fun testAddSameProductIncreasesQuantity() {
        viewModel.addProduct("RAJNIGANDHA", "Rajnigandha", 50, "GREEN")
        viewModel.addProduct("RAJNIGANDHA", "Rajnigandha", 50, "GREEN")

        assertEquals(1, viewModel.cartItems.value.size)
        assertEquals(2, viewModel.cartItems.value[0].quantity)
        assertEquals(100, viewModel.cartTotal.value)
    }

    @Test
    fun testIncrementAndDecrementQuantity() {
        viewModel.addProduct("WATER", "Loose Water", 5, "BLUE", quantity = 2)
        val itemId = viewModel.cartItems.value[0].id

        viewModel.incrementQuantity(itemId)
        assertEquals(3, viewModel.cartItems.value[0].quantity)
        assertEquals(15, viewModel.cartTotal.value)

        viewModel.decrementQuantity(itemId)
        assertEquals(2, viewModel.cartItems.value[0].quantity)
        assertEquals(10, viewModel.cartTotal.value)

        // Decrement down to 0 removes item
        viewModel.decrementQuantity(itemId)
        viewModel.decrementQuantity(itemId)
        assertTrue(viewModel.isCartEmpty.value)
        assertEquals(0, viewModel.cartTotal.value)
    }

    @Test
    fun testRemoveCartItem() {
        viewModel.addProduct("CHIPS", "Chips", 10, "YELLOW", quantity = 3)
        val itemId = viewModel.cartItems.value[0].id

        viewModel.removeCartItem(itemId)
        assertTrue(viewModel.isCartEmpty.value)
        assertEquals(0, viewModel.cartTotal.value)
    }

    @Test
    fun testClearCart() {
        viewModel.addProduct("ITEM1", "Item 1", 20)
        viewModel.addProduct("ITEM2", "Item 2", 30)
        assertEquals(50, viewModel.cartTotal.value)

        viewModel.clearCart()
        assertTrue(viewModel.isCartEmpty.value)
        assertEquals(0, viewModel.cartTotal.value)
        assertEquals(0, viewModel.cartItems.value.size)
    }

    @Test
    fun testCompleteSaleClearsCartAndPersists() = runTest {
        viewModel.addProduct("PAAN", "Meetha Paan", 35, "GREEN", quantity = 2)
        assertEquals(70, viewModel.cartTotal.value)

        viewModel.completeSale("CASH")

        // Cart should be cleared after sale completion
        assertTrue(viewModel.isCartEmpty.value)
        assertEquals(0, viewModel.cartTotal.value)
        assertEquals(0, viewModel.cartItems.value.size)

        // Transaction should be saved
        assertEquals(1, fakeDao.transactions.size)
        assertEquals(70, fakeDao.transactions[0].amount)
        assertEquals("CASH", fakeDao.transactions[0].paymentType)
    }
}
