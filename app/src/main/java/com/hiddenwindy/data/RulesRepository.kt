package com.hiddenwindy.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A single piece of rules content within a section. */
sealed interface RulesBlock {
    /** Provenance of this block: true when it came from the expansion pack. */
    val fromExpansion: Boolean

    data class Heading(val text: String, val level: Int, override val fromExpansion: Boolean) : RulesBlock
    data class Paragraph(val text: String, override val fromExpansion: Boolean) : RulesBlock
    data class Bullets(val items: List<String>, override val fromExpansion: Boolean) : RulesBlock
}

/** A navigable rules section: its own content plus any nested subsections. */
data class RulesSection(
    val id: String,
    val title: String,
    val fromExpansion: Boolean,
    val blocks: List<RulesBlock>,
    val children: List<RulesSection>,
)

/** A single search result pointing at the section that contains the match. */
data class RulesSearchHit(
    val section: RulesSection,
    /** Breadcrumb of ancestor titles, e.g. "Hiding › The Hider Deck › Curses". */
    val breadcrumb: String,
    /** A short snippet of the matching text with the query in context. */
    val snippet: String,
)

/**
 * Loads the bundled rules content (`assets/rules.json`, produced by
 * `tools/build_rules_content.py`) and provides table-of-contents access plus
 * unified keyword search across the whole rulebook.
 *
 * The parsed model is cached after the first load; parsing is cheap and happens
 * once per process.
 */
class RulesRepository(private val context: Context) {

    private var cached: RulesDocument? = null

    data class RulesDocument(
        val attribution: String,
        val sections: List<RulesSection>,
    )

    fun load(): RulesDocument {
        cached?.let { return it }
        val root = JSONObject(readAsset(ASSET_NAME))
        val attribution = root.optString("attribution")
        val sections = parseSections(root.optJSONArray("sections"))
        return RulesDocument(attribution, sections).also { cached = it }
    }

    /** Case-insensitive search across every section's title and text blocks. */
    fun search(query: String): List<RulesSearchHit> {
        val needle = query.trim()
        if (needle.length < 2) return emptyList()
        val lower = needle.lowercase()
        val hits = ArrayList<RulesSearchHit>()
        val seen = HashSet<String>()
        for (top in load().sections) {
            collectHits(top, ancestors = emptyList(), lower = lower, out = hits, seen = seen)
        }
        return hits
    }

    private fun collectHits(
        section: RulesSection,
        ancestors: List<String>,
        lower: String,
        out: MutableList<RulesSearchHit>,
        seen: MutableSet<String>,
    ) {
        val path = ancestors + section.title
        val snippet = firstMatchSnippet(section, lower)
        if (snippet != null && seen.add(section.id)) {
            out.add(
                RulesSearchHit(
                    section = section,
                    breadcrumb = path.joinToString(" \u203a "),
                    snippet = snippet,
                )
            )
        }
        for (child in section.children) {
            collectHits(child, path, lower, out, seen)
        }
    }

    /** Returns a snippet around the first match in the section, or null if none. */
    private fun firstMatchSnippet(section: RulesSection, lower: String): String? {
        if (section.title.lowercase().contains(lower)) {
            return section.title
        }
        for (block in section.blocks) {
            val text = when (block) {
                is RulesBlock.Heading -> block.text
                is RulesBlock.Paragraph -> block.text
                is RulesBlock.Bullets -> block.items.firstOrNull { it.lowercase().contains(lower) }
                    ?: block.items.joinToString(" ")
            }
            val idx = text.lowercase().indexOf(lower)
            if (idx >= 0) return snippetAround(text, idx, lower.length)
        }
        return null
    }

    private fun snippetAround(text: String, matchIndex: Int, matchLen: Int): String {
        val start = (matchIndex - SNIPPET_PAD).coerceAtLeast(0)
        val end = (matchIndex + matchLen + SNIPPET_PAD).coerceAtMost(text.length)
        val prefix = if (start > 0) "\u2026" else ""
        val suffix = if (end < text.length) "\u2026" else ""
        return prefix + text.substring(start, end).trim() + suffix
    }

    private fun parseSections(array: JSONArray?): List<RulesSection> {
        if (array == null) return emptyList()
        val result = ArrayList<RulesSection>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            result.add(
                RulesSection(
                    id = obj.optString("id"),
                    title = obj.optString("title"),
                    fromExpansion = obj.optString("source") == SOURCE_EXPANSION,
                    blocks = parseBlocks(obj.optJSONArray("blocks")),
                    children = parseSections(obj.optJSONArray("children")),
                )
            )
        }
        return result
    }

    private fun parseBlocks(array: JSONArray?): List<RulesBlock> {
        if (array == null) return emptyList()
        val result = ArrayList<RulesBlock>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val fromExpansion = obj.optString("source") == SOURCE_EXPANSION
            val block = when (obj.optString("type")) {
                "heading" -> RulesBlock.Heading(
                    text = obj.optString("text"),
                    level = obj.optInt("level", 2),
                    fromExpansion = fromExpansion,
                )
                "paragraph" -> RulesBlock.Paragraph(
                    text = obj.optString("text"),
                    fromExpansion = fromExpansion,
                )
                "bullet" -> {
                    val itemsArray = obj.optJSONArray("items")
                    val items = if (itemsArray != null) {
                        (0 until itemsArray.length()).map { itemsArray.optString(it) }
                    } else {
                        emptyList()
                    }
                    RulesBlock.Bullets(items = items, fromExpansion = fromExpansion)
                }
                else -> null
            }
            if (block != null) result.add(block)
        }
        return result
    }

    private fun readAsset(name: String): String =
        context.assets.open(name).bufferedReader().use { it.readText() }

    companion object {
        private const val ASSET_NAME = "rules.json"
        private const val SOURCE_EXPANSION = "expansion"
        private const val SNIPPET_PAD = 60
    }
}
