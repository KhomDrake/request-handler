package br.com.cosmind.request_handler.sample.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.khomdrake.request_handler.requestHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class MainViewModel @Inject constructor() : ViewModel() {

    fun abc() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                requestHandler<String>(
                    tag = "Abc"
                ) {
                    request {
                        "abc"
                    }
                    minDuration(2.seconds)
                }
                    .executeData()

            }
        }
    }

}