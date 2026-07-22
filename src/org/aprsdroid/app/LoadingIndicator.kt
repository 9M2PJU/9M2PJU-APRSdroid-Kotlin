package org.aprsdroid.app

/**
 * Kotlin port of the Scala `LoadingIndicator` trait.
 *
 * In the Compose rewrite, loading state is handled by Compose's
 * state system (e.g. a `MutableState<Boolean>`), but this interface
 * is kept for compatibility with any code that still uses the
 * callback-based pattern.
 */
interface LoadingIndicator {
    fun onStartLoading()
    fun onStopLoading()
}
