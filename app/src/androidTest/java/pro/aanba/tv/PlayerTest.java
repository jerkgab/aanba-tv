package pro.aanba.tv;

import android.content.Intent;
import android.graphics.Bitmap;
import android.test.ActivityInstrumentationTestCase2;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;

@SuppressWarnings("deprecation")
public class PlayerTest extends ActivityInstrumentationTestCase2<MainActivity> {
    public PlayerTest(){super(MainActivity.class);}
    MainActivity app;
    @Override protected void setUp()throws Exception{super.setUp();Intent i=new Intent();i.putExtra("qa_mode",true);setActivityIntent(i);app=getActivity();getInstrumentation().waitForIdleSync();if(app.watching==null)onUi(()->app.startDemo());}
    void onUi(Runnable r){app.runOnUiThread(r);getInstrumentation().waitForIdleSync();}
    String js(String script)throws Exception{
        CountDownLatch latch=new CountDownLatch(1);String[] value={null};app.runOnUiThread(()->app.web.evaluateJavascript(script,r->{value[0]=r;latch.countDown();}));assertTrue(latch.await(10,TimeUnit.SECONDS));return value[0];
    }
    double videoTime()throws Exception{return Double.parseDouble(js("document.querySelector('video').currentTime"));}
    void waitForPlayback()throws Exception{
        long end=System.currentTimeMillis()+25000;while(System.currentTimeMillis()<end){try{if(videoTime()>0.25)return;}catch(Exception ignored){}Thread.sleep(250);}fail("Local HTML5 video did not start");
    }
    void capture(String name)throws Exception{
        Thread.sleep(400);getInstrumentation().waitForIdleSync();
        Bitmap image=getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(image);
        File dir=new File(app.getExternalFilesDir(null),"qa");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
    }

    void waitForController()throws Exception{waitForPlayback();long end=System.currentTimeMillis()+5000;while(!app.controls.position.enabled&&System.currentTimeMillis()<end)Thread.sleep(100);assertTrue("Timeline state: "+js("window.AanbaTv?JSON.stringify(window.AanbaTv.state()):location.href"),app.controls.position.enabled);}
    public void testVideoSurvivesPanelAndResize()throws Exception{
        waitForController();String token=js("window.__qaToken=String(Math.random());window.__qaToken");onUi(()->app.setMode(MainActivity.PANEL));assertEquals(View.VISIBLE,app.sidebar.getVisibility());capture("panel");double a=videoTime();Thread.sleep(700);assertTrue(videoTime()>a||a>10);assertEquals(1,app.loadCount);assertEquals(token,js("window.__qaToken"));assertEquals(16d/9,(double)app.videoHost.getWidth()/app.videoHost.getHeight(),.01);onUi(()->app.setMode(MainActivity.HIDDEN));assertEquals(app.root.getWidth(),app.videoHost.getWidth());
    }
    public void testUpHoldAndDoubleBack()throws Exception{
        waitForController();long now=android.os.SystemClock.uptimeMillis();getInstrumentation().sendKeySync(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_UP,0));Thread.sleep(750);getInstrumentation().sendKeySync(new KeyEvent(now,android.os.SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_UP,0));getInstrumentation().waitForIdleSync();assertEquals(MainActivity.PANEL,app.mode);assertFalse(app.controlsVisible);getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);getInstrumentation().waitForIdleSync();assertEquals(MainActivity.HIDDEN,app.mode);assertNotNull(app.watching);getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);getInstrumentation().waitForIdleSync();assertNull(app.watching);
    }
    public void testPlayerRequestedFullscreenKeepsStatsAndVideo()throws Exception{
        waitForPlayback();
        js("document.querySelector('video').onclick=function(){this.requestFullscreen();};true");
        int[] position=new int[2];onUi(()->app.web.getLocationOnScreen(position));
        long tap=android.os.SystemClock.uptimeMillis();
        getInstrumentation().sendPointerSync(MotionEvent.obtain(tap,tap,MotionEvent.ACTION_DOWN,position[0]+app.web.getWidth()/2f,position[1]+app.web.getHeight()/2f,0));
        getInstrumentation().sendPointerSync(MotionEvent.obtain(tap,tap+60,MotionEvent.ACTION_UP,position[0]+app.web.getWidth()/2f,position[1]+app.web.getHeight()/2f,0));
        long until=System.currentTimeMillis()+10000;
        while(app.customVideo==null&&System.currentTimeMillis()<until){Thread.sleep(100);getInstrumentation().waitForIdleSync();}
        assertNotNull("Player's fullscreen request must stay inside the Activity",app.customVideo);
        assertEquals("true",js("!!document.fullscreenElement"));
        onUi(()->{app.setMode(MainActivity.PANEL);app.showControls(false);if(app.visibleToast!=null)app.visibleToast.cancel();});
        assertSame(app.videoHost,app.customVideo.getParent());
        assertEquals(16.0/9.0,(double)app.videoHost.getWidth()/app.videoHost.getHeight(),0.01);
        double first=videoTime();Thread.sleep(650);double next=videoTime();assertTrue("Fullscreen video advances alongside panel",next>first||first>11);
        assertEquals(1,app.loadCount);capture("fullscreen-panel");
        onUi(()->app.exitCustomVideo());Thread.sleep(400);getInstrumentation().waitForIdleSync();
        assertNull(app.customVideo);assertEquals(View.VISIBLE,app.web.getVisibility());assertEquals(1,app.loadCount);
        assertEquals("false",js("document.querySelector('video').paused"));
    }
    public void testMediaPauseAndResume()throws Exception{
        waitForController();getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PAUSE);Thread.sleep(400);assertEquals("true",js("document.querySelector('video').paused"));
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PLAY);Thread.sleep(500);assertEquals("false",js("document.querySelector('video').paused"));
    }

    public void testTimelineAndVirtualSettingsButton()throws Exception{
        waitForController();getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);getInstrumentation().waitForIdleSync();assertTrue(app.controlsVisible);assertSame(app.controls.seek,app.getCurrentFocus());
        long at=android.os.SystemClock.uptimeMillis();getInstrumentation().sendKeySync(new KeyEvent(at,at,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT,0));getInstrumentation().sendKeySync(new KeyEvent(at,at+500,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT,5));Thread.sleep(700);getInstrumentation().sendKeySync(new KeyEvent(at,android.os.SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_RIGHT,0));getInstrumentation().waitForIdleSync();assertEquals(MainActivity.HIDDEN,app.mode);assertEquals(1,app.loadCount);
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);getInstrumentation().waitForIdleSync();assertSame(app.controls.play,app.getCurrentFocus());getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);getInstrumentation().waitForIdleSync();assertSame(app.controls.restart,app.getCurrentFocus());getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);getInstrumentation().waitForIdleSync();assertSame(app.controls.sound,app.getCurrentFocus());getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);getInstrumentation().waitForIdleSync();assertSame(app.controls.settings,app.getCurrentFocus());capture("native-controls");getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);getInstrumentation().waitForIdleSync();assertFalse(app.controlsVisible);assertNotNull(app.watching);
    }
    String allText(View v){StringBuilder b=new StringBuilder();if(v instanceof TextView)b.append(((TextView)v).getText());if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)b.append(allText(((ViewGroup)v).getChildAt(i)));return b.toString();}
}
