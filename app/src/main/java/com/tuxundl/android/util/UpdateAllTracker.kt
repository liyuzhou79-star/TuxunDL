package com.tuxundl.android.util

import java.util.UUID

/**
 * "Update all profiles" batch tracker.
 *
 * Written for the profiles screen in 1.3.5 and extracted in 1.4.1 so
 * the home screen's long-press quick menu can run the very same
 * batch: while a batch is running, the per-profile update callbacks
 * feed this tracker instead of popping an individual snackbar for
 * every profile; when the last one lands (including the display-name
 * lookups for the failures), [onFinished] fires exactly once with
 * the success count and the failed profiles' names.
 *
 * Not thread-safe by design: it is only ever touched from the main
 * thread (broadcast callbacks and the activities' event loops).
 */
class UpdateAllTracker(
    private val onFinished: (succeeded: Int, failedNames: List<String>) -> Unit,
) {
    private var pending: MutableSet<UUID>? = null
    private var succeeded = 0
    private val failedNames = mutableListOf<String>()

    /** Failed batch members whose display name is still being looked up. */
    private var nameLookups = 0

    /** True while a batch is running. */
    val isActive: Boolean
        get() = pending != null

    /**
     * Start tracking a batch over [targets]. An empty target list
     * leaves the tracker idle: no summary will follow.
     */
    fun begin(targets: Collection<UUID>) {
        if (targets.isEmpty()) {
            pending = null
            return
        }

        pending = targets.toMutableSet()
        succeeded = 0
        failedNames.clear()
        nameLookups = 0
    }

    /** Give up on the current batch (firing the updates failed midway). */
    fun abandon() {
        pending = null
    }

    /** Feed a successful update; true when [uuid] was a batch member. */
    fun onCompleted(uuid: UUID): Boolean {
        if (pending?.remove(uuid) != true) return false

        succeeded++
        finishIfDone()
        return true
    }

    /**
     * Feed a failed update; true when [uuid] was a batch member. The
     * caller then looks the display name up and reports it through
     * [onFailureNameResolved].
     */
    fun onFailed(uuid: UUID): Boolean {
        if (pending?.remove(uuid) != true) return false

        nameLookups++
        return true
    }

    /** Pair with [onFailed]: record the looked-up name (uuid when unknown). */
    fun onFailureNameResolved(uuid: UUID, name: String?) {
        failedNames += name ?: uuid.toString()
        nameLookups--
        finishIfDone()
    }

    private fun finishIfDone() {
        val current = pending ?: return
        if (current.isNotEmpty() || nameLookups > 0) return

        pending = null
        val done = succeeded
        val failed = failedNames.toList()
        succeeded = 0
        failedNames.clear()

        onFinished(done, failed)
    }
}
