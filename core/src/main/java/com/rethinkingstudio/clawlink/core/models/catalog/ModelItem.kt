package com.rethinkingstudio.clawlink.core.models.catalog

import kotlinx.serialization.Serializable

@Serializable
data class ModelItem(
    val providerId: String,
    val modelId: String,
    val modelAlias: String,
    val modelName: String,
    val isSelected: Boolean = false,
    val isDefault: Boolean = false,
    val contextWindow: Int? = null,
    val maxOutputTokens: Int? = null,
    val capabilities: List<String>? = null,
    val pricing: ModelPricing? = null
) {
    val displayName: String get() = modelAlias.ifBlank { modelName }
    val subtitle: String get() = modelName.ifBlank { modelId }
}

@Serializable
data class ModelPricing(
    val inputPerMillionTokens: Double? = null,
    val outputPerMillionTokens: Double? = null,
    val currency: String = "USD"
)
