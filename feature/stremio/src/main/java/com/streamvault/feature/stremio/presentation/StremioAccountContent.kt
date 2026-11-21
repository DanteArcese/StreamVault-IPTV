package com.streamvault.feature.stremio.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streamvault.core.ui.components.PhoneAddQrPanel
import com.streamvault.core.ui.design.BrowseColors as AppColors
import com.streamvault.core.ui.design.LocalAppSpacing
import com.streamvault.core.ui.interaction.TvButton
import com.streamvault.core.ui.interaction.TvPrimaryButton
import com.streamvault.domain.model.stremio.StremioAccount
import com.streamvault.domain.model.stremio.StremioAccountError
import com.streamvault.domain.model.stremio.StremioAccountLimits
import com.streamvault.feature.stremio.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@HiltViewModel
class StremioAccountViewModel @Inject constructor(private val account: StremioAccount): ViewModel() {
    val state=account.state
    init { account.start(); viewModelScope.launch { account.load() } }
    fun connect()=action { account.beginLink() }
    fun cancel()=action { account.cancelLink() }
    fun disconnect()=action { account.disconnect() }
    fun sync()=action { account.sync() }
    suspend fun poll() { account.pollLink() }
    private fun action(block:suspend () -> Any?) { viewModelScope.launch {
        try { block() } catch(cancel:CancellationException) { throw cancel }
    } }
}

@Composable
internal fun StremioAccountContent(onBack:()->Unit,viewModel:StremioAccountViewModel=hiltViewModel()) {
    androidx.activity.compose.BackHandler(onBack=onBack)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uri=LocalUriHandler.current
    LaunchedEffect(state.linkCode) {
        if(state.linkCode!=null) while(true) { delay(5_000); viewModel.poll() }
    }
    val spacing=LocalAppSpacing.current
    // The primary action (Sync now / Connect) takes initial focus; the back action stays secondary.
    val primaryFocus=remember { FocusRequester() }
    val primaryVisible=state.ready && state.linkUrl==null
    LaunchedEffect(primaryVisible,state.connected) {
        if(primaryVisible) runCatching { primaryFocus.requestFocus() }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical=spacing.xs),verticalArrangement=Arrangement.spacedBy(spacing.xs)) {
        TvButton(onClick=onBack) { Text(stringResource(R.string.stremio_manage_addons)) }
        Text(stringResource(R.string.stremio_account),style=MaterialTheme.typography.titleMedium,color=AppColors.TextPrimary)
        if(state.connected) {
            Text(state.displayName.orEmpty(),style=MaterialTheme.typography.bodyLarge,color=AppColors.TextSecondary,
                maxLines=1,overflow=TextOverflow.Ellipsis)
            Row(horizontalArrangement=Arrangement.spacedBy(spacing.xs)) {
                TvPrimaryButton(onClick=viewModel::sync,enabled=!state.busy,modifier=Modifier.focusRequester(primaryFocus)) {
                    Text(stringResource(R.string.stremio_sync_now))
                }
                TvButton(onClick=viewModel::disconnect) { Text(stringResource(R.string.stremio_disconnect)) }
            }
        } else if(state.linkUrl!=null) {
            PhoneAddQrPanel(url=state.linkUrl,title=stringResource(R.string.stremio_account_link),
                description=stringResource(R.string.stremio_account_link),status=state.linkCode,
                startLabel=stringResource(R.string.stremio_connect),stopLabel=stringResource(R.string.stremio_cancel),
                onStart={ state.linkUrl?.let { runCatching { uri.openUri(it) } } },onStop=viewModel::cancel)
        } else {
            Text(stringResource(R.string.stremio_account_sync_description),style=MaterialTheme.typography.bodyLarge,color=AppColors.TextSecondary)
            TvPrimaryButton(onClick=viewModel::connect,enabled=state.ready && !state.busy,modifier=Modifier.focusRequester(primaryFocus)) {
                Text(stringResource(R.string.stremio_connect))
            }
        }
        if(state.busy) Text(stringResource(R.string.stremio_syncing),style=MaterialTheme.typography.bodyLarge,color=AppColors.TextSecondary)
        state.error?.let { error -> Text(stremioAccountErrorText(error),style=MaterialTheme.typography.bodyLarge,color=AppColors.TextPrimary) }
        if(state.connected && !state.busy && state.error==null && state.skippedItems>0) {
            Text(stringResource(R.string.stremio_sync_skipped_items,state.skippedItems),
                style=MaterialTheme.typography.bodyLarge,color=AppColors.TextSecondary)
        }
        if(state.error==StremioAccountError.STORAGE) {
            TvButton(onClick=viewModel::cancel) { Text(stringResource(R.string.stremio_cancel)) }
        }
        StremioStreamingServiceContent()
    }
}

@Composable
private fun stremioAccountErrorText(error: StremioAccountError): String = when(error) {
    StremioAccountError.EXPIRED -> stringResource(R.string.stremio_link_expired)
    StremioAccountError.INCOGNITO -> stringResource(R.string.stremio_sync_incognito)
    StremioAccountError.SESSION_EXPIRED -> stringResource(R.string.stremio_error_sign_in_expired)
    StremioAccountError.LIBRARY_TOO_LARGE -> stringResource(R.string.stremio_error_library_too_large,StremioAccountLimits.MAX_LIBRARY_ITEMS)
    StremioAccountError.NETWORK, StremioAccountError.SIGN_IN, StremioAccountError.STORAGE, StremioAccountError.SYNC -> stringResource(R.string.stremio_error_generic)
}
