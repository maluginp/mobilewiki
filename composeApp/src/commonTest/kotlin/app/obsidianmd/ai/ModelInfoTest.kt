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
}
