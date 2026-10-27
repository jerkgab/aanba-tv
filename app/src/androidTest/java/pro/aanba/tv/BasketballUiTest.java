package pro.aanba.tv;

import android.content.Intent;
import android.graphics.Bitmap;
import android.test.ActivityInstrumentationTestCase2;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;

@SuppressWarnings("deprecation")
public class BasketballUiTest extends ActivityInstrumentationTestCase2<MainActivity> {
    MainActivity a;
    public BasketballUiTest(){super(MainActivity.class);}
    @Override protected void setUp()throws Exception{super.setUp();Intent i=new Intent();i.putExtra("qa_mode",true);setActivityIntent(i);a=getActivity();getInstrumentation().waitForIdleSync();}
    void ui(Runnable r){a.runOnUiThread(r);getInstrumentation().waitForIdleSync();}
    String asset(String name)throws Exception{try(InputStream in=getInstrumentation().getContext().getAssets().open(name+".json");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);return out.toString("UTF-8");}}
    void key(int code){getInstrumentation().sendKeyDownUpSync(code);getInstrumentation().waitForIdleSync();}
    String text(View v){StringBuilder b=new StringBuilder();if(v instanceof TextView)b.append(((TextView)v).getText()).append(' ');if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)b.append(text(((ViewGroup)v).getChildAt(i)));return b.toString();}
    TextView findText(View v,String value){if(v instanceof TextView&&((TextView)v).getText().toString().contains(value))return (TextView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView found=findText(((ViewGroup)v).getChildAt(i),value);if(found!=null)return found;}return null;}
    void capture(String name)throws Exception{Thread.sleep(600);getInstrumentation().waitForIdleSync();Bitmap bitmap=getInstrumentation().getUiAutomation().takeScreenshot();File dir=new File(a.getExternalFilesDir(null),"qa12");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
    void awaitVideo()throws Exception{long end=System.currentTimeMillis()+20000;while(System.currentTimeMillis()<end){if(videoTime()>0.3)return;Thread.sleep(200);}fail("Local video did not start");}
    double videoTime()throws Exception{CountDownLatch latch=new CountDownLatch(1);String[] value={"0"};a.runOnUiThread(()->a.web.evaluateJavascript("Number(document.querySelector('video').currentTime)||0",s->{value[0]=s;latch.countDown();}));assertTrue(latch.await(5,TimeUnit.SECONDS));return Double.parseDouble(value[0]);}
    public void testExtendedPanelTabsScrollSpoilersAndVideoContinuity()throws Exception{
        GameDetails data=GameDetails.parse(asset("summary-final"),"nba",System.currentTimeMillis());Match match=new Match("fixture-final","Майами Хит","Шарлотт Хорнетс","MIA","CHA","NBA","finished","https://vkvideo.ru/live-55574239_456428303","",data.score.start);
        ui(()->{a.openMatch(match,true);a.watchingDetails=data;a.setMode(MainActivity.PANEL);a.showControls(false);});awaitVideo();assertEquals(1,a.loadCount);
        assertTrue(text(a.sidebar).contains("Максимальный отрыв"));assertTrue(text(a.sidebar).contains("37-86"));capture("team-stats");
        double before=videoTime();key(KeyEvent.KEYCODE_DPAD_RIGHT);assertEquals(1,a.statsPanel.tab);assertTrue(text(a.sidebar).contains("Antetokounmpo"));assertTrue(text(a.sidebar).contains("MIN"));capture("player-boxscore");
        key(KeyEvent.KEYCODE_DPAD_DOWN);Thread.sleep(400);assertTrue(a.statsPanel.scroll.getScrollY()>0);capture("players-scrolled");
        assertEquals(1,a.loadCount);assertTrue(videoTime()>before||before>10);
        int y=a.statsPanel.scroll.getScrollY();ui(()->a.updateStats());Thread.sleep(150);assertEquals(y,a.statsPanel.scroll.getScrollY());
        key(KeyEvent.KEYCODE_BACK);assertEquals(MainActivity.HIDDEN,a.mode);assertNotNull(a.watching);key(KeyEvent.KEYCODE_BACK);assertNull(a.watching);
    }
    public void testStandingsTeamCalendarGameAndBack()throws Exception{
        LeagueTable table=LeagueTable.parse(asset("standings-div"),System.currentTimeMillis());TeamSchedule schedule=TeamSchedule.parse(asset("team-schedule"),System.currentTimeMillis());
        ui(()->{a.destroyPlayer();a.demo=false;a.leagueScreen=new LeagueScreen(a);a.leagueScreen.table=table;a.leagueScreen.year=table.year;a.leagueScreen.status="ESPN · снимок ответа для проверки интерфейса";a.leagueScreen.drawTable();});
        assertTrue(text(a.root).contains("Таблица NBA"));assertNotNull(a.root.findViewWithTag("team-2"));capture("standings-east");
        ui(()->{a.leagueScreen.group=1;a.leagueScreen.drawTable();});assertTrue(text(a.root).contains("Minnesota"));capture("standings-west");
        ui(()->{a.leagueScreen.group=2;a.leagueScreen.drawTable();});assertTrue(text(a.root).contains("Атлантический"));capture("standings-divisions");
        ui(()->{LeagueScreen screen=a.leagueScreen;screen.generation++;screen.team=table.team("MIN");screen.schedule=schedule;screen.gameFilter=0;screen.drawTeam();});assertTrue(text(a.root).contains("Ближайшие"));capture("team-calendar");TextView season=findText(a.root,"2026–2027");assertNotNull(season);assertTrue("Season label must be visible",season.getWidth()>0&&season.getHeight()>0);
        ui(()->{a.leagueScreen.gameFilter=1;a.leagueScreen.drawTeam();});String hidden=text(a.root);assertFalse(hidden.contains("Счёт скрыт"));
        TeamSchedule.Game g=schedule.games.get(0);ui(()->{a.leagueScreen.game=g;a.leagueScreen.drawGame();});assertFalse(text(a.root).contains("Статистика скрыта"));key(KeyEvent.KEYCODE_BACK);assertNull(a.leagueScreen.game);assertNotNull(a.leagueScreen.team);key(KeyEvent.KEYCODE_BACK);assertNull(a.leagueScreen.team);key(KeyEvent.KEYCODE_BACK);assertNull(a.leagueScreen);
    }
    public void testKeylessOnlineSummaryStandingsAndCalendar()throws Exception{
        GameDetails data=GameDetails.parse(Network.get("https://site.api.espn.com/apis/site/v2/sports/basketball/nba/summary?event=401656363"),"nba",System.currentTimeMillis());assertNotNull(data.team("BOS"));assertFalse(data.team("BOS").players.isEmpty());
        CountDownLatch done=new CountDownLatch(2);Object[] result=new Object[2];
        ui(()->{a.repository.standings((v,s,e)->{result[0]=v;done.countDown();});a.repository.schedule("16",2027,2,(v,s,e)->{result[1]=v;done.countDown();});});
        assertTrue("Free ESPN requests complete",done.await(35,TimeUnit.SECONDS));assertNotNull(result[0]);assertNotNull(result[1]);
    }
}
