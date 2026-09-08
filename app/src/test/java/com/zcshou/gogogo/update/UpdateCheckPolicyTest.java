package com.acooldog.toolbox.update;

import org.junit.Test;
import static org.junit.Assert.*;

public class UpdateCheckPolicyTest {
    @Test public void firstLaunchChecks() {
        assertTrue(UpdateCheckPolicy.shouldCheck(1000L, 0L));
    }
    @Test public void recentSuccessfulCheckIsThrottled() {
        assertFalse(UpdateCheckPolicy.shouldCheck(2000L, 1000L));
    }
    @Test public void nextDayChecksAgainWithoutAnAppUpgrade() {
        assertTrue(UpdateCheckPolicy.shouldCheck(86401000L, 1000L));
    }
    @Test public void clockRollbackDoesNotBlockUpdatesIndefinitely() {
        assertTrue(UpdateCheckPolicy.shouldCheck(500L, 1000L));
    }
}
