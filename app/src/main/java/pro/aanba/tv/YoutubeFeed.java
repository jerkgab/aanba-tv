package pro.aanba.tv;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
public final class YoutubeFeed {
 static final String CHANNEL="UC-GILc1ZMxtDAyIATn6de5Q",ATOM="http://www.w3.org/2005/Atom",YT="http://www.youtube.com/xml/schemas/2015",MEDIA="http://search.yahoo.com/mrss/";
 public long fetchedAt;public final List<Video> videos=new ArrayList<>();
 public static class Video{public String id,title,thumbnail;public long published;public String watchUrl(){return "https://www.youtube.com/watch?v="+id;}public String displayTitle(){return title.replaceAll("\\s*#[^\\s]+","").trim();}public boolean longForm(){return title.split("#",-1).length<3;}}
 static String text(Element parent,String ns,String name){NodeList n=parent.getElementsByTagNameNS(ns,name);return n.getLength()==0?"":n.item(0).getTextContent().trim();}
 public static YoutubeFeed parse(String xml,long at)throws Exception{
  String upper=xml.toUpperCase(Locale.US);if(upper.contains("<!DOCTYPE")||upper.contains("<!ENTITY"))throw new IOException("XML entities are not allowed");DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);factory.setExpandEntityReferences(false);
  Document doc=factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));Element root=doc.getDocumentElement();String channel=text(root,YT,"channelId");if(!(channel.equals(CHANNEL)||("UC"+channel).equals(CHANNEL)))throw new IOException("Wrong YouTube channel");
  YoutubeFeed feed=new YoutubeFeed();feed.fetchedAt=at;NodeList entries=root.getElementsByTagNameNS(ATOM,"entry");Set<String> seen=new HashSet<>();for(int i=0;i<Math.min(30,entries.getLength());i++){
   Element entry=(Element)entries.item(i);Video v=new Video();v.id=text(entry,YT,"videoId");if(!v.id.matches("[A-Za-z0-9_-]{11}")||!seen.add(v.id))continue;v.title=text(entry,ATOM,"title");if(v.title.isEmpty())continue;try{v.published=OffsetDateTime.parse(text(entry,ATOM,"published")).toInstant().toEpochMilli();}catch(Exception e){v.published=0;}
   v.thumbnail="https://i.ytimg.com/vi/"+v.id+"/hqdefault.jpg";NodeList thumbnails=entry.getElementsByTagNameNS(MEDIA,"thumbnail");if(thumbnails.getLength()>0){String url=((Element)thumbnails.item(0)).getAttribute("url");if(SafeLinks.thumbnail(url))v.thumbnail=url;}
   feed.videos.add(v);
  }if(feed.videos.isEmpty())throw new IOException("YouTube feed is empty");feed.videos.sort((a,b)->Long.compare(b.published,a.published));return feed;
 }
 public static YoutubeFeed bundled(String json)throws Exception{org.json.JSONObject source=new org.json.JSONObject(json);YoutubeFeed feed=new YoutubeFeed();feed.fetchedAt=LocalDate.parse(source.getString("captured")).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();org.json.JSONArray rows=source.getJSONArray("videos");for(int i=0;i<rows.length();i++){org.json.JSONObject row=rows.getJSONObject(i);Video v=new Video();v.id=row.getString("id");if(!v.id.matches("[A-Za-z0-9_-]{11}"))continue;v.title=row.getString("title");v.published=OffsetDateTime.parse(row.getString("published")).toInstant().toEpochMilli();v.thumbnail="https://i.ytimg.com/vi/"+v.id+"/hqdefault.jpg";feed.videos.add(v);}return feed;}
 public List<Video> featured(){List<Video> out=new ArrayList<>();for(Video v:videos)if(v.longForm()&&out.size()<3)out.add(v);for(Video v:videos)if(out.size()<3&&!out.contains(v))out.add(v);return out;}
}
