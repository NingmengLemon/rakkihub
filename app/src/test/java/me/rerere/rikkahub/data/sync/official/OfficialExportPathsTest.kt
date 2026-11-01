package me.rerere.rikkahub.data.sync.official

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 官方兼容导出的前缀推导。
 *
 * 这条逻辑决定「导出的备份装到官方那边之后附件还在不在」，但它依赖的只是路径字符串，所以放在
 * JVM 上跑，不用等设备。
 */
class OfficialExportPathsTest {

    private val packageName = "top.lemoneko.rakkihub"
    private val official = "me.rerere.rikkahub"

    @Test fun `official prefix keeps the user number and swaps the package`() {
        assertEquals(
            "file:///data/user/0/$official/files/",
            OfficialExportPaths.officialFilesPrefix("file:///data/user/0/$packageName/files/", packageName, official),
        )
        assertEquals(
            "file:///data/user/10/$official/files/",
            OfficialExportPaths.officialFilesPrefix("file:///data/user/10/$packageName/files/", packageName, official),
        )
    }

    @Test fun `debug suffix is part of the package segment`() {
        assertEquals(
            "file:///data/user/0/$official/files/",
            OfficialExportPaths.officialFilesPrefix(
                "file:///data/user/0/$packageName.debug/files/",
                "$packageName.debug",
                official
            ),
        )
    }

    @Test fun `user number is read from the path`() {
        assertEquals("0", OfficialExportPaths.userNumber("file:///data/user/0/$packageName/files/"))
        assertEquals("10", OfficialExportPaths.userNumber("file:///data/user/10/$packageName/files/"))
        assertNull(OfficialExportPaths.userNumber("file:///data/data/$packageName/files/"))
    }

    @Test fun `canonical user path rewrites to the official install`() {
        val rewrites = OfficialExportPaths.attachmentRewrites(
            "file:///data/user/0/$packageName/files/",
            packageName,
            official,
        )
        assertTrue(
            rewrites.contains("file:///data/user/0/$packageName/files/" to "file:///data/user/0/$official/files/")
        )
    }

    @Test fun `legacy data path is covered too`() {
        val rewrites = OfficialExportPaths.attachmentRewrites(
            "file:///data/user/0/$packageName/files/",
            packageName,
            official,
        )
        assertTrue(rewrites.contains("file:///data/data/$packageName/files/" to "file:///data/data/$official/files/"))
    }

    @Test fun `work profile user number is kept`() {
        val rewrites = OfficialExportPaths.attachmentRewrites(
            "file:///data/user/10/$packageName/files/",
            packageName,
            official,
        )
        assertTrue(rewrites.any { it.first == "file:///data/user/10/$packageName/files/" })
        assertTrue(rewrites.none { it.first == "file:///data/user/0/$packageName/files/" })
    }

    @Test fun `a non canonical files dir still yields the canonical shapes`() {
        // 测试机上的 filesDir 可能挂在 cache 下；仍要能推出规范形态，正式安装里两者相同
        val rewrites = OfficialExportPaths.attachmentRewrites(
            "file:///data/user/0/$packageName/cache/backup-test/files/",
            packageName,
            official,
        )
        assertEquals(3, rewrites.size)
        assertTrue(rewrites.any { it.first == "file:///data/user/0/$packageName/files/" })
    }

    @Test fun `the same package needs no rewrite`() {
        assertTrue(
            OfficialExportPaths.attachmentRewrites("file:///data/user/0/$official/files/", official, official).isEmpty()
        )
    }

    @Test fun `a data shaped files dir still yields one rewrite`() {
        // filesDir 本身是 /data/data/ 形态时取不到用户号，只剩实际形态；正式安装不会这样
        val rewrites = OfficialExportPaths.attachmentRewrites(
            "file:///data/data/$packageName/files/",
            packageName,
            official,
        )
        assertEquals(
            listOf("file:///data/data/$packageName/files/" to "file:///data/data/$official/files/"),
            rewrites,
        )
    }

    @Test fun `like patterns escape the characters that change their meaning`() {
        assertEquals("a\\_b", OfficialExportPaths.escapeLikePattern("a_b"))
        assertEquals("a\\%b", OfficialExportPaths.escapeLikePattern("a%b"))
        assertEquals("a\\\\b", OfficialExportPaths.escapeLikePattern("a\\b"))
        assertEquals("top.lemoneko.rakkihub", OfficialExportPaths.escapeLikePattern("top.lemoneko.rakkihub"))
    }
}
