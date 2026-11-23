package site.kentok.lists.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import site.kentok.lists.R

/** Иконка с текстом (JPG→PNG) — вход, ПИН и остальные экраны. */
@Composable
fun BrandLogo(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp
) {
    Image(
        painter = painterResource(R.drawable.ic_brand),
        contentDescription = "Editor-Ok",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .size(size)
            .padding(bottom = 4.dp)
    )
}
