package pro.aanba.tv;
import java.net.URI;
import java.util.Locale;
/** Public destinations only. Channel IDs and video IDs are identifiers, never credentials. */
final class SafeLinks {
 static final String YOUTUBE_CHANNEL="https://www.youtube.com/@aanba/videos";
 static boolean youtube(String url){return url!=null&&(url.equals(YOUTUBE_CHANNEL)||url.matches("https://www\\.youtube\\.com/watch\\?v=[A-Za-z0-9_-]{11}"));}
 static boolean thumbnail(String url){try{URI uri=new URI(url);String host=uri.getHost();return "https".equals(uri.getScheme())&&uri.getUserInfo()==null&&(uri.getPort()==-1||uri.getPort()==443)&&host!=null&&(host.equals("ytimg.com")||host.endsWith(".ytimg.com"))&&uri.getRawQuery()==null&&uri.getFragment()==null&&uri.getPath().matches("/vi/[A-Za-z0-9_-]{11}/[A-Za-z0-9_.-]+");}catch(Exception e){return false;}}
 static boolean api(String url){try{URI uri=new URI(url);if(!"https".equals(uri.getScheme())||uri.getUserInfo()!=null||uri.getPort()!=-1&&uri.getPort()!=443)return false;String host=uri.getHost();return host!=null&&(host.equals("aanba.pro")||host.equals("www.aanba.pro")||host.equals("site.api.espn.com")||host.equals("www.youtube.com")||host.equals("youtube.com"));}catch(Exception e){return false;}}
}
