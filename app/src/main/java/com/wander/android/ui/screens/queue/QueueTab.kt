package com.wander.android.ui.screens.queue

import androidx.annotation.StringRes
import com.wander.android.R

/** The two halves of the local queue the drawer can show. */
internal enum class QueueTab(@StringRes val label: Int) {
    UP_NEXT(R.string.queue_section_up_next),
    HISTORY(R.string.queue_next_tab_history)
}
