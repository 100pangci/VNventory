package com.vnventory.app.data.mapper

import com.vnventory.app.data.local.entity.ExpenseAllocationEntity
import com.vnventory.app.data.local.entity.ExpenseEntity
import com.vnventory.app.data.local.entity.OwnedCopyEntity
import com.vnventory.app.data.local.entity.PurchaseOrderEntity
import com.vnventory.app.data.local.entity.ReleaseCacheEntity
import com.vnventory.app.data.local.entity.VnCacheEntity
import com.vnventory.app.domain.model.Expense
import com.vnventory.app.domain.model.OwnedCopy
import com.vnventory.app.domain.model.PurchaseOrder
import com.vnventory.app.domain.model.ReleaseInfo
import com.vnventory.app.domain.model.VnInfo

// ---------------------------------------------------------------------------
// Entity <-> Domain（本地事实来源）
// ---------------------------------------------------------------------------

fun VnCacheEntity.toDomain(): VnInfo = VnInfo(
    id = vndbId,
    legacyTitle = title,
    legacyAltTitle = altTitle,
    released = released,
    imageUrl = imageUrl,
    description = description,
    fromCache = true,
    originalTitle = originalTitle,
    romanizedTitle = romanizedTitle,
)

fun VnInfo.toEntity(fetchedAt: Long): VnCacheEntity = VnCacheEntity(
    originalTitle = originalTitle,
    romanizedTitle = romanizedTitle,
    vndbId = id,
    title = legacyTitle,
    altTitle = legacyAltTitle,
    released = released,
    imageUrl = imageUrl,
    description = description,
    fetchedAt = fetchedAt,
)

fun ReleaseCacheEntity.toDomain(vnId: String): ReleaseInfo = ReleaseInfo(
    originalTitle = originalTitle,
    romanizedTitle = romanizedTitle,
    id = vndbId,
    vnId = vnId,
    legacyTitle = title,
    released = released,
    platforms = splitCompact(platforms, ReleaseCacheEntity.SEP_LIST),
    languages = splitCompact(languages, ReleaseCacheEntity.SEP_LIST),
    publishers = splitCompact(publishers, ReleaseCacheEntity.SEP_PUBLISHERS),
    jan = jan,
    minAge = minAge,
    official = official,
    packagingImageUrl = packagingImageUrl,
    coverImageUrl = null,
)

fun ReleaseInfo.toEntity(fetchedAt: Long): ReleaseCacheEntity = ReleaseCacheEntity(
    originalTitle = originalTitle,
    romanizedTitle = romanizedTitle,
    vndbId = id,
    title = legacyTitle,
    released = released,
    platforms = platforms.joinToString(ReleaseCacheEntity.SEP_LIST),
    languages = languages.joinToString(ReleaseCacheEntity.SEP_LIST),
    publishers = publishers.joinToString(ReleaseCacheEntity.SEP_PUBLISHERS),
    jan = jan,
    minAge = minAge,
    official = official,
    packagingImageUrl = packagingImageUrl,
    fetchedAt = fetchedAt,
)

fun OwnedCopyEntity.toDomain(): OwnedCopy = OwnedCopy(
    vnOriginalTitle = vnOriginalTitle,
    vnRomanizedTitle = vnRomanizedTitle,
    releaseOriginalTitle = releaseOriginalTitle,
    releaseRomanizedTitle = releaseRomanizedTitle,
    id = id,
    vnId = vnId,
    releaseId = releaseId,
    vnTitle = vnTitle,
    releaseTitle = releaseTitle,
    coverUrl = coverUrl,
    priceMinor = priceMinor,
    currency = currency,
    condition = condition,
    conditionNote = conditionNote,
    purchaseDate = purchaseDate,
    shop = shop,
    orderId = orderId,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun OwnedCopy.toEntity(): OwnedCopyEntity = OwnedCopyEntity(
    vnOriginalTitle = vnOriginalTitle,
    vnRomanizedTitle = vnRomanizedTitle,
    releaseOriginalTitle = releaseOriginalTitle,
    releaseRomanizedTitle = releaseRomanizedTitle,
    id = id,
    vnId = vnId,
    releaseId = releaseId,
    vnTitle = vnTitle,
    releaseTitle = releaseTitle,
    coverUrl = coverUrl,
    priceMinor = priceMinor,
    currency = currency,
    condition = condition,
    conditionNote = conditionNote,
    purchaseDate = purchaseDate,
    shop = shop,
    orderId = orderId,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun PurchaseOrderEntity.toDomain(): PurchaseOrder = PurchaseOrder(
    id = id,
    title = title,
    merchant = merchant,
    orderDate = orderDate,
    currency = currency,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun PurchaseOrder.toEntity(): PurchaseOrderEntity = PurchaseOrderEntity(
    id = id,
    title = title,
    merchant = merchant,
    orderDate = orderDate,
    currency = currency,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ExpenseEntity.toDomain(allocations: Map<Long, Long> = emptyMap()): Expense = Expense(
    id = id,
    orderId = orderId,
    name = name,
    category = category,
    amountMinor = amountMinor,
    currency = currency,
    mode = mode,
    notes = notes,
    createdAt = createdAt,
    allocations = allocations,
)

fun Expense.toEntity(): ExpenseEntity = ExpenseEntity(
    id = id,
    orderId = orderId,
    name = name,
    category = category,
    amountMinor = amountMinor,
    currency = currency,
    mode = mode,
    notes = notes,
    createdAt = createdAt,
)

fun ExpenseAllocationEntity.toPair(): Pair<Long, Long> = ownedCopyId to amountMinor

private fun splitCompact(raw: String, separator: String): List<String> =
    raw.split(separator)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
