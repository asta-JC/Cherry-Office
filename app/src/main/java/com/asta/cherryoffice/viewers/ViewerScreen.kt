package com.asta.cherryoffice.viewers

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.asta.cherryoffice.ui.viewer.ViewerViewModel

@Composable
fun ViewerScreen(
    viewModel: ViewerViewModel,
    documentUri: Uri,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    com.asta.cherryoffice.ui.viewer.ViewerScreen(
        viewModel = viewModel,
        documentUri = documentUri,
        onBackClick = onBackClick,
        modifier = modifier
    )
}
