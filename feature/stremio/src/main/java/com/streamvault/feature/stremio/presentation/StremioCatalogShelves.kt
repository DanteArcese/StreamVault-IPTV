package com.streamvault.feature.stremio.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streamvault.core.ui.components.shell.AppSectionHeader
import com.streamvault.core.ui.design.BrowseColors as AppColors
import com.streamvault.core.ui.design.LocalAppSpacing
import com.streamvault.core.ui.interaction.TvButton
import com.streamvault.domain.model.stremio.StremioMediaType
import com.streamvault.domain.model.stremio.StremioMetaPreview
import com.streamvault.feature.stremio.R

/**
 * Stremio add-on catalogs of one media type, shown as shelves in the Movies or Series tab (issue #326).
 * App composition injects this into the Catalog feature, so Catalog never depends on Stremio.
 * Renders nothing when Stremio content is hidden or no enabled catalog of [type] can be browsed.
 */
@Composable
fun StremioCatalogShelves(
    type: StremioMediaType,
    onDetails: (Long, StremioMetaPreview) -> Unit,
    visibility: StremioVisibilityViewModel = hiltViewModel(),
    viewModel: StremioCatalogBrowseViewModel = hiltViewModel()
) {
    val access by visibility.state.collectAsStateWithLifecycle()
    if (!access.canAccessContent) return
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rows = remember(state.rows, type) {
        StremioShelfSelection.shelfRows(state.rows, type).filter(StremioShelfSelection::isVisibleShelf)
    }
    if (rows.isEmpty()) return
    StremioShelfSection {
        rows.forEach { row ->
            key(row.key) {
                LaunchedEffect(row.key, row.addonFingerprint) { viewModel.ensureLoaded(row.key) }
                StremioShelfRow(
                    row = row,
                    metas = row.metas,
                    onRetry = { viewModel.retry(row.key) },
                    onDetails = onDetails
                )
            }
        }
    }
}

/**
 * Stremio add-on search results for the global Search screen (issue #326). Only catalogs that
 * declare search support are queried, and only results for [query] itself are shown.
 */
@Composable
fun StremioSearchResults(
    query: String,
    includeMovies: Boolean,
    includeSeries: Boolean,
    onDetails: (Long, StremioMetaPreview) -> Unit,
    visibility: StremioVisibilityViewModel = hiltViewModel(),
    viewModel: StremioCatalogBrowseViewModel = hiltViewModel()
) {
    val access by visibility.state.collectAsStateWithLifecycle()
    if (!access.canAccessContent) return
    val trimmed = query.trim()
    if (trimmed.length < MIN_SEARCH_LENGTH) return
    val types = remember(includeMovies, includeSeries) {
        buildSet {
            if (includeMovies) add(StremioMediaType.MOVIE)
            if (includeSeries) add(StremioMediaType.SERIES)
        }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val searchRows = remember(state.rows, types) { StremioShelfSelection.searchRows(state.rows, types) }
    val searchKeys = remember(searchRows) { searchRows.map { it.key } }
    LaunchedEffect(trimmed, searchKeys) { viewModel.searchCatalogs(searchKeys, trimmed) }
    val withResults = searchRows.filter { StremioShelfSelection.hasResultsFor(it, trimmed) }
    val searching = searchRows.any { it.loading }
    if (withResults.isEmpty() && !searching) return
    StremioShelfSection {
        if (withResults.isEmpty()) StremioShelfLoading()
        withResults.forEach { row ->
            key(row.key) {
                StremioShelfRow(row = row, metas = row.metas, onRetry = { viewModel.retry(row.key) }, onDetails = onDetails)
            }
        }
    }
}

@Composable
private fun StremioShelfSection(content: @Composable () -> Unit) {
    val spacing = LocalAppSpacing.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.md, vertical = spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        Text(
            text = stringResource(R.string.stremio_title),
            style = MaterialTheme.typography.titleLarge,
            color = AppColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        content()
    }
}

@Composable
private fun StremioShelfRow(
    row: StremioCatalogRowState,
    metas: List<StremioMetaPreview>,
    onRetry: () -> Unit,
    onDetails: (Long, StremioMetaPreview) -> Unit
) {
    val spacing = LocalAppSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        // The add-on name is a subtitle, so rows from different add-ons with the same catalog name stay distinct.
        AppSectionHeader(title = catalogLabel(row, showAddon = false), subtitle = row.addonName)
        when {
            metas.isEmpty() && row.error != null -> Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = row.error,
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                TvButton(onClick = onRetry) { Text(stringResource(R.string.stremio_retry)) }
            }
            metas.isEmpty() -> StremioShelfLoading()
            else -> LazyRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.cardGap),
                contentPadding = PaddingValues(vertical = spacing.xs)
            ) {
                items(metas.take(StremioShelfSelection.MAX_ITEMS_PER_ROW), key = { "${it.type.typeName}:${it.id}" }) { meta ->
                    val focusRequester = remember(meta.id) { FocusRequester() }
                    Box(Modifier.width(shelfCardWidth(meta))) {
                        CatalogMetaCard(
                            meta = meta,
                            focusRequester = focusRequester,
                            onFocused = {},
                            onClick = { onDetails(row.key.addonId, meta) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StremioShelfLoading() {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        CircularProgressIndicator(color = AppColors.Brand, modifier = Modifier.size(32.dp))
    }
}

/** Card widths match the minimum cell sizes of the Extensions catalog grid. */
private fun shelfCardWidth(meta: StremioMetaPreview): Dp = when (meta.posterShape) {
    "landscape" -> 260.dp
    "square" -> 180.dp
    else -> 132.dp
}

private const val MIN_SEARCH_LENGTH = 2
