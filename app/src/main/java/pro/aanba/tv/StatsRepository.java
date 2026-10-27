package pro.aanba.tv;

import android.content.Context;
import android.os.Handler;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;

/** Direct, keyless ESPN requests; bounded device cache and one request per resource at a time. */
final class StatsRepository {
    interface Callback<T>{void loaded(T value,boolean stale,String error);}
    interface Fetcher{String get(String url)throws IOException;}
    interface Parser<T>{T parse(String json,long at)throws Exception;}
    private final Fetcher fetcher;private final File directory;private final ExecutorService io;private final Handler main;
    private final Map<String,List<Callback<?>>> pending=new HashMap<>();
    StatsRepository(Context context,ExecutorService io,Handler main){this(context,io,main,Network::get);}
    StatsRepository(Context context,ExecutorService io,Handler main,Fetcher fetcher){this.fetcher=fetcher;directory=new File(context.getCacheDir(),"basketball");directory.mkdirs();this.io=io;this.main=main;}
    void summary(String league,String event,boolean finished,Callback<GameDetails> callback){
        if(!event.matches("\\d+")||!(league.equals("nba")||league.equals("wnba"))){callback.loaded(null,true,"Неверный матч");return;}
        request("game-"+league+"-"+event,"https://site.api.espn.com/apis/site/v2/sports/basketball/"+league+"/summary?event="+event,finished?86400000L:20000L,(s,t)->GameDetails.parse(s,league,t),callback);
    }
    void standings(Callback<LeagueTable> callback){request("standings","https://site.api.espn.com/apis/v2/sports/basketball/nba/standings?type=0&level=3",300000L,LeagueTable::parse,callback);}
    void youtube(Callback<YoutubeFeed> callback){youtube(false,callback);}
    void youtube(boolean force,Callback<YoutubeFeed> callback){request("youtube-aanba","https://www.youtube.com/feeds/videos.xml?channel_id="+YoutubeFeed.CHANNEL,force?0L:900000L,YoutubeFeed::parse,callback);}
    void season(String team,int year,int type,Callback<SeasonStats> callback){
        if(!team.matches("\\d+")||year<1947||year>2200||type<1||type>3){callback.loaded(null,true,"Неверная команда");return;}
        request("season-stats-"+team+"-"+year+"-"+type,"https://site.api.espn.com/apis/site/v2/sports/basketball/nba/teams/"+team+"/statistics?season="+year+"&seasontype="+type,300000L,SeasonStats::parse,callback);
    }
    void schedule(String team,int year,int type,Callback<TeamSchedule> callback){
        if(!team.matches("\\d+")||year<1947||year>2200||type<1||type>3){callback.loaded(null,true,"Неверная команда");return;}
        request("schedule-"+team+"-"+year+"-"+type,"https://site.api.espn.com/apis/site/v2/sports/basketball/nba/teams/"+team+"/schedule?season="+year+"&seasontype="+type,300000L,TeamSchedule::parse,callback);
    }
    private <T> void request(String key,String url,long ttl,Parser<T> parser,Callback<T> callback){
        if(io.isShutdown())return;
        synchronized(pending){List<Callback<?>> listeners=pending.get(key);if(listeners!=null){listeners.add(callback);return;}listeners=new ArrayList<>();listeners.add(callback);pending.put(key,listeners);}
        io.execute(()->{
            T cached=null;long at=0;
            try{JSONObject stored=new JSONObject(read(new File(directory,key+".json")));at=stored.getLong("at");cached=parser.parse(stored.getString("body"),at);}catch(Exception ignored){}
            if(cached!=null&&System.currentTimeMillis()-at<ttl){deliver(key,cached,false,null,true);return;}
            if(cached!=null)deliver(key,cached,true,null,false);
            try{String body=fetcher.get(url);if(key.startsWith("game-")){JSONObject source=new JSONObject(body),compact=new JSONObject();for(String field:new String[]{"header","boxscore","plays"})if(source.has(field))compact.put(field,source.get(field));body=compact.toString();}
                long now=System.currentTimeMillis();T value=parser.parse(body,now);save(key,new JSONObject().put("at",now).put("body",body).toString());deliver(key,value,false,null,true);}
            catch(Exception error){deliver(key,cached,true,"Не удалось обновить "+(key.startsWith("youtube")?"YouTube":"ESPN")+". Проверьте интернет.",true);}
        });
    }
    @SuppressWarnings("unchecked") private <T> void deliver(String key,T value,boolean stale,String error,boolean finish){
        List<Callback<?>> listeners;synchronized(pending){listeners=pending.get(key);if(listeners==null)return;listeners=new ArrayList<>(listeners);if(finish)pending.remove(key);}
        for(Callback<?> listener:listeners){Callback<T> c=(Callback<T>)listener;main.post(()->c.loaded(value,stale,error));}
    }
    private String read(File file)throws IOException{if(file.length()>6_000_000)throw new IOException("Cache size");try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);return out.toString(StandardCharsets.UTF_8.name());}}
    private synchronized void save(String key,String text){
        File target=new File(directory,key+".json"),temp=new File(directory,key+".tmp");
        try{try(OutputStream out=new FileOutputStream(temp)){out.write(text.getBytes(StandardCharsets.UTF_8));}if(!temp.renameTo(target))temp.delete();
            File[] files=directory.listFiles((dir,name)->name.endsWith(".json"));if(files==null)return;Arrays.sort(files,Comparator.comparingLong(File::lastModified));long total=0;for(File f:files)total+=f.length();for(File f:files)if(total>12_000_000&&!f.equals(target)){long size=f.length();if(f.delete())total-=size;}
        }catch(Exception ignored){temp.delete();}
    }
}
