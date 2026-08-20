package org.gotson.komga.infrastructure.datasource

import com.ibm.icu.text.Collator
import io.github.oshai.kotlinlogging.KotlinLogging
import org.gotson.komga.infrastructure.unicode.Collators
import org.gotson.komga.language.stripAccents
import org.sqlite.Collation
import org.sqlite.Function
import org.sqlite.SQLiteConnection
import org.sqlite.SQLiteDataSource
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.sql.Connection

private val log = KotlinLogging.logger {}

class SqliteUdfDataSource : SQLiteDataSource() {
  companion object {
    const val UDF_STRIP_ACCENTS = "UDF_STRIP_ACCENTS"
    const val UDF_URL_DECODE = "UDF_URL_DECODE"
    const val COLLATION_UNICODE_1 = "COLLATION_UNICODE_1"
    const val COLLATION_UNICODE_3 = "COLLATION_UNICODE_3"
  }

  override fun getConnection(): Connection = super.getConnection().also { addAllUdf(it as SQLiteConnection) }

  override fun getConnection(
    username: String?,
    password: String?,
  ): SQLiteConnection = super.getConnection(username, password).also { addAllUdf(it) }

  private fun addAllUdf(connection: SQLiteConnection) {
    createUdfRegexp(connection)
    createUdfStripAccents(connection)
    createUdfUrlDecode(connection)
    createUnicodeCollation(connection, COLLATION_UNICODE_3, Collators.collator3)
    createUnicodeCollation(connection, COLLATION_UNICODE_1, Collators.collator1)
  }

  private fun createUdfRegexp(connection: SQLiteConnection) {
    log.debug { "Adding custom REGEXP function" }
    Function.create(
      connection,
      "REGEXP",
      object : Function() {
        override fun xFunc() {
          val regexp = (value_text(0) ?: "").toRegex(RegexOption.IGNORE_CASE)
          val text = value_text(1) ?: ""

          result(if (regexp.containsMatchIn(text)) 1 else 0)
        }
      },
    )
  }

  private fun createUdfStripAccents(connection: SQLiteConnection) {
    log.debug { "Adding custom $UDF_STRIP_ACCENTS function" }
    Function.create(
      connection,
      UDF_STRIP_ACCENTS,
      object : Function() {
        override fun xFunc() =
          when (val text = value_text(0)) {
            null -> error("Argument must not be null")
            else -> result(text.stripAccents())
          }
      },
    )
  }

  private fun createUdfUrlDecode(connection: SQLiteConnection) {
    log.debug { "Adding custom $UDF_URL_DECODE function" }
    Function.create(
      connection,
      UDF_URL_DECODE,
      object : Function() {
        override fun xFunc() {
          when (val text = value_text(0)) {
            null -> result()
            else ->
              try {
                // URLDecoder is designed for application/x-www-form-urlencoded and would turn a literal
                // '+' into a space; escape it first so percent-encoded URI path segments decode correctly.
                result(URLDecoder.decode(text.replace("+", "%2B"), StandardCharsets.UTF_8))
              } catch (e: IllegalArgumentException) {
                // not a validly percent-encoded string, return as-is
                result(text)
              }
          }
        }
      },
    )
  }

  private fun createUnicodeCollation(
    connection: SQLiteConnection,
    collationName: String,
    collator: Collator,
  ) {
    log.debug { "Adding custom $collationName collation" }
    Collation.create(
      connection,
      collationName,
      object : Collation() {
        override fun xCompare(
          str1: String,
          str2: String,
        ): Int = collator.compare(str1, str2)
      },
    )
  }
}
