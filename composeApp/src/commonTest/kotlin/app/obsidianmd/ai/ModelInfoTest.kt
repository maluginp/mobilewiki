package app.obsidianmd.ai

import kotlin.test.Test
import kotlin.test.assertEquals

class ModelInfoTest {
    @Test
    fun context_label_formats_thousands_as_k() {
        assertEquals("128K ctx", ModelInfo("m", contextLength = 128_000).contextLabel())
        assertEquals("8K ctx", ModelInfo("m", contextLength = 8192).contextLabel())
        assertEquals("1M ctx", ModelInfo("m", contextLength = 1_000_000).contextLabel())
    }

    @Test
    fun context_label_empty_when_missing() {
        assertEquals("", ModelInfo("m").contextLabel())
    }

    @Test
    fun price_label_is_per_million_input_tokens() {
        // pricing.prompt — цена за токен в USD строкой (как отдаёт OpenRouter)
        assertEquals("$0.15/M", ModelInfo("m", pricing = ModelPricing(prompt = "0.00000015")).priceLabel())
        assertEquals("$3/M", ModelInfo("m", pricing = ModelPricing(prompt = "0.000003")).priceLabel())
    }

    @Test
    fun price_label_free_when_zero() {
        assertEquals("Free", ModelInfo("m", pricing = ModelPricing(prompt = "0")).priceLabel())
    }

    @Test
    fun price_label_empty_when_missing() {
        assertEquals("", ModelInfo("m").priceLabel())
        assertEquals("", ModelInfo("m", pricing = ModelPricing(prompt = "")).priceLabel())
    }

    @Test
    fun negative_price_treated_as_unknown() {
        // openrouter/auto отдаёт prompt = "-1" (переменная цена)
        val auto = ModelInfo("openrouter/auto", pricing = ModelPricing(prompt = "-1"))
        assertEquals("", auto.priceLabel())
        // и отсекается любым ценовым фильтром, а не пролезает как «дёшево»
        assertEquals(emptyList(), listOf(auto).filterModels("", PriceFilter.UNDER_1, ContextFilter.ANY).map { it.id })
    }

    private val models = listOf(
        ModelInfo("free", contextLength = 8_000, pricing = ModelPricing(prompt = "0")),
        ModelInfo("cheap", contextLength = 128_000, pricing = ModelPricing(prompt = "0.0000005")), // $0.5/M
        ModelInfo("mid", contextLength = 200_000, pricing = ModelPricing(prompt = "0.000003")),     // $3/M
        ModelInfo("pricey", contextLength = 1_000_000, pricing = ModelPricing(prompt = "0.00001")), // $10/M
        ModelInfo("unknown"),                                                                       // нет цены/ctx
    )

    private fun ids(price: PriceFilter = PriceFilter.ANY, ctx: ContextFilter = ContextFilter.ANY, q: String = "") =
        models.filterModels(q, price, ctx).map { it.id }

    @Test
    fun no_filters_returns_all() {
        assertEquals(listOf("free", "cheap", "mid", "pricey", "unknown"), ids())
    }

    @Test
    fun price_free_keeps_only_zero() {
        assertEquals(listOf("free"), ids(price = PriceFilter.FREE))
    }

    @Test
    fun price_under_1_keeps_free_and_cheap_but_not_priced_higher_or_unknown() {
        assertEquals(listOf("free", "cheap"), ids(price = PriceFilter.UNDER_1))
    }

    @Test
    fun price_under_5_excludes_pricey_and_unknown() {
        assertEquals(listOf("free", "cheap", "mid"), ids(price = PriceFilter.UNDER_5))
    }

    @Test
    fun context_min_filters_and_drops_unknown() {
        assertEquals(listOf("cheap", "mid", "pricey"), ids(ctx = ContextFilter.K128))
        assertEquals(listOf("pricey"), ids(ctx = ContextFilter.M1))
    }

    @Test
    fun price_and_context_and_query_combine() {
        // ≤$5/M, ≥128K ctx → cheap, mid; query «mid» сужает до mid
        assertEquals(listOf("mid"), ids(price = PriceFilter.UNDER_5, ctx = ContextFilter.K128, q = "mid"))
    }

    private val named = listOf(
        ModelInfo("b", "Beta", pricing = ModelPricing(prompt = "0.000003")), // $3/M
        ModelInfo("a", "Alpha", pricing = ModelPricing(prompt = "0.0000005")), // $0.5/M
        ModelInfo("z", "Zeta"), // нет цены
    )

    @Test
    fun sort_by_name_case_insensitive() {
        assertEquals(listOf("Alpha", "Beta", "Zeta"), named.sortModels(SortOrder.NAME).map { it.name })
    }

    @Test
    fun sort_by_price_ascending_unknown_last() {
        assertEquals(listOf("a", "b", "z"), named.sortModels(SortOrder.PRICE_ASC).map { it.id })
    }

    @Test
    fun sort_by_price_descending_unknown_last() {
        assertEquals(listOf("b", "a", "z"), named.sortModels(SortOrder.PRICE_DESC).map { it.id })
    }
}
