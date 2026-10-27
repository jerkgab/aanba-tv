package pro.aanba.tv;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
public final class Network {
 public static String get(String url)throws IOException{
  for(int redirects=0;redirects<4;redirects++){
   if(!SafeLinks.api(url))throw new IOException("Неподдерживаемый адрес источника");HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(10000);c.setReadTimeout(12000);c.setRequestProperty("User-Agent","AANBA-TV/"+BuildConfig.VERSION_NAME+" (Android TV)");c.setRequestProperty("Accept","application/json,application/atom+xml,text/html;q=0.9");c.setInstanceFollowRedirects(false);
   try{int status=c.getResponseCode();if(status==301||status==302||status==303||status==307||status==308){String location=c.getHeaderField("Location");if(location==null)throw new IOException("Пустой адрес перенаправления");url=new URL(new URL(url),location).toString();continue;}if(status!=200)throw new IOException("Источник ответил "+status);
    try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[8192];for(int n,total=0;(n=in.read(buf))!=-1;){total+=n;if(total>4_000_000)throw new IOException("Слишком большой ответ источника");out.write(buf,0,n);}return out.toString(StandardCharsets.UTF_8.name());}
   }finally{c.disconnect();}
  }throw new IOException("Слишком много перенаправлений");
 }
}
