package pro.aanba.tv;

import org.json.*;
import java.time.OffsetDateTime;
import java.util.*;

public final class TeamSchedule {
    public String id,code,name,record,season;public long fetchedAt;public int year,seasonType;
    public final List<Game> games=new ArrayList<>();
    public static class Game {
        public String id,home,away,homeCode,awayCode,state,homeScore,awayScore;public long start;
        public Match asMatch(){return new Match("espn:"+id,home,away,homeCode,awayCode,"NBA","pre".equals(state)?"upcoming":"in".equals(state)?"live":"finished","","",start);}
        public String title(){return awayCode+"  @  "+homeCode;}
    }
    public static TeamSchedule parse(String json,long fetchedAt)throws JSONException{
        JSONObject root=new JSONObject(json),team=root.getJSONObject("team");TeamSchedule out=new TeamSchedule();out.fetchedAt=fetchedAt;out.id=team.getString("id");out.code=Match.normal(team.getString("abbreviation"));out.name=team.optString("displayName",out.code);
        // ESPN's root season/team record describe the current season even in historical responses.
        JSONObject season=root.optJSONObject("requestedSeason");if(season==null)season=root.optJSONObject("season");out.year=season==null?0:season.optInt("year");out.season=season==null?"":season.optString("displayName");out.seasonType=season==null?2:season.optInt("type",2);
        out.record=out.seasonType==2&&out.season.equals(team.optString("seasonSummary"))?team.optString("recordSummary",""):"";
        JSONArray events=root.optJSONArray("events");if(events!=null)for(int i=0;i<events.length();i++)try{
            JSONObject e=events.getJSONObject(i),c=e.getJSONArray("competitions").getJSONObject(0);Game g=new Game();g.id=e.getString("id");g.start=OffsetDateTime.parse(e.getString("date")).toInstant().toEpochMilli();JSONObject status=c.getJSONObject("status");g.state=status.getJSONObject("type").optString("state","pre");
            JSONArray teams=c.getJSONArray("competitors");for(int j=0;j<teams.length();j++){
                JSONObject row=teams.getJSONObject(j),t=row.getJSONObject("team");String code=Match.normal(t.getString("abbreviation")),name=t.optString("displayName",code);Object score=row.opt("score");String value=score instanceof JSONObject?((JSONObject)score).optString("displayValue","—"):score==null?"—":score.toString();
                if("home".equals(row.optString("homeAway"))){g.home=name;g.homeCode=code;g.homeScore=value;}else{g.away=name;g.awayCode=code;g.awayScore=value;}
            }if(g.homeCode!=null&&g.awayCode!=null)out.games.add(g);
        }catch(Exception ignored){}
        out.games.sort(Comparator.comparingLong(g->g.start));return out;
    }
}
