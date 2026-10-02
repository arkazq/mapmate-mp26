package com.mapmate.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
fun ScreenLifecycleEffect(onActiveChanged: (Boolean) -> Unit) {
    val owner = LocalLifecycleOwner.current
    val currentCallback = rememberUpdatedState(onActiveChanged)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            currentCallback.value(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        }
        owner.lifecycle.addObserver(observer)
        currentCallback.value(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose {
            owner.lifecycle.removeObserver(observer)
            currentCallback.value(false)
        }
    }
}
