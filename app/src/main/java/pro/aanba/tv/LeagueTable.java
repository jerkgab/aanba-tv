package pro.aanba.tv;

import org.json.*;
import java.util.*;

public final class LeagueTable {
    public String season;public int year;public long fetchedAt;
    public final List<Conference> conferences=new ArrayList<>();
    public static class Conference {public String name;public final List<Division> divisions=new ArrayList<>();public final List<Entry> entries=new ArrayList<>();}
    public static class Division {public String name;public final List<Entry> entries=new ArrayList<>();}
    public static class Entry {
        public String id,code,name,conference,division;public int rank;
        public final Map<String,String> stats=new HashMap<>();
        public String stat(String key){return stats.getOrDefault(key,"—");}
    }
    public Entry team(String code){for(Conference c:conferences)for(Entry e:c.entries)if(e.code.equals(code))return e;return null;}
    static final Comparator<Entry> ORDER=Comparator.comparingInt(e->e.rank<=0?999:e.rank);
    public static LeagueTable parse(String json,long fetchedAt)throws JSONException {
        JSONObject root=new JSONObject(json);LeagueTable table=new LeagueTable();table.fetchedAt=fetchedAt;JSONObject season=root.getJSONObject("season");table.season=season.optString("displayName");table.year=season.getInt("year");
        JSONArray children=root.getJSONArray("children");for(int i=0;i<children.length();i++){
            JSONObject conf=children.getJSONObject(i);Conference c=new Conference();c.name=conf.getString("name");JSONArray divisions=conf.optJSONArray("children");
            if(divisions!=null)for(int j=0;j<divisions.length();j++){
                JSONObject div=divisions.getJSONObject(j);Division d=new Division();d.name=div.getString("name");readEntries(div,c.name,d.name,d.entries);d.entries.sort(ORDER);c.divisions.add(d);c.entries.addAll(d.entries);
            }
            else readEntries(conf,c.name,"",c.entries);
            c.entries.sort(ORDER);
            if(!c.entries.isEmpty())for(Entry e:c.entries)try{
                Entry leader=c.entries.get(0);double gap=(Double.parseDouble(leader.stat("wins"))-Double.parseDouble(e.stat("wins"))+Double.parseDouble(e.stat("losses"))-Double.parseDouble(leader.stat("losses")))/2;
                e.stats.put("conferenceGamesBehind",gap==0?"—":gap==(int)gap?Integer.toString((int)gap):String.format(Locale.US,"%.1f",gap));
            }catch(NumberFormatException ignored){}
            table.conferences.add(c);
        }
        int size=0;for(Conference c:table.conferences)size+=c.entries.size();if(size==0)throw new JSONException("Таблица пуста");return table;
    }
    private static void readEntries(JSONObject group,String conference,String division,List<Entry> out)throws JSONException{
        JSONObject standings=group.optJSONObject("standings");if(standings==null)return;JSONArray entries=standings.optJSONArray("entries");if(entries==null)return;
        for(int i=0;i<entries.length();i++){
            JSONObject source=entries.getJSONObject(i),t=source.getJSONObject("team");Entry e=new Entry();e.id=t.getString("id");e.code=Match.normal(t.getString("abbreviation"));e.name=t.optString("displayName",e.code);e.conference=conference;e.division=division;
            JSONArray stats=source.optJSONArray("stats");if(stats!=null)for(int j=0;j<stats.length();j++){JSONObject s=stats.getJSONObject(j);e.stats.put(s.optString("name"),s.optString("displayValue",s.optString("summary","—")));if("playoffSeed".equals(s.optString("name")))e.rank=s.optInt("value",0);}out.add(e);
        }
    }
}
