package com.example.silkroad
import com.example.silkroad.domain.ShopRules
import com.example.silkroad.domain.model.CartItem
import com.example.silkroad.domain.model.Product
import org.junit.Assert.assertEquals
import org.junit.Test
/** Regression for the quantity overflow documented in practice 11. */
class WhiteBoxKnownDefectTest {
    @Test fun hugePositiveDeltaMustNotDeleteAnExistingItem() {
        val product=Product(1,"Producto",1.0,"","beauty","")
        val result=ShopRules.changeQuantity(listOf(CartItem(product,1)),product,Int.MAX_VALUE)
        assertEquals("Positive increase should saturate at 99",99,result.firstOrNull()?.quantity?:0)
    }
}
