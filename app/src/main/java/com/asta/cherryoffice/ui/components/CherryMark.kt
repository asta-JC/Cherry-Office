package com.asta.cherryoffice.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.asta.cherryoffice.R

/** Full shared identity, including the surrounding app symbols, on a transparent backing. */
@Composable
fun CherryMark(modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    Image(
        painter = painterResource(if (dark) R.drawable.cherryapps_logo_full_dark else R.drawable.cherryapps_logo_full),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(32.dp).padding(2.dp)
    )
}
