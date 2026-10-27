package pro.aanba.tv;

import android.content.Intent;
import android.graphics.Bitmap;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("deprecation")
public class HomeUiTest extends ActivityInstrumentationTestCase2<MainActivity> {
    public HomeUiTest(){super(MainActivity.class);}
    public void testCompactCardsLogosAndSpoilerSafeRecords()throws Exception{
        Intent intent=new Intent();intent.putExtra("qa_mode",true);setActivityIntent(intent);
        MainActivity a=getActivity();List<Match> list=new ArrayList<>();long now=System.currentTimeMillis();
        String[][] names={{"Нью-Орлеан Пеликанс","Денвер Наггетс","NOP","DEN"},{"Бостон Селтикс","Лос-Анджелес Лейкерс","BOS","LAL"},{"Голден Стэйт Уорриорз","Сан-Антонио Спёрс","GSW","SAS"},{"Майами Хит","Шарлотт Хорнетс","MIA","CHA"},{"Нью-Йорк Никс","Бруклин Нетс","NYK","BKN"},{"Даллас Маверикс","Финикс Санз","DAL","PHX"},{"Хьюстон Рокетс","Атланта Хоукс","HOU","ATL"},{"Вашингтон Уизардс","Чикаго Буллз","WAS","CHI"},{"Миннесота Тимбервулвз","Мемфис Гриззлис","MIN","MEM"}};
        for(int i=0;i<names.length;i++)list.add(new Match("qa"+i,names[i][0],names[i][1],names[i][2],names[i][3],"NBA",i==5?"finished":i==1?"live":"upcoming","https://vkvideo.ru/live-55574239_456428280","",now+(i==5?-24*3600000L:i==1?-3600000L:(i+1)*3600000L)));
        a.runOnUiThread(()->{
            a.destroyPlayer();a.demo=false;a.matches.clear();a.matches.addAll(list);a.scores.clear();
            for(int i=0;i<list.size();i++){
                Match match=list.get(i);Score score=Score.demo(match);score.state=i==5?"post":i==1?"in":"pre";
                score.aRecord="3-15";score.bRecord="12-6";a.scores.put(score.key()+":"+score.start,score);
            }
            a.sourceMessage="ДЕМОНСТРАЦИЯ ИНТЕРФЕЙСА · пример данных, не реальные матчи";a.renderHome();a.root.findViewWithTag("qa0").requestFocus();
        });
        getInstrumentation().waitForIdleSync();Thread.sleep(2200);getInstrumentation().waitForIdleSync();
        View first=a.root.findViewWithTag("qa0"),other=a.root.findViewWithTag("qa2"),third=a.root.findViewWithTag("qa3");
        // Live sorts first, then the two earliest upcoming cards: all three fit in the first row.
        int[] x=new int[2],y=new int[2],z=new int[2];first.getLocationOnScreen(x);other.getLocationOnScreen(y);third.getLocationOnScreen(z);
        assertTrue(first.getWidth()<a.root.getWidth()/2);assertTrue(first.getHeight()<=a.dp(150));
        View live=a.root.findViewWithTag("qa1");int[] liveAt=new int[2];live.getLocationOnScreen(liveAt);
        assertSame(live.getParent(),first.getParent());assertSame(first.getParent(),other.getParent());
        assertTrue(text(first).contains("(3-15)"));assertTrue(text(first).contains("(12-6)"));
        assertFalse("A finished game must not leak its outcome through a current record",text(a.root.findViewWithTag("qa5")).contains("(3-15)"));
        assertTrue("Both actual logos must load from packaged assets",images(first)>=2);
        for(int i=0;i<names.length;i++){View card=a.root.findViewWithTag("qa"+i);a.runOnUiThread(card::requestFocus);getInstrumentation().waitForIdleSync();Thread.sleep(350);android.graphics.Rect visible=new android.graphics.Rect();assertTrue("Card must be visible: "+i,card.getGlobalVisibleRect(visible));assertEquals("Full card width: "+i,card.getWidth(),visible.width());assertEquals("Full card height: "+i,card.getHeight(),visible.height());assertEquals(1f,card.getScaleX());}
        Bitmap bitmap=getInstrumentation().getUiAutomation().takeScreenshot();
        File dir=new File(a.getExternalFilesDir(null),"qa");dir.mkdirs();
        try(FileOutputStream output=new FileOutputStream(new File(dir,"home-compact-demo.png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,output);}bitmap.recycle();
    }
    private int images(View v){int n=v instanceof ImageView&&((ImageView)v).getDrawable()!=null?1:0;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)n+=images(((ViewGroup)v).getChildAt(i));return n;}
    private String text(View v){StringBuilder s=new StringBuilder();if(v instanceof TextView)s.append(((TextView)v).getText());if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)s.append(text(((ViewGroup)v).getChildAt(i)));return s.toString();}
}
