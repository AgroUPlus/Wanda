package com.wander.android

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every GraphQL operation Wanda sends must name a root field Agro's committed schema has.
 *
 * `agro-schema.graphql` is a copy of `docs/api/schema.graphql` in the Agro repository, which Agro's
 * own snapshot test keeps in step with the server. Refresh it from there whenever Agro's API
 * changes; this test then fails on any call the server no longer answers, on a laptop instead of
 * on a phone nobody can hot-fix.
 */
class AgroSchemaContractTest {
    private val schema: String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("agro-schema.graphql")) {
            "agro-schema.graphql is missing from the test resources"
        }.bufferedReader().use { it.readText() }

    private fun rootFields(type: String): Set<String> {
        val body = Regex("""type $type \{(.*?)\n\}""", RegexOption.DOT_MATCHES_ALL)
            .find(schema)?.groupValues?.get(1)
            ?: error("type $type is missing from the schema")
        return Regex("""^\s{0,4}(\w+)(?:\(|:)""", RegexOption.MULTILINE)
            .findAll(body).map { it.groupValues[1] }.toSet()
    }

    private val operation = Regex(
        """\b(query|mutation)\s+\w+\s*(?:\([^)]*\))?\s*\{\s*(\w+)""" +
            """|"(query|mutation)\s*(?:\([^)]*\))?\s*\{\s*(\w+)"""
    )

    @Test
    fun theSchemaCopyHasBothRoots() {
        assertTrue(rootFields("Query").isNotEmpty())
        assertTrue(rootFields("Mutation").isNotEmpty())
    }

    @Test
    fun everyOperationWandaSendsIsInAgrosSchema() {
        val queries = rootFields("Query")
        val mutations = rootFields("Mutation")
        val unknown = mutableListOf<String>()
        var checked = 0

        File("src/main/java").walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            operation.findAll(file.readText()).forEach { match ->
                val kind = match.groupValues[1].ifEmpty { match.groupValues[3] }
                val field = match.groupValues[2].ifEmpty { match.groupValues[4] }
                checked++
                val known = if (kind == "query") queries else mutations
                if (field !in known) unknown += "${file.name}: $kind $field"
            }
        }

        assertTrue("no GraphQL operations were found; the scan is broken", checked > 0)
        assertTrue("operations missing from Agro's schema: $unknown", unknown.isEmpty())
    }
}
