package top.redlnn.spectra.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import top.redlnn.spectra.R

@Composable
fun SpectraLogo(modifier: Modifier = Modifier) {
    Box(modifier.background(colorResource(R.color.spectra_icon_background))) {
        Image(
            painterResource(R.drawable.spectra_icon),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
