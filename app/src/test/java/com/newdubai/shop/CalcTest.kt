package com.newdubai.shop

import com.newdubai.shop.data.*
import org.junit.Assert.assertEquals
import org.junit.Test

class CalcTest {
    private val order = TailorOrder(
        id = 1, no = "ND-0001", customerId = 1, type = "m_sq", fabricSource = "shop",
        fabricId = 1, meters = 4.0, fabricPrice = 2000.0, tailorPrice = 800.0,
        orderDate = "2026-11-01", deliveryDate = "2026-11-08", status = "new"
    )

    @Test fun balanceAfterPartialPayments() {
        val pays = listOf(
            Payment(orderId = 1, customerId = 1, amount = 1000.0, date = "2026-11-01", kind = "adv"),
            Payment(orderId = 1, customerId = 1, amount = 500.0, date = "2026-11-03", kind = "part")
        )
        assertEquals(2800.0, order.total, 0.001)
        assertEquals(1300.0, Calc.balance(order, pays), 0.001)
        assertEquals(1300.0, Calc.customerDebt(1, listOf(order), pays), 0.001)
    }

    @Test fun unitConversion() {
        assertEquals(10.0, Calc.cmToIn(25.4), 0.0001)
        assertEquals(25.4, Calc.inToCm(10.0), 0.0001)
    }

    @Test fun netProfit() {
        val s = Calc.stats("2026-11-01", "2026-11-30", listOf(order), emptyList(),
            emptyList(), listOf(Purchase(fabricId = 1, meters = 10.0, pricePerMeter = 150.0, date = "2026-11-02")),
            listOf(Expense(title = "Rent", amount = 300.0, date = "2026-11-05", category = "rent")))
        assertEquals(800.0 + 2000.0 - 1500.0 - 300.0, s.net, 0.001)
    }
}
