package com.example.silkroad

import com.example.silkroad.domain.ShopRules
import com.example.silkroad.domain.model.CartItem
import com.example.silkroad.domain.model.Product
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class ShopRulesTest {
    private val shirt = Product(1, "Camisa de algodón", 19.99, "Cómoda", "men's clothing", "")
    private val phone = Product(2, "Teléfono", 100.0, "", "electronics", "")
    @Test fun searchIgnoresCaseAndAccents() { assertEquals(listOf(shirt), ShopRules.filter(listOf(shirt, phone), " ALGODON ", null)) }
    @Test fun searchIncludesTranslatedCategories() { assertEquals(listOf(phone), ShopRules.filter(listOf(shirt, phone), "electronica", null)) }
    @Test fun categoryAndQueryAreCombined() { assertTrue(ShopRules.filter(listOf(shirt, phone), "camisa", "electronics").isEmpty()) }
    @Test fun emptySearchReturnsAll() { assertEquals(2, ShopRules.filter(listOf(shirt, phone), "", null).size) }
    @Test fun blankUsernameIsRejected() { assertNotNull(ShopRules.credentialsError("  ", "123456")) }
    @Test fun shortPasswordIsRejected() { assertNotNull(ShopRules.credentialsError("demo", "123")) }
    @Test fun validCredentialsPassValidation() { assertNull(ShopRules.credentialsError("demo", "SilkRoad2026")) }
    @Test fun whitespaceOnlyUserIsRejected() { assertNotNull(ShopRules.credentialsError("    ", "12345678")) }
    @Test fun sevenCharacterPasswordIsRejected() { assertNotNull(ShopRules.credentialsError("demo", "1234567")) }
    @Test fun eightCharacterPasswordPassesLengthValidation() { assertNull(ShopRules.credentialsError("demo", "12345678")) }
    @Test fun credentialsHaveUpperLengthLimits() {
        assertNotNull(ShopRules.credentialsError("a".repeat(41), "12345678"))
        assertNotNull(ShopRules.credentialsError("demo", "a".repeat(129)))
    }
    @Test fun addingTwiceCreatesOneLine() {
        val first = ShopRules.changeQuantity(emptyList(), shirt, 1)
        val second = ShopRules.changeQuantity(first, shirt, 1)
        assertEquals(1, second.size); assertEquals(2, second.single().quantity)
        assertEquals(1, first.single().quantity)
    }
    @Test fun removingLastUnitDeletesLine() { assertTrue(ShopRules.changeQuantity(listOf(CartItem(shirt)), shirt, -1).isEmpty()) }
    @Test fun quantityIsCappedAt99() { assertEquals(99, ShopRules.changeQuantity(listOf(CartItem(shirt, 99)), shirt, 1).single().quantity) }
    @Test fun negativeQuantityCannotCreateLine() { assertTrue(ShopRules.changeQuantity(emptyList(), shirt, -1).isEmpty()) }
    @Test fun totalUsesDecimalArithmetic() {
        val cheap = shirt.copy(price = 0.1)
        assertEquals(BigDecimal("0.30"), ShopRules.total(listOf(CartItem(cheap, 3))))
    }
    @Test fun emptyCartHasZeroTotal() { assertEquals(BigDecimal("0.00"), ShopRules.total(emptyList())) }
}
