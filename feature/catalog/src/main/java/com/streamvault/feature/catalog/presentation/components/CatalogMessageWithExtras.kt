package com.streamvault.feature.catalog.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * A centered empty-state [message] that keeps app-supplied rows (such as Stremio add-on shelves)
 * reachable below it, so a library without provider content can still show add-on catalogs.
 * With no extra rows it looks exactly like a centered message.
 */
@Composable
internal fun CatalogMessageWithExtras(
    extraContent: @Composable () -> Unit,
    message: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            message()
            extraContent()
        }
    }
}
