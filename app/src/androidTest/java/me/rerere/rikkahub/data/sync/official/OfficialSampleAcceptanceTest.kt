package me.rerere.rikkahub.data.sync.official

import android.database.sqlite.SQLiteDatabase
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

/**
 * 验收：拿真实的官方备份，与一份**独立算出来的**期望值逐表比对。
 *
 * 期望值由 `tools/backup-expectations.py` 直接用 Python 的 sqlite3 读原始备份算出，不经过我们
 * 的读取层，所以这里对不上就是读取层读错了，不是自证。
 *
 * 样本与期望值都放在仓库外（含私人数据），测试从设备上读，没有就跳过；CI 上不会因此失败。
 * 具体命令见 `docs/custom/backup/acceptance.md`。
 */
@RunWith(AndroidJUnit4::class)
class OfficialSampleAcceptanceTest {
    /** 一个样本：版本、设备上的库路径，以及独立算出来的期望值 */
    private data class Case(val version: Int, val sample: File, val expectations: File) {
        val present: Boolean get() = sample.isFile && expectations.isFile
    }

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val cases = listOf(
        Case(
            version = 32,
            sample = File("/data/local/tmp/rikkahub-sample-v32.db"),
            expectations = File("/data/local/tmp/backup-expectations-v32.json"),
        ),
        Case(
            version = 31,
            sample = File("/data/local/tmp/rikkahub-sample-v31.db"),
            expectations = File("/data/local/tmp/backup-expectations-v31.json"),
        ),
    )

    @Test fun everyTableMatchesTheIndependentlyComputedDigest() {
        val available = cases.filter { it.present }
        assumeTrue("没有放样本或期望值，跳过", available.isNotEmpty())
        available.forEach(::verify)
    }

    private fun verify(case: Case) {
        val expected = Json.parseToJsonElement(case.expectations.readText()).jsonObject
        assertEquals(
            "v${case.version} 期望值的 schema 版本要与样本一致",
            case.version,
            expected.getValue("schemaFileVersion").jsonPrimitive.int,
        )

        OfficialDatabaseReader.open(case.sample).use { reader ->
            assertEquals("读取层选中的快照版本", case.version, reader.snapshot.version)
            val tables = expected.getValue("tables").jsonObject
            for ((name, raw) in tables) {
                val entry = raw.jsonObject
                val expectedRows = entry.getValue("rows").jsonPrimitive.long
                val expectedDigest = entry.getValue("sha256").jsonPrimitive.content
                val table = requireNotNull(reader.tables[name]) { "快照里没有表 $name" }

                // 期望值是按主键升序算的，读取层不保证顺序，所以这里先按主键排序再算摘要
                val pkIndex = table.primaryKey.map { key -> table.columns.indexOfFirst { it.name == key } }
                val collected = ArrayList<Pair<List<Any?>, ByteArray>>()
                reader.forEachRow(name) { row ->
                    val cells = ByteArrayOutputStream()
                    for (column in table.columns) {
                        writeCell(cells, row.value(column.name))
                    }
                    collected.add(pkIndex.map { row.value(table.columns[it].name) } to cells.toByteArray())
                }
                collected.sortWith { left, right -> compareKeys(left.first, right.first) }

                val digest = MessageDigest.getInstance("SHA-256")
                var first = true
                for ((_, cells) in collected) {
                    if (!first) digest.update(0x1e.toByte())
                    first = false
                    digest.update(cells)
                }
                assertEquals("表 $name 的行数", expectedRows, collected.size.toLong())
                assertEquals(
                    "表 $name 的内容摘要",
                    expectedDigest,
                    digest.digest().joinToString("") { "%02x".format(it) },
                )
            }
            assertTrue("样本至少要有一张非空表", tables.values.any { it.jsonObject.getValue("rows").jsonPrimitive.long > 0 })
        }
    }

    /**
     * 转换产物要与同一份期望值对得上。
     *
     * 这一步才是「导入器真的搬对了数据」的判据：读取层对得上只说明能读，转换器与写入层
     * 走的是另一条路（新建库、按快照列插入、重写附件路径），写错列内容但行数对不会在这里混过去。
     */
    @Test fun theConvertedDatabaseMatchesTheSameDigests() {
        val available = cases.filter { it.present }
        assumeTrue("没有放样本或期望值，跳过", available.isNotEmpty())
        val filesUriPrefix = context.filesDir.toUri().toString().trimEnd('/') + "/"
        available.forEach { case ->
            val expected = Json.parseToJsonElement(case.expectations.readText()).jsonObject
            val target = File(context.cacheDir, "acceptance-v${case.version}.db")
            target.delete()
            target.parentFile?.mkdirs()
            runBlocking { OfficialDatabaseConverter(context).convert(case.sample, target) }
            try {
                SQLiteDatabase.openDatabase(target.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                    assertEquals("转换结果应当是当前 Room 版本", OfficialDatabaseSchema.ROOM_VERSION, db.version)
                }
                OfficialDatabaseReader.open(target).use { reader ->
                    assertTrue("转换结果不应有快照之外的列", reader.unexpectedColumns.isEmpty())
                    // 转换产物是当前版本的结构，源版本没有的列不参与比对：这里按**源版本**的列
                    // 集合算摘要，语义是「源里有的每一列都原样搬过来了」。
                    val sourceTables = requireNotNull(OfficialSnapshots.forVersion(case.version)) {
                        "没有 v${case.version} 的官方快照"
                    }.tables
                    val tables = expected.getValue("tables").jsonObject
                    for ((name, raw) in tables) {
                        val entry = raw.jsonObject
                        val table = requireNotNull(sourceTables[name]) { "源快照里没有表 $name" }
                        val expectedRows = entry.getValue("rows").jsonPrimitive.long
                        val expectedDigest = entry.getValue("sha256").jsonPrimitive.content
                        val pkIndex = table.primaryKey.map { key -> table.columns.indexOfFirst { it.name == key } }
                        val collected = ArrayList<Pair<List<Any?>, ByteArray>>()
                        var foreignPrefix = 0
                        reader.forEachRow(name) { row ->
                            val cells = ByteArrayOutputStream()
                            for (column in table.columns) {
                                val value = row.value(column.name)
                                if (value is String && VALUE_PREFIX.containsMatchIn(value) &&
                                    !value.contains(filesUriPrefix)
                                ) {
                                    foreignPrefix++
                                }
                                writeCell(cells, value)
                            }
                            collected.add(pkIndex.map { row.value(table.columns[it].name) } to cells.toByteArray())
                        }
                        collected.sortWith { left, right -> compareKeys(left.first, right.first) }
                        val digest = MessageDigest.getInstance("SHA-256")
                        var first = true
                        for ((_, cells) in collected) {
                            if (!first) digest.update(0x1e.toByte())
                            first = false
                            digest.update(cells)
                        }
                        assertEquals("v${case.version} 表 $name 转换后的行数", expectedRows, collected.size.toLong())
                        assertEquals(
                            "v${case.version} 表 $name 转换后的内容摘要",
                            expectedDigest,
                            digest.digest().joinToString("") { "%02x".format(it) },
                        )
                        assertEquals("v${case.version} 表 $name 还有指向别的安装的附件路径", 0, foreignPrefix)
                    }
                }
            } finally {
                target.delete()
            }
        }
    }

    /** SQLite 的排序：NULL < 数字 < 文本 */
    private fun compareKeys(left: List<Any?>, right: List<Any?>): Int {
        for (index in left.indices) {
            val a = left[index]
            val b = right[index]
            val result = when {
                a == null && b == null -> 0
                a == null -> -1
                b == null -> 1
                a is Long && b is Long -> a.compareTo(b)
                else -> a.toString().compareTo(b.toString())
            }
            if (result != 0) return result
        }
        return 0
    }

    /** 附件引用的绝对前缀在导入时会被换成本机路径，校验内容时统一成占位符 */
    private val VALUE_PREFIX = Regex("""file:///data/(?:user/\d+|data)/[^/"\s]+/files/""")

    private val VALUE_PREFIX_PLACEHOLDER = "file://<files>/"

    /** 与 `tools/backup-expectations.py` 的规范化逐字节一致：类型字节 + 4 字节小端长度 + 载荷 */
    private fun writeCell(sink: ByteArrayOutputStream, value: Any?) {
        val payload: ByteArray = when (value) {
            null -> ByteArray(0)
            is Long -> value.toString().toByteArray()
            is Double -> {
                val bits = java.lang.Double.doubleToLongBits(value)
                ByteArray(8) { index -> (bits ushr (56 - index * 8)).toByte() }
            }

            is String -> VALUE_PREFIX.replace(value, VALUE_PREFIX_PLACEHOLDER).toByteArray()
            is ByteArray -> value
            else -> error("未知的列值类型：${value::class}")
        }
        val kind = when (value) {
            null -> 'N'
            is Long -> 'I'
            is Double -> 'R'
            is String -> 'T'
            else -> 'B'
        }
        sink.write(kind.code)
        sink.write(payload.size and 0xFF)
        sink.write(payload.size ushr 8 and 0xFF)
        sink.write(payload.size ushr 16 and 0xFF)
        sink.write(payload.size ushr 24 and 0xFF)
        sink.write(payload)
    }
}
