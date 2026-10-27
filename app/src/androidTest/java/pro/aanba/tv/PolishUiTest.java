package pro.aanba.tv;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.test.ActivityInstrumentationTestCase2;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
@SuppressWarnings("deprecation")
public class PolishUiTest extends ActivityInstrumentationTestCase2<MainActivity>{
 MainActivity a;Set<String> originalFavorites;String originalHistory;
 public PolishUiTest(){super(MainActivity.class);}
 @Override protected void setUp()throws Exception{super.setUp();Intent i=new Intent();i.putExtra("qa_mode",true);setActivityIntent(i);a=getActivity();getInstrumentation().waitForIdleSync();originalFavorites=new HashSet<>(a.library.favorites);originalHistory=a.prefs.getString("watch-history","[]");}
 @Override protected void tearDown()throws Exception{ui(()->{a.resumePending=true;a.library.favorites.clear();a.library.favorites.addAll(originalFavorites);a.prefs.edit().putStringSet("favorite-teams",originalFavorites).putString("watch-history",originalHistory).commit();a.library.history.entries.clear();a.library.history.entries.putAll(new WatchHistory(originalHistory).entries);});super.tearDown();}
 void ui(Runnable r){a.runOnUiThread(r);getInstrumentation().waitForIdleSync();}
 String fixture(String file)throws Exception{try(InputStream in=getInstrumentation().getContext().getAssets().open(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);return out.toString("UTF-8");}}
 String text(View v){StringBuilder b=new StringBuilder();if(v instanceof TextView)b.append(((TextView)v).getText()).append(' ');if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)b.append(text(((ViewGroup)v).getChildAt(i)));return b.toString();}
 void capture(String name)throws Exception{Thread.sleep(450);getInstrumentation().waitForIdleSync();Bitmap b=getInstrumentation().getUiAutomation().takeScreenshot();File dir=new File(a.getExternalFilesDir(null),"qa14");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
 public void testFavoritesPersistAndYoutubeHomeCardsFit()throws Exception{
  YoutubeFeed feed=YoutubeFeed.parse(fixture("youtube-feed.xml"),1);List<Match> matches=ScheduleParser.parse(fixture("schedule-2026-10-25.html"));LeagueTable table=LeagueTable.parse(fixture("standings-div.json"),1);
  ui(()->{a.destroyPlayer();a.demo=false;a.matches.clear();a.matches.addAll(matches);a.youtube=feed;a.leagueScreen=new LeagueScreen(a);a.leagueScreen.table=table;a.leagueScreen.year=table.year;a.leagueScreen.group=4;a.leagueScreen.drawTable();});
  String code=matches.get(0).homeCode;LeagueTable.Entry team=table.team(code);assertNotNull(team);boolean was=a.library.favorite("nba",code);ui(()->a.root.findViewWithTag("favorite-"+team.id).performClick());assertEquals(!was,new UserLibrary(a.prefs).favorite("nba",code));if(was)ui(()->a.root.findViewWithTag("favorite-"+team.id).performClick());capture("favorites");
  ui(()->{a.leagueScreen.close();a.filter=0;a.renderHome();});Thread.sleep(1600);getInstrumentation().waitForIdleSync();assertTrue(text(a.root).contains("Обзоры и видео"));assertTrue(text(a.root.findViewWithTag(matches.get(0).id)).contains("★"));
  int top=Integer.MAX_VALUE;for(YoutubeFeed.Video v:feed.featured()){View tile=a.root.findViewWithTag("yt:"+v.id);assertNotNull(tile);Rect rect=new Rect();assertTrue(tile.getGlobalVisibleRect(rect));assertEquals(tile.getHeight(),rect.height());assertEquals(tile.getWidth(),rect.width());top=Math.min(top,rect.top);}
  // Visible match cards must fit their viewport with both logos and the date fully inside.
  View first=a.root.findViewWithTag(a.ordered().get(0).id);assertTrue(first.getHeight()>=a.dp(106));assertTrue(first.getBottom()<top);assertTrue(text(first).contains("окт"));capture("home-youtube-favorites");
  ui(()->a.root.findViewWithTag("youtube-all").performClick());assertNotNull(a.youtubeScreen);assertTrue(text(a.root).contains("ПРЕВЬЮ"));capture("youtube-videos");ui(()->a.onBackPressed());assertNull(a.youtubeScreen);
 }
 public void testCompactStandingsAndAdvancedSeasonMetrics()throws Exception{
  LeagueTable table=LeagueTable.parse(fixture("standings-div.json"),1);SeasonStats stats=SeasonStats.parse(fixture("team-stats.json"),1);
  ui(()->{a.destroyPlayer();a.demo=false;a.leagueScreen=new LeagueScreen(a);a.leagueScreen.table=table;a.leagueScreen.year=table.year;a.leagueScreen.drawTable();});View team=a.root.findViewWithTag("team-2");assertTrue(team.getHeight()<=a.dp(34));capture("standings-compact");
  ui(()->{a.leagueScreen.group=3;a.leagueScreen.drawTable();});assertFalse(text(a.root).contains("— — —"));assertTrue(text(a.root).contains("OPP"));capture("league-analytics");
  ui(()->{a.leagueScreen.team=table.team("MIN");a.leagueScreen.teamTab=1;a.leagueScreen.seasonStats=stats;a.leagueScreen.drawTeam();});String body=text(a.root);assertTrue(body.contains("TS%"));assertTrue(body.contains(stats.value("trueShootingPct")+"%"));assertTrue(body.contains("ПОДБОРЫ"));capture("team-advanced");
 }
 String js(String script)throws Exception{CountDownLatch ready=new CountDownLatch(1);String[] value={null};a.runOnUiThread(()->a.web.evaluateJavascript(script,v->{value[0]=v;ready.countDown();}));assertTrue(ready.await(10,TimeUnit.SECONDS));return value[0];}
 void localRecording(Match match,String url,boolean resume)throws Exception{
  ui(()->a.openMatch(match,true));Thread.sleep(600);js("document.querySelector('video').src="+JSONObject.quote(url)+";document.querySelector('video').load();document.querySelector('video').play();true");long until=System.currentTimeMillis()+12000;while(System.currentTimeMillis()<until){if("true".equals(js("!!window.AanbaTv&&window.AanbaTv.state().end>55&&window.AanbaTv.state().ready")))break;Thread.sleep(200);}assertEquals("true",js("!!window.AanbaTv&&window.AanbaTv.state().end>55&&window.AanbaTv.state().ready"));ui(()->{a.ui.removeCallbacks(a.poll);a.demo=false;a.watchingDetails=null;a.resumePending=resume;a.resumeReadyAt=0;});
 }
 public void testActualMediaPositionSavedAndResumedOnReopen()throws Exception{
  byte[] bytes;try(InputStream in=getInstrumentation().getContext().getAssets().open("resume-test-60s.mp4");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);bytes=out.toByteArray();}String url="data:video/mp4;base64,"+android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP);
  Match match=new Match("qa-resume","Бостон","Лейкерс","BOS","LAL","NBA","finished","https://vkvideo.ru/video-55574239_999999999","",System.currentTimeMillis()-86400000);localRecording(match,url,false);js("window.AanbaTv.seekTo(23);window.AanbaTv.pause();true");Thread.sleep(1100);ui(()->a.saveProgress());WatchHistory.Entry entry=new UserLibrary(a.prefs).history.get(match);assertNotNull("Playback progress must be persisted",entry);assertEquals(23,entry.seconds,1.5);assertTrue(entry.resumable());
  ui(()->a.destroyPlayer());localRecording(match,url,true);long until=System.currentTimeMillis()+7000;double current=0;while(System.currentTimeMillis()<until){current=Double.parseDouble(js("document.querySelector('video').currentTime"));if(current>=22&&!a.resumePending)break;Thread.sleep(200);}assertTrue("Media resumes at stored position, actual="+current,current>=22&&current<29);assertFalse(a.resumePending);ui(()->{a.showControls(true);a.controls.settings.requestFocus();});capture("player-resumed");
  ui(()->a.restartRecording());Thread.sleep(700);assertTrue(Double.parseDouble(js("document.querySelector('video').currentTime"))<3);
 }
 public void testYoutubeCardOpensInstalledYoutubeApp()throws Exception{
  Intent target=new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://www.youtube.com/watch?v=g68Rh2qeuf4")).setPackage("com.google.android.youtube.tv");if(a.getPackageManager().resolveActivity(target,0)==null)return;
  YoutubeFeed feed=YoutubeFeed.parse(fixture("youtube-feed.xml"),1);ui(()->{a.destroyPlayer();a.demo=false;a.youtube=feed;a.renderHome();a.root.findViewWithTag("yt:g68Rh2qeuf4").performClick();});boolean opened=false;long until=System.currentTimeMillis()+10000;while(System.currentTimeMillis()<until){android.view.accessibility.AccessibilityNodeInfo root=getInstrumentation().getUiAutomation().getRootInActiveWindow();if(root!=null&&"com.google.android.youtube.tv".contentEquals(root.getPackageName())){opened=true;break;}Thread.sleep(250);}assertTrue("Click on the native card must open YouTube TV",opened);
 }
 public void testKeylessYoutubeAndTeamMetricsOnline()throws Exception{
  CountDownLatch done=new CountDownLatch(2);Object[] result=new Object[2];ui(()->{a.repository.youtube((v,s,e)->{result[0]=v;done.countDown();});a.repository.season("16",2027,2,(v,s,e)->{result[1]=v;done.countDown();});});assertTrue(done.await(45,TimeUnit.SECONDS));assertNotNull("YouTube RSS accessible on Android TV",result[0]);assertNotNull("ESPN season stats accessible on Android TV",result[1]);assertEquals(2027,((SeasonStats)result[1]).year);
 }
}
