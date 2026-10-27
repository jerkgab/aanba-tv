package pro.aanba.tv;

import android.content.Intent;
import android.graphics.Bitmap;
import android.test.ActivityInstrumentationTestCase2;
import android.view.KeyEvent;
import java.io.*;
import java.util.*;

@SuppressWarnings("deprecation")
public class SourceTest extends ActivityInstrumentationTestCase2<MainActivity>{
    public SourceTest(){super(MainActivity.class);}
    public void testLiveCatalogAndScoreFeedAndDashboard()throws Exception{
        List<Match> list=ScheduleParser.parse(Network.get("https://aanba.pro/schedule"));assertTrue(list.size()>=1);
        Match selected=null;for(Match m:list)if(m.espnLeague().equals("nba")){selected=m;break;}assertNotNull(selected);
        List<Score> feed=Score.parse(Network.get("https://site.api.espn.com/apis/site/v2/sports/basketball/nba/scoreboard?dates="+selected.espnDate()),"nba",System.currentTimeMillis());
        for(Match w:list)if(w.espnLeague().equals("wnba")){
            List<Score> women=Score.parse(Network.get("https://site.api.espn.com/apis/site/v2/sports/basketball/wnba/scoreboard?dates="+w.espnDate()),"wnba",System.currentTimeMillis());
            boolean linked=false;for(Score s:women)if(s.matches(w))linked=true;assertTrue("WNBA aliases map to the right game",linked);feed.addAll(women);break;
        }
        boolean matched=false;for(Score s:feed)if(s.matches(selected))matched=true;assertTrue("AANBA game maps to ESPN teams and date",matched);
        Intent i=new Intent();i.putExtra("qa_mode",true);setActivityIntent(i);MainActivity a=getActivity();
        a.runOnUiThread(()->{a.matches.clear();a.matches.addAll(list);for(Score s:feed)a.scores.put(s.key()+":"+s.start,s);a.sourceMessage="Обновлено "+a.time(System.currentTimeMillis())+" · время вашего ТВ";a.goHome();});getInstrumentation().waitForIdleSync();
        for(int n=0;n<6;n++)getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);getInstrumentation().waitForIdleSync();assertNotNull(a.getCurrentFocus());
        a.runOnUiThread(()->{a.renderHome();a.root.findViewWithTag(a.ordered().get(0).id).requestFocus();});getInstrumentation().waitForIdleSync();
        Thread.sleep(600);getInstrumentation().waitForIdleSync();
        Bitmap image=getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(image);File dir=new File(a.getExternalFilesDir(null),"qa");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,"home.png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
    }
}
