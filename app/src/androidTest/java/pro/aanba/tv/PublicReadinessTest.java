package pro.aanba.tv;
import android.content.*;
import android.test.ActivityInstrumentationTestCase2;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
@SuppressWarnings("deprecation")
public class PublicReadinessTest extends ActivityInstrumentationTestCase2<MainActivity>{
 MainActivity a;public PublicReadinessTest(){super(MainActivity.class);}
 @Override protected void setUp()throws Exception{super.setUp();Intent i=new Intent();i.putExtra("qa_mode",true);setActivityIntent(i);a=getActivity();getInstrumentation().waitForIdleSync();}
 void ui(Runnable r){a.runOnUiThread(r);getInstrumentation().waitForIdleSync();}
 String text(View v){StringBuilder b=new StringBuilder();if(v instanceof TextView)b.append(((TextView)v).getText()).append(' ');if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)b.append(text(((ViewGroup)v).getChildAt(i)));return b.toString();}
 public void testYoutubeFailureKeepsSectionAndChannelReachable(){ui(()->{a.destroyPlayer();a.demo=false;a.youtube=null;a.youtubeMessage="Лента YouTube недоступна";a.renderHome();});assertNotNull(a.root.findViewWithTag("youtube-channel"));assertNotNull(a.root.findViewWithTag("youtube-refresh"));assertTrue(text(a.root).contains("Лента не загрузилась"));ui(()->a.root.findViewWithTag("youtube-all").performClick());assertNotNull(a.root.findViewWithTag("yt-channel"));assertNotNull(a.root.findViewWithTag("yt-refresh"));assertTrue(text(a.root).contains("Лента недоступна"));}
 public void testBundledPicksHavePreviewsBeforeAnyNetworkRequest(){ui(()->{a.destroyPlayer();a.demo=false;a.initYoutube();a.youtubeMessage="YouTube недоступен · подборка из сборки";a.renderHome();});assertTrue(a.youtubeBundled);assertEquals(3,a.youtube.videos.size());for(YoutubeFeed.Video v:a.youtube.featured()){View card=a.root.findViewWithTag("yt:"+v.id);assertNotNull(card);assertTrue("Bundled thumbnail loads without YouTube",images(card)>0);}assertTrue(text(a.root).contains("подборка из сборки"));}
 int images(View v){int count=v instanceof ImageView&&((ImageView)v).getDrawable()!=null?1:0;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)count+=images(((ViewGroup)v).getChildAt(i));return count;}
 public void testTenConcurrentSubscribersShareOneFetchAndOfflineCache()throws Exception{
  String xml;try(InputStream in=getInstrumentation().getContext().getAssets().open("youtube-feed.xml");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);xml=out.toString("UTF-8");}String response=xml;File cache=new File(a.getCacheDir(),"network-qa-"+System.nanoTime());cache.mkdirs();Context context=new ContextWrapper(a){@Override public File getCacheDir(){return cache;}};AtomicInteger requests=new AtomicInteger();CountDownLatch release=new CountDownLatch(1),done=new CountDownLatch(10);StatsRepository repository=new StatsRepository(context,a.io,a.ui,url->{requests.incrementAndGet();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){throw new IOException(e);}return response;});
  ui(()->{for(int i=0;i<10;i++)repository.youtube(true,(value,stale,error)->{if(!stale&&value!=null)done.countDown();});});release.countDown();assertTrue(done.await(10,TimeUnit.SECONDS));assertEquals("Ten consumers of one feed should not issue ten requests",1,requests.get());
  CountDownLatch failed=new CountDownLatch(1);AtomicBoolean cacheReturned=new AtomicBoolean();StatsRepository offline=new StatsRepository(context,a.io,a.ui,url->{throw new IOException("Simulated offline");});ui(()->offline.youtube(true,(value,stale,error)->{if(error!=null){cacheReturned.set(value!=null&&stale&&!value.videos.isEmpty());failed.countDown();}}));assertTrue(failed.await(8,TimeUnit.SECONDS));assertTrue("An offline update keeps the usable cached feed",cacheReturned.get());delete(cache);
 }
 void delete(File f){File[] children=f.listFiles();if(children!=null)for(File c:children)delete(c);f.delete();}
}
