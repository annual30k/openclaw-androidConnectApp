package com.rethinkingstudio.clawlink.core.models.catalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ModelItem(
    @SerialName("providerId") val providerId: String = "",
    @SerialName("modelId") val modelId: String = "",
    @SerialName("alias") val modelAlias: String = "",
    @SerialName("name") val modelName: String = "",
    @SerialName("provider") val provider: String = "",
    @SerialName("isSelected") val isSelected: Boolean = false,
    @SerialName("isDefault") val isDefault: Boolean = false,
    @SerialName("contextWindow") val contextWindow: String? = null,
    @SerialName("tags") val capabilities: List<String>? = null,
    val pricing: ModelPricing? = null
) {
    val displayName: String get() = modelAlias.ifBlank { modelName.ifBlank { modelId } }
    val subtitle: String get() = provider.ifBlank { providerId }
}

@Serializable
data class ModelPricing(
    val inputPerMillionTokens: Double? = null,
    val outputPerMillionTokens: Double? = null,
    val currency: String = "USD"
)
