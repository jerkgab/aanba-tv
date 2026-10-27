package pro.aanba.tv;
import org.junit.Test;
import static org.junit.Assert.*;
public class SeekModelTest {
 @Test public void recordingMovesTenSecondsAndClamps(){SeekModel s=new SeekModel();s.update(0,7200,150,true);s.step(1,0);assertEquals(160,s.draft,.01);assertEquals(160,s.finish(),.01);s.step(-1,0);assertEquals(150,s.finish(),.01);s.update(0,12,11,true);s.step(1,0);assertEquals(12,s.finish(),.01);}
 @Test public void holdingAcceleratesWithoutOpeningStatistics(){SeekModel s=new SeekModel();s.update(0,7200,100,true);s.step(1,0);s.step(1,4);s.step(1,8);assertEquals(160,s.finish(),.01);}
 @Test public void liveDvrCannotSeekOutsideItsWindow(){SeekModel s=new SeekModel();s.update(1000,1500,1480,true);s.step(1,100);assertEquals(1500,s.finish(),.01);s.step(-1,100);assertEquals(1380,s.finish(),.01);s.update(1400,1600,1550,true);assertTrue(s.draft>=1400);}
 @Test public void pollingDoesNotResetAnActivePreview(){SeekModel s=new SeekModel();s.update(0,7200,100,true);s.step(1,0);s.update(0,7200,102,true);assertEquals(110,s.draft,.01);s.cancel();assertEquals(102,s.draft,.01);}
 @Test public void unavailableOrInfiniteRangeCannotBeScrubbed(){SeekModel s=new SeekModel();s.update(0,Double.POSITIVE_INFINITY,0,true);assertFalse(s.enabled);s.step(1,10);assertFalse(s.seeking);s.update(0,100,10,false);assertFalse(s.enabled);assertEquals(0,s.progress());}
 @Test public void progressMapsSecondsIndependentlyOfScreenResolution(){SeekModel s=new SeekModel();s.update(100,200,150,true);assertEquals(5000,s.progress());s.progress(2500);assertEquals(125,s.finish(),.01);s.progress(20000);assertEquals(200,s.finish(),.01);}
 @Test public void movingDvrWindowClampsPendingPreview(){SeekModel s=new SeekModel();s.update(100,200,110,true);s.step(-1,0);s.update(120,220,180,true);assertEquals(120,s.draft,.01);assertEquals(120,s.finish(),.01);}
}
