package com.vnventory.app.ui.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import com.vnventory.app.domain.model.OwnedCopy
import com.vnventory.app.domain.model.ReleaseInfo
import com.vnventory.app.domain.model.TitleDisplayMode
import com.vnventory.app.domain.model.VnInfo

/** Display-only preference, shared by every screen and dialog. Never stored in a snapshot. */
val LocalTitleDisplayMode = compositionLocalOf { TitleDisplayMode.ORIGINAL }

@Composable fun VnInfo.uiTitle(): String = displayTitle(LocalTitleDisplayMode.current)
@Composable fun VnInfo.uiSecondaryTitle(): String? = secondaryTitle(LocalTitleDisplayMode.current)
@Composable fun ReleaseInfo.uiTitle(): String = displayTitle(LocalTitleDisplayMode.current)
@Composable fun OwnedCopy.uiTitle(): String = displayTitle(LocalTitleDisplayMode.current)
@Composable fun OwnedCopy.uiReleaseName(): String = displayReleaseName(LocalTitleDisplayMode.current).localized()
