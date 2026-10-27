package pro.aanba.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Handler;
import android.util.LruCache;
import android.widget.ImageView;
import org.json.JSONObject;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;

/** Real packaged team logos; unknown teams keep a monogram instead of an empty image. */
final class TeamBranding {
    private final Context context;
    private final ExecutorService io;
    private final Handler main;
    private JSONObject teams=new JSONObject();
    private final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(3*1024*1024){
        @Override protected int sizeOf(String key,Bitmap value){return value.getByteCount();}
    };
    TeamBranding(Context context,ExecutorService io,Handler main){
        this.context=context.getApplicationContext();this.io=io;this.main=main;
        try(InputStream in=context.getAssets().open("teams.json")){
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] chunk=new byte[4096];
            for(int n;(n=in.read(chunk))!=-1;)out.write(chunk,0,n);
            teams=new JSONObject(out.toString(StandardCharsets.UTF_8.name()));
        }catch(Exception ignored){}
    }
    private JSONObject info(Match match,String code){return teams.optJSONObject(match.espnLeague()+":"+code);}
    int color(Match match,String code){
        JSONObject t=info(match,code);try{return Color.parseColor("#"+t.optString("color"));}catch(Exception ignored){return 0xff41495c;}
    }
    String nickname(String full){int at=full.lastIndexOf(' ');return at>=0?full.substring(at+1):full;}
    void load(Match match,String code,ImageView target){
        JSONObject t=info(match,code);if(t==null)return;
        String asset=t.optString("asset","");target.setTag(asset);
        Bitmap ready=cache.get(asset);if(ready!=null){target.setImageBitmap(ready);return;}
        WeakReference<ImageView> ref=new WeakReference<>(target);
        if(io.isShutdown())return;
        io.execute(()->{
            Bitmap bitmap=cache.get(asset);
            if(bitmap==null)try(InputStream input=context.getAssets().open(asset)){
                BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=4;
                bitmap=BitmapFactory.decodeStream(input,null,options);if(bitmap!=null)cache.put(asset,bitmap);
            }catch(Exception ignored){}
            Bitmap result=bitmap;
            main.post(()->{ImageView image=ref.get();if(image!=null&&result!=null&&asset.equals(image.getTag()))image.setImageBitmap(result);});
        });
    }
}
