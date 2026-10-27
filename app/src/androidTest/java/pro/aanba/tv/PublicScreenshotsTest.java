package pro.aanba.tv;
import android.content.Intent;
import android.graphics.Bitmap;
import android.test.ActivityInstrumentationTestCase2;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
/** Opt-in screenshots: real catalogue/ESPN data; never fabricate a broadcast frame. */
@SuppressWarnings("deprecation")
public class PublicScreenshotsTest extends ActivityInstrumentationTestCase2<MainActivity>{
 MainActivity a;public PublicScreenshotsTest(){super(MainActivity.class);}
 void ui(Runnable r){a.runOnUiThread(r);getInstrumentation().waitForIdleSync();}
 void capture(String name)throws Exception{Thread.sleep(700);getInstrumentation().waitForIdleSync();Bitmap image=getInstrumentation().getUiAutomation().takeScreenshot();File dir=new File(a.getExternalFilesDir(null),"public-screenshots");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
 String fixture(String name)throws Exception{try(InputStream in=getInstrumentation().getContext().getAssets().open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);return out.toString("UTF-8");}}
 String js(String script)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] value={"null"};a.runOnUiThread(()->a.web.evaluateJavascript(script,v->{value[0]=v;done.countDown();}));assertTrue(done.await(6,TimeUnit.SECONDS));return value[0];}
 public void testCapturePublicScreens()throws Exception{
  Intent i=new Intent();i.putExtra("qa_mode",true);setActivityIntent(i);a=getActivity();getInstrumentation().waitForIdleSync();Set<String> favorites=new HashSet<>(a.library.favorites);
  ui(()->{a.destroyPlayer();a.demo=false;a.initYoutube();a.sourceMessage="Загружаем расписание…";a.renderHome();a.refreshSchedule(true);a.loadYoutube();});long until=System.currentTimeMillis()+35000;while((a.matches.isEmpty()||a.loading||a.youtubeLoading)&&System.currentTimeMillis()<until)Thread.sleep(250);assertFalse(a.matches.isEmpty());Thread.sleep(2000);ui(()->a.root.findViewWithTag(a.ordered().get(0).id).requestFocus());capture("01-home");
  long now=System.currentTimeMillis();LeagueTable table=LeagueTable.parse(Network.get("https://site.api.espn.com/apis/v2/sports/basketball/nba/standings?type=0&level=3"),now);SeasonStats season=SeasonStats.parse(Network.get("https://site.api.espn.com/apis/site/v2/sports/basketball/nba/teams/16/statistics?season="+table.year+"&seasontype=2"),now);TeamSchedule schedule=TeamSchedule.parse(Network.get("https://site.api.espn.com/apis/site/v2/sports/basketball/nba/teams/16/schedule?season="+table.year+"&seasontype=2"),now);
  ui(()->{a.leagueScreen=new LeagueScreen(a);a.leagueScreen.table=table;a.leagueScreen.year=table.year;a.leagueScreen.status="ESPN · "+a.time(now);a.leagueScreen.drawTable();});capture("02-standings");
  ui(()->{a.leagueScreen.team=table.team("MIN");a.leagueScreen.teamTab=1;a.leagueScreen.seasonStats=season;a.leagueScreen.drawTeam();});capture("03-team-analytics");
  GameDetails details=GameDetails.parse(fixture("showcase-game.json"),"nba",now);TeamSchedule.Game game=null;for(TeamSchedule.Game g:schedule.games)if(g.id.equals(details.eventId)){game=g;break;}assertNotNull(game);TeamSchedule.Game selected=game;
  ui(()->{a.leagueScreen.game=selected;a.leagueScreen.drawGame();a.leagueScreen.detailPanel.bind(selected.asMatch(),details.score,details,false,"");a.leagueScreen.detailPanel.selectTab(1);});capture("04-box-score");
  ui(()->{a.leagueScreen.game=null;a.leagueScreen.team=null;a.leagueScreen.group=4;a.leagueScreen.drawTable();});capture("05-favorites");
  ui(()->{a.leagueScreen=null;a.youtubeScreen=new YoutubeScreen(a);a.youtubeScreen.render();});capture("06-youtube");ui(()->{a.youtubeScreen=null;a.renderHome();});
  Match stream=null;for(Match m:a.ordered())if(m.key().equals(selected.asMatch().key())){stream=m;break;}if(stream==null)for(Match m:a.ordered())if("post".equals(m.state(a.scoreFor(m),System.currentTimeMillis()))&&m.league.equals("NBA")){stream=m;break;}
  if(stream!=null){Match recording=stream;ui(()->a.openMatch(recording,false));boolean ready=false;long end=System.currentTimeMillis()+30000;while(System.currentTimeMillis()<end){try{JSONObject state=new JSONObject(js("window.AanbaTv?window.AanbaTv.state():{}"));if(state.optBoolean("ready")&&state.optInt("width")>0){ready=true;break;}}catch(Exception ignored){}Thread.sleep(300);}File dir=new File(a.getExternalFilesDir(null),"public-screenshots");dir.mkdirs();try(FileWriter out=new FileWriter(new File(dir,"player-result.txt"))){out.write("actual_vk_ready="+ready+"\n");}if(ready){ui(()->{a.showControls(true);a.controls.settings.requestFocus();});capture("07-player");ui(()->{a.setMode(MainActivity.PANEL);a.showControls(false);});Thread.sleep(2500);capture("08-player-stats");}}
  ui(()->{a.library.favorites.clear();a.library.favorites.addAll(favorites);a.prefs.edit().putStringSet("favorite-teams",favorites).apply();a.leagueScreen=null;a.youtubeScreen=null;a.goHome();});
 }
}
