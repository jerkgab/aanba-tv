package pro.aanba.tv;
import android.content.Context;
import android.graphics.*;
import android.os.Handler;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.*;
import java.net.*;
import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
/** Thumbnails have their own bounded pool so an unavailable YouTube cannot delay matches or ESPN. */
final class ThumbLoader {
 final Context context;final File directory;final ExecutorService io=Executors.newFixedThreadPool(2);final Handler main;
 final Map<String,List<WeakReference<ImageView>>> pending=new HashMap<>();
 final LruCache<String,Bitmap> memory=new LruCache<String,Bitmap>(4*1024*1024){protected int sizeOf(String k,Bitmap b){return b.getByteCount();}};
 ThumbLoader(Context context,ExecutorService unused,Handler main){this.context=context.getApplicationContext();this.main=main;directory=new File(context.getCacheDir(),"youtube-thumbnails");directory.mkdirs();}
 void load(String url,ImageView image){if(!SafeLinks.thumbnail(url))return;image.setTag(url);Bitmap ready=memory.get(url);if(ready!=null){image.setImageBitmap(ready);return;}
  try{String id=new URI(url).getPath().split("/")[2];try(InputStream in=context.getAssets().open("youtube/"+id+".jpg")){Bitmap fallback=BitmapFactory.decodeStream(in);if(fallback!=null)image.setImageBitmap(fallback);}}catch(Exception ignored){}
  if(io.isShutdown())return;synchronized(pending){List<WeakReference<ImageView>> list=pending.get(url);if(list!=null){list.add(new WeakReference<>(image));return;}list=new ArrayList<>();list.add(new WeakReference<>(image));pending.put(url,list);}
  io.execute(()->{Bitmap bitmap=null;try{StringBuilder name=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(url.getBytes(java.nio.charset.StandardCharsets.UTF_8)))name.append(String.format(Locale.US,"%02x",b));File file=new File(directory,name+".img");
   if(file.exists()){if(file.length()<=1_500_000)try(InputStream in=new FileInputStream(file)){bitmap=decode(read(in));}if(bitmap==null)file.delete();}
   if(bitmap==null){HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(8000);connection.setReadTimeout(10000);connection.setRequestProperty("User-Agent","AANBA-TV/"+BuildConfig.VERSION_NAME);try{if(connection.getResponseCode()!=200)throw new IOException("Thumbnail unavailable");try(InputStream in=connection.getInputStream()){byte[] data=read(in);bitmap=decode(data);if(bitmap!=null){File temp=new File(directory,name+".tmp");try(FileOutputStream save=new FileOutputStream(temp)){save.write(data);}if(!temp.renameTo(file))temp.delete();}}}finally{connection.disconnect();}}
   if(bitmap!=null)memory.put(url,bitmap);trim();
  }catch(Exception ignored){}Bitmap result=bitmap;List<WeakReference<ImageView>> targets;synchronized(pending){targets=pending.remove(url);}if(targets!=null)main.post(()->{for(WeakReference<ImageView> ref:targets){ImageView target=ref.get();if(target!=null&&result!=null&&url.equals(target.getTag()))target.setImageBitmap(result);}});});
 }
 static byte[] read(InputStream in)throws IOException{try(ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] chunk=new byte[8192];for(int n,total=0;(n=in.read(chunk))!=-1;){total+=n;if(total>1_500_000)throw new IOException("Thumbnail too large");out.write(chunk,0,n);}return out.toByteArray();}}
 static Bitmap decode(byte[] data){BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,opts);if(opts.outWidth<=0||opts.outHeight<=0||opts.outWidth>10000||opts.outHeight>10000)return null;opts.inJustDecodeBounds=false;opts.inSampleSize=1;while(opts.outWidth/opts.inSampleSize>960||opts.outHeight/opts.inSampleSize>540)opts.inSampleSize*=2;return BitmapFactory.decodeByteArray(data,0,data.length,opts);}
 synchronized void trim(){File[] files=directory.listFiles((dir,name)->name.endsWith(".img"));if(files==null)return;Arrays.sort(files,Comparator.comparingLong(File::lastModified));long size=0;for(File file:files)size+=file.length();for(File file:files)if(size>8_000_000){long n=file.length();if(file.delete())size-=n;}}
 void close(){io.shutdownNow();synchronized(pending){pending.clear();}}
}
