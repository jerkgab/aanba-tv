package pro.aanba.tv;
import org.json.*;
import java.util.*;

/** Local recording positions keyed by the immutable VK owner/video pair, never by list order. */
final class WatchHistory {
 static final class Entry {String key;double seconds,duration;long updatedAt;boolean completed;double fraction(){return duration>0?Math.min(1,Math.max(0,seconds/duration)):0;}boolean resumable(){return !completed&&seconds>=15;}}
 final Map<String,Entry> entries=new HashMap<>();
 WatchHistory(String json){try{JSONArray rows=new JSONArray(json);for(int i=0;i<rows.length();i++){JSONObject o=rows.getJSONObject(i);Entry e=new Entry();e.key=o.getString("key");e.seconds=o.getDouble("seconds");e.duration=o.getDouble("duration");e.updatedAt=o.getLong("at");e.completed=o.optBoolean("completed");if(valid(e.seconds,e.duration))entries.put(e.key,e);}}catch(Exception ignored){}}
 static boolean valid(double time,double duration){return Double.isFinite(time)&&Double.isFinite(duration)&&time>=0&&duration>=30&&time<=duration;}
 static String key(Match m){java.util.regex.Matcher x=java.util.regex.Pattern.compile("(?:live|video)(-55574239)_(\\d+)").matcher(m.vkUrl);return x.find()?x.group(1)+"_"+x.group(2):"";}
 Entry get(Match m){return entries.get(key(m));}
 boolean save(String key,double seconds,double duration,long now){if(key.isEmpty()||!valid(seconds,duration))return false;Entry e=new Entry();e.key=key;e.seconds=seconds;e.duration=duration;e.updatedAt=now;e.completed=duration-seconds<=Math.min(30,duration*.02);entries.put(key,e);while(entries.size()>60){Entry oldest=Collections.min(entries.values(),Comparator.comparingLong(v->v.updatedAt));entries.remove(oldest.key);}return true;}
 double restore(Entry e,double start,double end){if(e==null||!Double.isFinite(end)||end<=start)return -1;if(Math.abs(e.duration-end)>Math.max(600,e.duration*.2))return -1;return e.completed?Math.max(0,start):Math.max(start,Math.min(end-1,e.seconds));}
 String serialize(){JSONArray rows=new JSONArray();for(Entry e:entries.values())try{rows.put(new JSONObject().put("key",e.key).put("seconds",e.seconds).put("duration",e.duration).put("at",e.updatedAt).put("completed",e.completed));}catch(JSONException ignored){}return rows.toString();}
}
