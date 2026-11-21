package com.streamvault.feature.stremio.presentation

import com.streamvault.domain.model.stremio.StremioMediaType

/**
 * Chooses which add-on catalog rows appear outside the Extensions tab: as shelves in the
 * Movies/Series tabs and as result rows in global Search (issue #326).
 */
internal object StremioShelfSelection {
    /** Bounds the requests a tab visit can start; users pick catalogs per add-on in Extensions. */
    const val MAX_SHELF_ROWS = 12
    const val MAX_SEARCH_ROWS = 12
    const val MAX_ITEMS_PER_ROW = 30

    /** Catalogs of [type] that can be shown without the user supplying a search, genre or extra value. */
    fun shelfRows(rows: List<StremioCatalogRowState>, type: StremioMediaType): List<StremioCatalogRowState> =
        rows.filter { it.catalog.type == type && it.isUsable() && it.browsableWithoutInput() }
            .take(MAX_SHELF_ROWS)

    /**
     * Search-capable catalogs of [types]. When an add-on has a dedicated search catalog for a type
     * (search is required), its browse catalogs that merely accept a search are left out so the
     * same results are not listed twice.
     */
    fun searchRows(rows: List<StremioCatalogRowState>, types: Set<StremioMediaType>): List<StremioCatalogRowState> {
        val candidates = rows.filter { it.catalog.type in types && it.supportsSearch && it.isUsable() }
        val dedicated = candidates.filter { it.searchRequired }
            .mapTo(mutableSetOf()) { it.key.addonId to it.catalog.type }
        return candidates.filter { it.searchRequired || (it.key.addonId to it.catalog.type) !in dedicated }
            .take(MAX_SEARCH_ROWS)
    }

    /** Search rows only show results for the query they were loaded with, never stale or default content. */
    fun hasResultsFor(row: StremioCatalogRowState, query: String): Boolean =
        row.loadedSearch == query && row.metas.isNotEmpty()

    /** A shelf that loaded and came back empty is hidden instead of showing an empty row. */
    fun isVisibleShelf(row: StremioCatalogRowState): Boolean =
        !(row.pageCount > 0 && row.metas.isEmpty() && row.error == null && !row.loading)

    private fun StremioCatalogRowState.isUsable(): Boolean =
        blockedReason == null && !isLocked && !selectedGenreLocked

    private fun StremioCatalogRowState.browsableWithoutInput(): Boolean =
        !searchRequired && !genreRequired && customExtras.none { it.isRequired }
}
