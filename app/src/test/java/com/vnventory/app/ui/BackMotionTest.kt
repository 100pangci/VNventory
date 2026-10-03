package com.vnventory.app.ui

import com.vnventory.app.ui.theme.ShelfMotion
import org.junit.Assert.*
import org.junit.Test

class BackMotionTest {
    @Test fun `返回与界面收尾均短于进入动画`() {
        assertEquals(180, ShelfMotion.Back)
        assertTrue(ShelfMotion.Back < ShelfMotion.Navigation)
        assertTrue(ShelfMotion.BackRecovery < ShelfMotion.Back)
        assertTrue(ShelfMotion.Chrome < ShelfMotion.Back)
        assertTrue(ShelfMotion.BackEasing.transform(.25f) > .5f)
    }

    @Test fun `松手完成时长随剩余进度缩短并限制在合法区间`() {
        assertEquals(180, ShelfMotion.backFinishDuration(0f))
        assertEquals(90, ShelfMotion.backFinishDuration(.5f))
        assertEquals(36, ShelfMotion.backFinishDuration(.8f))
        assertEquals(0, ShelfMotion.backFinishDuration(1f))
        assertEquals(180, ShelfMotion.backFinishDuration(-1f))
        assertEquals(0, ShelfMotion.backFinishDuration(2f))
    }
}
