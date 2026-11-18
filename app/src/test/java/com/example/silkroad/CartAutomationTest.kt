package com.example.silkroad

import com.example.silkroad.domain.ShopRules
import com.example.silkroad.domain.model.CartItem
import com.example.silkroad.domain.model.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal

class CartAutomationTest {
    private val product = Product(7, "Teclado", 2.675, "", "electronics", "")

    @Test fun addsAnItemAndPreservesOtherProducts() {
        val other = CartItem(product.copy(id = 8), 1)
        val cart = ShopRules.changeQuantity(listOf(other), product, 2)

        assertEquals(listOf(other, CartItem(product, 2)), cart)
    }

    @Test fun emptyCartHasZeroTotal() {
        assertEquals(BigDecimal("0.00"), ShopRules.total(emptyList()))
    }

    @Test fun decrementBelowZeroRemovesTheItem() {
        val cart = listOf(CartItem(product, 2))

        assertEquals(emptyList<CartItem>(), ShopRules.changeQuantity(cart, product, -3))
    }

    @Test fun roundsTotalToTwoDecimals() {
        val cart = listOf(CartItem(product, 3))

        assertEquals(BigDecimal("8.03"), ShopRules.total(cart))
    }

    @Test fun rejectsNonFinitePricesWhenCalculatingTotal() {
        val cart = listOf(CartItem(product.copy(price = Double.NaN), 1))

        assertThrows(NumberFormatException::class.java) { ShopRules.total(cart) }
    }
}
