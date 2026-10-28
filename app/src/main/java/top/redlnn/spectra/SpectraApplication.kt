package top.redlnn.spectra

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/** One settings / analysis owner across the app's activities; only MainActivity enables input. */
class SpectraApplication : Application(), ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
    val analyzer: AnalyzerViewModel by lazy {
        ViewModelProvider(
            viewModelStore,
            ViewModelProvider.AndroidViewModelFactory.getInstance(this),
        )[AnalyzerViewModel::class.java]
    }
}
