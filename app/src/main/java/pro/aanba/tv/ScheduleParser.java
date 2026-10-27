package pro.aanba.tv;

import org.json.*;
import java.util.*;
import java.util.regex.*;

/** Reads server-rendered event data, never executes scripts from AANBA. */
public final class ScheduleParser {
    public static List<Match> parse(String html) throws JSONException {
        LinkedHashMap<String,Match> events=new LinkedHashMap<>();
        Matcher m=Pattern.compile("self\\.__next_f\\.push\\((.*?)\\)</script>",Pattern.DOTALL).matcher(html);
        StringBuilder flight=new StringBuilder();
        while(m.find()){
            try {JSONArray p=new JSONArray(m.group(1));if(p.length()>1&&p.opt(1) instanceof String)flight.append(p.getString(1));}catch(JSONException ignored){}
        }
        for(String line:flight.toString().split("\n")){
            int colon=line.indexOf(':');if(colon<0)continue;
            String value=line.substring(colon+1);
            try {if(value.startsWith("["))walk(new JSONArray(value),events);else if(value.startsWith("{"))walk(new JSONObject(value),events);}catch(JSONException ignored){}
        }
        // Supports saved / future direct JSON responses as well.
        if(events.isEmpty())try{walk(new JSONTokener(html).nextValue(),events);}catch(JSONException ignored){}
        if(events.isEmpty())throw new JSONException("Расписание изменилось: матчи не найдены");
        List<Match> list=new ArrayList<>(events.values());
        list.sort(Comparator.comparingLong(x->x.start));return list;
    }
    private static void walk(Object node,Map<String,Match> out){
        if(node instanceof JSONObject){
            JSONObject o=(JSONObject)node;
            if(o.has("vkVideoUrl")&&o.has("homeTeam")&&o.has("awayTeam")){Match m=Match.fromJson(o);if(m!=null)out.put(m.id,m);return;}
            Iterator<String> keys=o.keys();while(keys.hasNext())walk(o.opt(keys.next()),out);
        }else if(node instanceof JSONArray){JSONArray a=(JSONArray)node;for(int i=0;i<a.length();i++)walk(a.opt(i),out);}
    }
}
