package pro.aanba.tv;

import org.junit.Test;
import static org.junit.Assert.*;

public class RemoteGesturesTest {
    @Test public void shortUpIsNotStatistics(){
        RemoteGestures g=new RemoteGestures();g.upDown(1000);
        assertFalse(g.upHeld(1200));assertEquals(RemoteGestures.STEP,g.upUp(1200));
    }
    @Test public void heldUpOpensExactlyOnceAndDoesNotSeekOnRelease(){
        RemoteGestures g=new RemoteGestures();g.upDown(1000);
        assertTrue(g.upHeld(1000+RemoteGestures.HOLD_MS));assertFalse(g.upHeld(3000));
        assertEquals(RemoteGestures.NONE,g.upUp(3100));
    }
    @Test public void keyRepeatsDoNotRestartHoldTimer(){
        RemoteGestures g=new RemoteGestures();g.upDown(1000);g.upDown(1300);
        assertTrue(g.upHeld(1000+RemoteGestures.HOLD_MS));
    }
    @Test public void releaseStillRecognisesHoldIfUiWasBusy(){
        RemoteGestures g=new RemoteGestures();g.upDown(1000);
        assertEquals(RemoteGestures.PANEL,g.upUp(1700));
    }
    @Test public void cancelCannotOpenStatisticsLater(){
        RemoteGestures g=new RemoteGestures();g.upDown(1000);g.cancelUp();
        assertFalse(g.upHeld(3000));assertEquals(RemoteGestures.NONE,g.upUp(3000));
    }
    @Test public void onlyQuickConsecutiveBackExits(){
        RemoteGestures g=new RemoteGestures();assertFalse(g.back(1000));
        assertTrue(g.back(1500));assertFalse(g.back(1800));
        assertFalse(g.back(5000));assertTrue(g.back(5200));
    }
    @Test public void otherInputCancelsDoubleBack(){
        RemoteGestures g=new RemoteGestures();assertFalse(g.back(1000));g.resetBack();
        assertFalse(g.back(1200));
    }
}
