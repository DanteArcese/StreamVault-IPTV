package com.streamvault.feature.stremio.presentation

import com.google.common.truth.Truth.assertThat
import com.streamvault.domain.model.stremio.StremioCatalogExtraDef
import com.streamvault.domain.model.stremio.StremioCatalogRef
import com.streamvault.domain.model.stremio.StremioMediaType
import com.streamvault.domain.model.stremio.StremioMetaPreview
import org.junit.Test

class StremioShelfSelectionTest {
    @Test
    fun `movie shelves only include movie catalogs that need no user input`() {
        val rows = listOf(
            row(1, "top", StremioMediaType.MOVIE),
            row(1, "popular", StremioMediaType.SERIES),
            row(1, "search", StremioMediaType.MOVIE, extras = listOf(StremioCatalogExtraDef("search", isRequired = true))),
            row(1, "genre", StremioMediaType.MOVIE, extras = listOf(StremioCatalogExtraDef("genre", isRequired = true))),
            row(1, "region", StremioMediaType.MOVIE, extras = listOf(StremioCatalogExtraDef("region", isRequired = true))),
            row(2, "optional", StremioMediaType.MOVIE, extras = listOf(StremioCatalogExtraDef("genre"), StremioCatalogExtraDef("search")))
        )

        assertThat(StremioShelfSelection.shelfRows(rows, StremioMediaType.MOVIE).map { it.catalog.id })
            .containsExactly("top", "optional").inOrder()
        assertThat(StremioShelfSelection.shelfRows(rows, StremioMediaType.SERIES).map { it.catalog.id })
            .containsExactly("popular")
    }

    @Test
    fun `locked blocked and hidden-genre catalogs never become shelves or search rows`() {
        val searchable = listOf(StremioCatalogExtraDef("search"))
        val rows = listOf(
            row(1, "locked", StremioMediaType.MOVIE, extras = searchable).copy(isLocked = true),
            row(1, "blocked", StremioMediaType.MOVIE, extras = searchable).copy(blockedReason = "unsupported"),
            row(1, "genre-locked", StremioMediaType.MOVIE, extras = searchable).copy(selectedGenreLocked = true)
        )

        assertThat(StremioShelfSelection.shelfRows(rows, StremioMediaType.MOVIE)).isEmpty()
        assertThat(StremioShelfSelection.searchRows(rows, setOf(StremioMediaType.MOVIE))).isEmpty()
    }

    @Test
    fun `shelf count is bounded so a tab visit cannot start unbounded catalog requests`() {
        val rows = (1..40).map { row(1, "c$it", StremioMediaType.MOVIE) }
        assertThat(StremioShelfSelection.shelfRows(rows, StremioMediaType.MOVIE))
            .hasSize(StremioShelfSelection.MAX_SHELF_ROWS)
    }

    @Test
    fun `dedicated search catalogs replace browse catalogs that merely accept a search`() {
        val optional = listOf(StremioCatalogExtraDef("search"))
        val required = listOf(StremioCatalogExtraDef("search", isRequired = true))
        val rows = listOf(
            row(1, "top", StremioMediaType.MOVIE, extras = optional),
            row(1, "search", StremioMediaType.MOVIE, extras = required),
            row(1, "top-series", StremioMediaType.SERIES, extras = optional),
            row(2, "browse", StremioMediaType.MOVIE, extras = optional),
            row(2, "no-search", StremioMediaType.MOVIE)
        )

        assertThat(StremioShelfSelection.searchRows(rows, setOf(StremioMediaType.MOVIE, StremioMediaType.SERIES)).map { it.catalog.id })
            .containsExactly("search", "top-series", "browse").inOrder()
        assertThat(StremioShelfSelection.searchRows(rows, setOf(StremioMediaType.SERIES)).map { it.catalog.id })
            .containsExactly("top-series")
    }

    @Test
    fun `search rows only show results loaded for the current query`() {
        val loaded = row(1, "search", StremioMediaType.MOVIE).copy(loadedSearch = "alien", metas = listOf(meta("tt1")))

        assertThat(StremioShelfSelection.hasResultsFor(loaded, "alien")).isTrue()
        assertThat(StremioShelfSelection.hasResultsFor(loaded, "aliens")).isFalse()
        assertThat(StremioShelfSelection.hasResultsFor(loaded.copy(loadedSearch = null), "alien")).isFalse()
        assertThat(StremioShelfSelection.hasResultsFor(loaded.copy(metas = emptyList()), "alien")).isFalse()
    }

    @Test
    fun `shelves that loaded empty are hidden while loading and failed shelves stay visible`() {
        val base = row(1, "top", StremioMediaType.MOVIE)

        assertThat(StremioShelfSelection.isVisibleShelf(base)).isTrue()
        assertThat(StremioShelfSelection.isVisibleShelf(base.copy(loading = true))).isTrue()
        assertThat(StremioShelfSelection.isVisibleShelf(base.copy(pageCount = 1))).isFalse()
        assertThat(StremioShelfSelection.isVisibleShelf(base.copy(pageCount = 1, metas = listOf(meta("tt1"))))).isTrue()
        assertThat(StremioShelfSelection.isVisibleShelf(base.copy(error = "failed"))).isTrue()
    }

    private fun row(
        addonId: Long,
        catalogId: String,
        type: StremioMediaType,
        extras: List<StremioCatalogExtraDef> = emptyList()
    ): StremioCatalogRowState {
        val catalog = StremioCatalogRef(type = type, id = catalogId, extras = extras)
        return StremioCatalogRowState(
            key = StremioCatalogRowKey(addonId, catalog.settingsKey),
            addonName = "Addon $addonId",
            addonFingerprint = "fingerprint-$addonId",
            addonUpdatedAt = 0L,
            catalog = catalog
        )
    }

    private fun meta(id: String) = StremioMetaPreview(id = id, type = StremioMediaType.MOVIE, name = id)
}
