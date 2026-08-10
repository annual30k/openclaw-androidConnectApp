package com.rethinkingstudio.clawlink.core.state.chat

private data class CanonicalTimelineOrderKeyParts(
    val version: Int,
    val namespace: String,
    val conversationSequence: String
)

private fun canonicalTimelineOrderKeyParts(value: String): CanonicalTimelineOrderKeyParts? {
    val fields = value.split('|')
    val version = when (fields.firstOrNull()) {
        "v1" -> 1
        "v4" -> 4
        "v5" -> 5
        else -> return null
    }
    val namespace: String
    val conversationSequence: String
    val versionNumericFields: List<String>
    when (version) {
        1 -> {
            if (fields.size < 5) return null
            namespace = "0"
            conversationSequence = fields[1]
            versionNumericFields = listOf(fields[2], fields[3].substringBefore(':'))
        }
        4 -> {
            if (fields.size < 6) return null
            namespace = fields[1]
            conversationSequence = fields[2]
            versionNumericFields = listOf(fields[3], fields[4].substringBefore(':'))
        }
        else -> {
            if (fields.size < 7) return null
            namespace = fields[1]
            conversationSequence = fields[2]
            versionNumericFields = listOf(fields[3], fields[4])
        }
    }
    if (namespace.isEmpty() || !namespace.all(Char::isDigit)) return null
    if (conversationSequence.isEmpty() || !conversationSequence.all(Char::isDigit)) return null
    if (versionNumericFields.any { it.isEmpty() || !it.all(Char::isDigit) }) return null
    return CanonicalTimelineOrderKeyParts(
        version = version,
        namespace = namespace,
        conversationSequence = conversationSequence
    )
}

private fun compareCanonicalDecimal(left: String, right: String): Int {
    val normalizedLeft = left.trimStart('0').ifEmpty { "0" }
    val normalizedRight = right.trimStart('0').ifEmpty { "0" }
    val lengthComparison = normalizedLeft.length.compareTo(normalizedRight.length)
    if (lengthComparison != 0) return lengthComparison
    return normalizedLeft.compareTo(normalizedRight)
}

fun compareCanonicalTimelineOrderKeys(left: String, right: String): Int {
    if (left == right) return 0
    val leftParts = canonicalTimelineOrderKeyParts(left)
    val rightParts = canonicalTimelineOrderKeyParts(right)
    if (leftParts == null && rightParts == null) {
        return left.compareTo(right)
    }
    // v4 是 rank-first，v5 是 suborder-first；两种格式不能安全投影成同一字段序。
    // 公共前缀先按 namespace/sequence 比较，同一位置再按版本代际和该版本原始 key
    // 比较，既保留各版本内部语义，也保证比较器可传递。同 turn 的 user/output 顺序
    // 由 reconciler 使用稳定 turn identity 锚定，不能塞进全局 key 比较器制造排序环。
    if (leftParts == null) return -1
    if (rightParts == null) return 1
    listOf(
        leftParts.namespace to rightParts.namespace,
        leftParts.conversationSequence to rightParts.conversationSequence
    ).forEach { (leftValue, rightValue) ->
        val comparison = compareCanonicalDecimal(leftValue, rightValue)
        if (comparison != 0) return comparison
    }
    val versionComparison = leftParts.version.compareTo(rightParts.version)
    if (versionComparison != 0) return versionComparison
    return left.compareTo(right)
}
