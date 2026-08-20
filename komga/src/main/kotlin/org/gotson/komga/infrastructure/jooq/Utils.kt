package org.gotson.komga.infrastructure.jooq

import com.fasterxml.jackson.databind.ObjectMapper
import org.gotson.komga.domain.model.AllowExclude
import org.gotson.komga.domain.model.ContentRestrictions
import org.gotson.komga.domain.model.MediaExtension
import org.gotson.komga.infrastructure.datasource.SqliteUdfDataSource
import org.gotson.komga.jooq.main.Tables
import org.jooq.Condition
import org.jooq.Field
import org.jooq.SortField
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

fun Field<String>.noCase() = this.collate("NOCASE")

/**
 * Warning: SQLite doesn't use collations with LIKE
 */
fun Field<String>.unicode1() = this.collate(SqliteUdfDataSource.COLLATION_UNICODE_1)

fun Field<String>.unicode3() = this.collate(SqliteUdfDataSource.COLLATION_UNICODE_3)

fun Field<String>.udfStripAccents() = DSL.function(SqliteUdfDataSource.UDF_STRIP_ACCENTS, String::class.java, this)

fun Field<String>.udfUrlDecode() = DSL.function(SqliteUdfDataSource.UDF_URL_DECODE, String::class.java, this)

fun Sort.toOrderBy(sorts: Map<String, Field<out Any>>): List<SortField<out Any>> =
  this.mapNotNull {
    it.toSortField(sorts)
  }

fun Sort.Order.toSortField(sorts: Map<String, Field<out Any>>): SortField<out Any>? {
  val f = sorts[property] ?: return null
  return if (isAscending) f.asc() else f.desc()
}

fun Field<String>.sortByValues(
  values: List<String>,
  asc: Boolean = true,
): Field<Int> {
  var c = DSL.choose(this).`when`("dummy dsl", Int.MAX_VALUE)
  val multiplier = if (asc) 1 else -1
  values.forEachIndexed { index, value -> c = c.`when`(value, index * multiplier) }
  return c.otherwise(Int.MAX_VALUE)
}

/**
 * Computes the top-level folder name of a book's file relative to its library's root folder,
 * but only when the book is nested at least 2 folder levels deep (e.g. `<source>/<series>/<book.cbz>`).
 *
 * If a book is only 1 folder level deep (e.g. `<series>/<book.cbz>` as in standard libraries),
 * or sits directly at the root of the library, it returns null.
 *
 * This allows libraries structured with intermediate subfolders (such as Suwayomi: `Suwayomi/<Source>/<Series>/<Book>`)
 * to be filtered by folder/source name without hardcoding folder names or affecting single-level libraries.
 *
 * The result is percent-decoded (e.g. `Site%20Scans` returns `Site Scans`).
 */
fun bookFolderField(
  bookUrl: Field<String>,
  libraryRoot: Field<String>,
): Field<String> {
  // path portion after the library root, e.g. "MangaDex/One piece/c0024.cbz" or "Superhuman Era/Superhuman Era c0024.cbz"
  val relative = DSL.field("substr({0}, length({1}) + 1)", SQLDataType.VARCHAR, bookUrl, libraryRoot)
  // position of the 1st '/' separator
  val slash1 = DSL.field("instr({0}, '/')", SQLDataType.INTEGER, relative)
  // 1st path segment (e.g. "MangaDex" or "Superhuman Era")
  val firstSegment = DSL.field("CASE WHEN {1} > 0 THEN substr({0}, 1, {1} - 1) END", SQLDataType.VARCHAR, relative, slash1)
  // everything after the 1st path segment (e.g. "One piece/c0024.cbz" or "Superhuman Era c0024.cbz")
  val afterFirst = DSL.field("CASE WHEN {1} > 0 THEN substr({0}, {1} + 1) END", SQLDataType.VARCHAR, relative, slash1)
  // position of the 2nd '/' separator
  val slash2 = DSL.field("instr({0}, '/')", SQLDataType.INTEGER, afterFirst)
  // only return the 1st segment if there is at least a 2nd '/' (i.e. at least 2 directory levels deep)
  val rawFolder = DSL.field("CASE WHEN {0} > 0 THEN {1} END", SQLDataType.VARCHAR, slash2, firstSegment)
  return rawFolder.udfUrlDecode()
}

fun Field<String>.inOrNoCondition(list: Collection<String>?): Condition =
  when {
    list == null -> DSL.noCondition()
    list.isEmpty() -> DSL.falseCondition()
    else -> this.`in`(list)
  }

fun ContentRestrictions.toCondition(): Condition {
  val ageAllowed =
    if (ageRestriction?.restriction == AllowExclude.ALLOW_ONLY) {
      Tables.SERIES_METADATA.AGE_RATING.isNotNull
        .and(Tables.SERIES_METADATA.AGE_RATING.lessOrEqual(ageRestriction.age))
    } else {
      DSL.noCondition()
    }

  val labelAllowed =
    if (labelsAllow.isNotEmpty())
      Tables.SERIES_METADATA.SERIES_ID.`in`(
        DSL
          .select(Tables.SERIES_METADATA_SHARING.SERIES_ID)
          .from(Tables.SERIES_METADATA_SHARING)
          .where(Tables.SERIES_METADATA_SHARING.LABEL.`in`(labelsAllow)),
      )
    else
      DSL.noCondition()

  val ageDenied =
    if (ageRestriction?.restriction == AllowExclude.EXCLUDE)
      Tables.SERIES_METADATA.AGE_RATING.isNull
        .or(Tables.SERIES_METADATA.AGE_RATING.lessThan(ageRestriction.age))
    else
      DSL.noCondition()

  val labelDenied =
    if (labelsExclude.isNotEmpty())
      Tables.SERIES_METADATA.SERIES_ID.notIn(
        DSL
          .select(Tables.SERIES_METADATA_SHARING.SERIES_ID)
          .from(Tables.SERIES_METADATA_SHARING)
          .where(Tables.SERIES_METADATA_SHARING.LABEL.`in`(labelsExclude)),
      )
    else
      DSL.noCondition()

  return ageAllowed
    .or(labelAllowed)
    .and(ageDenied.and(labelDenied))
}

fun ObjectMapper.serializeJsonGz(obj: Any): ByteArray? =
  try {
    ByteArrayOutputStream().use { baos ->
      GZIPOutputStream(baos).use { gz ->
        this.writeValue(gz, obj)
        baos.toByteArray()
      }
    }
  } catch (e: Exception) {
    null
  }

inline fun <reified T> ObjectMapper.deserializeJsonGz(gzJson: ByteArray?): T? {
  if (gzJson == null) return null
  return try {
    GZIPInputStream(gzJson.inputStream()).use { gz ->
      this.readValue(gz, T::class.java) as T
    }
  } catch (e: Exception) {
    null
  }
}

fun ObjectMapper.deserializeMediaExtension(
  extensionClass: String?,
  extensionBlob: ByteArray?,
): MediaExtension? {
  if (extensionClass == null || extensionBlob == null) return null
  return try {
    GZIPInputStream(extensionBlob.inputStream()).use { gz ->
      this.readValue(gz, Class.forName(extensionClass)) as MediaExtension
    }
  } catch (e: Exception) {
    null
  }
}

fun rlbAlias(readListId: String) = Tables.READLIST_BOOK.`as`("RLB_$readListId")

fun csAlias(collectionId: String) = Tables.COLLECTION_SERIES.`as`("CS_$collectionId")

fun <T> buildPage(
  items: List<T>,
  pageable: Pageable,
  count: Int,
  sort: Sort?,
): PageImpl<T> =
  PageImpl(
    items,
    if (pageable.isPaged)
      PageRequest.of(pageable.pageNumber, pageable.pageSize, sort ?: pageable.sort)
    else
      PageRequest.of(0, maxOf(count, 20), sort ?: pageable.sort),
    count.toLong(),
  )
