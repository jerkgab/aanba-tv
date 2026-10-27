package pro.aanba.tv;

import android.content.Intent;
import android.graphics.Bitmap;
import android.test.ActivityInstrumentationTestCase2;
import android.view.KeyEvent;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Real-device opt-in integration test: official public VK controls, no stream URL extraction. */
@SuppressWarnings("deprecation")
public class VkPlayerTvTest extends ActivityInstrumentationTestCase2<MainActivity>{
 MainActivity a;
 public VkPlayerTvTest(){super(MainActivity.class);}
 void ui(Runnable r){a.runOnUiThread(r);getInstrumentation().waitForIdleSync();}
 String js(String s)throws Exception{CountDownLatch l=new CountDownLatch(1);String[] value={null};a.runOnUiThread(()->a.web.evaluateJavascript(s,v->{value[0]=v;l.countDown();}));assertTrue(l.await(8,TimeUnit.SECONDS));return value[0];}
 void key(int k){getInstrumentation().sendKeyDownUpSync(k);getInstrumentation().waitForIdleSync();Thread.yield();}
 String label()throws Exception{return new JSONArray("["+js("window.AanbaTv.focusedLabel()")+"]").getString(0);}
 JSONObject state()throws Exception{return new JSONObject(js("window.AanbaTv?window.AanbaTv.state():{}"));}
 void waitLabel(String fragment)throws Exception{long end=System.currentTimeMillis()+6000;while(System.currentTimeMillis()<end){if(label().contains(fragment))return;Thread.sleep(100);}fail("Expected VK focus: "+fragment+"; got "+label());}
 void chooseDown(String fragment)throws Exception{for(int i=0;i<16;i++){if(label().contains(fragment))return;key(KeyEvent.KEYCODE_DPAD_DOWN);Thread.sleep(90);}fail("VK option missing: "+fragment+"; got "+label());}
 void capture(String name)throws Exception{Thread.sleep(450);Bitmap bitmap=getInstrumentation().getUiAutomation().takeScreenshot();File d=new File(a.getExternalFilesDir(null),"qa13");d.mkdirs();try(FileOutputStream f=new FileOutputStream(new File(d,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,f);}bitmap.recycle();}
 void openSettings()throws Exception{key(KeyEvent.KEYCODE_MENU);key(KeyEvent.KEYCODE_DPAD_CENTER);waitLabel("Качество");assertTrue(a.vkNavigation);}
 public void testRealVkSettingsQualitySeekAndStatistics()throws Exception{
  Intent i=new Intent();i.putExtra("qa_mode",true);setActivityIntent(i);a=getActivity();getInstrumentation().waitForIdleSync();
  List<Match> matches=ScheduleParser.parse(Network.get("https://aanba.pro/schedule"));Match selected=null;for(Match m:matches)if("post".equals(m.state(null,System.currentTimeMillis()))&&m.league.equals("NBA")){selected=m;break;}assertNotNull(selected);Match match=selected;ui(()->a.openMatch(match,false));
  long end=System.currentTimeMillis()+40000;JSONObject initial=null;while(System.currentTimeMillis()<end){try{initial=state();if(initial.optBoolean("ready")&&initial.optBoolean("canSeek")&&a.controls.position.enabled)break;}catch(Exception ignored){}Thread.sleep(300);}File diagnostic=new File(a.getExternalFilesDir(null),"qa13");diagnostic.mkdirs();try(FileWriter f=new FileWriter(new File(diagnostic,"load-state.json"))){f.write(js("JSON.stringify({url:location.origin+location.pathname,title:document.title,bridge:!!window.AanbaTv,state:window.AanbaTv?window.AanbaTv.state():null,text:document.body.innerText.slice(0,700)})"));}capture("load-state");assertNotNull(initial);assertTrue("Actual VK video must load",initial.optBoolean("ready"));assertTrue(initial.optBoolean("canSeek"));
  String token=js("window.__qaToken=String(Math.random());window.__qaToken");openSettings();String original=label();capture("vk-settings");key(KeyEvent.KEYCODE_DPAD_CENTER);waitLabel("Вернуться");chooseDown("Другое");key(KeyEvent.KEYCODE_DPAD_CENTER);Thread.sleep(300);capture("vk-quality-options");chooseDown("1080p");String chosen=label();key(KeyEvent.KEYCODE_DPAD_CENTER);Thread.sleep(1500);
  openSettings();String applied=label();assertTrue("VK must confirm actual quality selection: "+applied,applied.contains("1080"));capture("vk-quality-selected");
  key(KeyEvent.KEYCODE_DPAD_CENTER);waitLabel("Вернуться");chooseDown("Автоматически");key(KeyEvent.KEYCODE_DPAD_CENTER);Thread.sleep(800);key(KeyEvent.KEYCODE_BACK);
  // Check the native timeline against the actual media element without reloading its page.
  JSONObject before=state();double time=before.getDouble("time");key(KeyEvent.KEYCODE_DPAD_RIGHT);Thread.sleep(1200);JSONObject sought=state();assertTrue("Real recording seeks forward",sought.getDouble("time")>=Math.min(before.getDouble("end")-1,time+7));assertEquals(MainActivity.HIDDEN,a.mode);key(KeyEvent.KEYCODE_BACK);
  long hold=android.os.SystemClock.uptimeMillis();getInstrumentation().sendKeySync(new KeyEvent(hold,hold,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_UP,0));Thread.sleep(750);getInstrumentation().sendKeySync(new KeyEvent(hold,android.os.SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_UP,0));getInstrumentation().waitForIdleSync();assertEquals(MainActivity.PANEL,a.mode);assertEquals(1,a.loadCount);assertEquals(token,js("window.__qaToken"));double first=state().getDouble("time");Thread.sleep(1800);assertTrue("Real VK video keeps playing beside stats",state().getDouble("time")>first);capture("vk-statistics");
  File dir=new File(a.getExternalFilesDir(null),"qa13");dir.mkdirs();try(FileWriter f=new FileWriter(new File(dir,"vk-verification.json"))){f.write(new JSONObject().put("original",original).put("chosen",chosen).put("applied",applied).put("initial",initial).put("afterSeek",sought).put("loadCount",a.loadCount).toString());}
 }
}
