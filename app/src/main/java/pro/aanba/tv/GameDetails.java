package pro.aanba.tv;

import org.json.*;
import java.time.OffsetDateTime;
import java.util.*;

/** ESPN event summary. Pre-game averages never masquerade as statistics of this game. */
public final class GameDetails {
    public String eventId,league; public long fetchedAt; public Score score;public boolean clockFromPlay;
    public final List<Team> teams=new ArrayList<>();
    public static final String[][] METRICS={
        {"fieldGoalsMade-fieldGoalsAttempted","Броски с игры"},{"fieldGoalPct","Попадания с игры, %"},
        {"threePointFieldGoalsMade-threePointFieldGoalsAttempted","Трёхочковые"},{"threePointFieldGoalPct","Трёхочковые, %"},
        {"freeThrowsMade-freeThrowsAttempted","Штрафные"},{"freeThrowPct","Штрафные, %"},
        {"totalRebounds","Подборы"},{"offensiveRebounds","В нападении"},{"defensiveRebounds","В защите"},
        {"assists","Передачи"},{"steals","Перехваты"},{"blocks","Блок-шоты"},{"totalTurnovers","Потери"},
        {"fouls","Фолы"},{"pointsInPaint","Очки из краски"},{"fastBreakPoints","Быстрый отрыв"},
        {"turnoverPoints","Очки после потерь"},{"largestLead","Максимальный отрыв"},{"dunksMade","Данки · забито"}
    };
    public static class Team {
        public String id,code,name;
        public final Map<String,String> stats=new LinkedHashMap<>();
        public final List<Player> players=new ArrayList<>();
        public String stat(String key){return stats.getOrDefault(key,"—");}
    }
    public static class Player {
        public String id,name,shortName,jersey,position,reason; public boolean starter,active,dnp;
        public final Map<String,String> stats=new LinkedHashMap<>();
        public String stat(String key){return stats.getOrDefault(key,"—");}
        public String shooting(String key){String value=stat(key);String[] n=value.split("-");
            if(n.length!=2)return value;
            try{int made=Integer.parseInt(n[0]),attempts=Integer.parseInt(n[1]);return value+(attempts>0?String.format(Locale.US," · %.1f%%",made*100d/attempts):" · —");}catch(Exception e){return value;}}
    }
    public Team team(String code){for(Team t:teams)if(t.code.equals(code))return t;return null;}
    public boolean matches(Match m){return score!=null&&score.matches(m);}
    public boolean stale(long now){return now-fetchedAt>(score!=null&&"post".equals(score.state)?86400000L:90000L);}
    public static GameDetails parse(String json,String league,long fetchedAt)throws JSONException {
        JSONObject root=new JSONObject(json),header=root.getJSONObject("header"),competition=header.getJSONArray("competitions").getJSONObject(0);
        GameDetails result=new GameDetails();result.eventId=header.getString("id");result.league=league;result.fetchedAt=fetchedAt;
        JSONObject event=new JSONObject().put("id",result.eventId).put("date",competition.getString("date")).put("status",competition.getJSONObject("status")).put("competitions",new JSONArray().put(competition));
        List<Score> scores=Score.parse(new JSONObject().put("events",new JSONArray().put(event)).toString(),league,fetchedAt);
        if(scores.size()!=1)throw new JSONException("Нет команд матча");result.score=scores.get(0);
        JSONObject box=root.optJSONObject("boxscore");if(box==null)return result;
        JSONArray teams=box.optJSONArray("teams");if(teams!=null)for(int i=0;i<teams.length();i++){
            JSONObject source=teams.getJSONObject(i),meta=source.getJSONObject("team");Team t=new Team();t.id=meta.getString("id");t.code=Match.normal(meta.getString("abbreviation"),league);t.name=meta.optString("displayName",t.code);
            if(!"pre".equals(result.score.state)){
                JSONArray stats=source.optJSONArray("statistics");if(stats!=null)for(int j=0;j<stats.length();j++){JSONObject s=stats.getJSONObject(j);String key=s.optString("name"),value=s.optString("displayValue","");if(!value.isEmpty())t.stats.put(key,value);}
                if(!t.stats.containsKey("totalTurnovers")&&t.stats.containsKey("turnovers"))t.stats.put("totalTurnovers",t.stats.get("turnovers"));
            }result.teams.add(t);
        }
        if("pre".equals(result.score.state))return result;
        JSONArray players=box.optJSONArray("players");if(players!=null)for(int i=0;i<players.length();i++){
            JSONObject group=players.getJSONObject(i);Team t=result.team(Match.normal(group.getJSONObject("team").getString("abbreviation"),league));if(t==null)continue;
            JSONArray sets=group.optJSONArray("statistics");if(sets==null)continue;Set<String> seen=new HashSet<>();
            for(int j=0;j<sets.length();j++){
                JSONObject set=sets.getJSONObject(j);JSONArray labels=set.optJSONArray("labels"),athletes=set.optJSONArray("athletes");if(labels==null||athletes==null)continue;
                for(int k=0;k<athletes.length();k++){
                    JSONObject row=athletes.getJSONObject(k),a=row.getJSONObject("athlete");String id=a.getString("id");if(!seen.add(id))continue;
                    Player p=new Player();p.id=id;p.name=a.optString("displayName","");p.shortName=a.optString("shortName",p.name);p.jersey=a.optString("jersey","");JSONObject pos=a.optJSONObject("position");p.position=pos==null?"":pos.optString("abbreviation","");p.starter=row.optBoolean("starter");p.active=row.optBoolean("active");p.dnp=row.optBoolean("didNotPlay");p.reason=row.optString("reason","");
                    JSONArray values=row.optJSONArray("stats");if(!p.dnp&&values!=null)for(int n=0;n<Math.min(labels.length(),values.length());n++)p.stats.put(labels.getString(n).toUpperCase(Locale.US),values.optString(n,"—"));t.players.add(p);
                }
            }
        }
        // Count only made dunks with a team attribution in the supplied play log.
        // Omit the metric unless the final log has all four quarters (never label a partial log as complete).
        JSONArray plays=root.optJSONArray("plays");boolean[] periods=new boolean[5];boolean startSeen=false,endSeen=false;Map<String,Integer> dunks=new HashMap<>();Set<String> seenPlays=new HashSet<>();
        if("in".equals(result.score.state)&&result.score.period<=0&&plays!=null)for(int i=plays.length()-1;i>=0;i--){
            JSONObject play=plays.optJSONObject(i);if(play==null)continue;JSONObject period=play.optJSONObject("period"),clock=play.optJSONObject("clock");
            if(period!=null&&clock!=null&&period.optInt("number")>0){result.score.period=period.optInt("number");result.score.clock=clock.optString("displayValue","");result.clockFromPlay=true;break;}
        }
        if(plays!=null)for(int i=0;i<plays.length();i++){
            JSONObject play=plays.getJSONObject(i),period=play.optJSONObject("period");int n=period==null?0:period.optInt("number");if(n>0&&n<5)periods[n]=true;
            JSONObject type=play.optJSONObject("type"),team=play.optJSONObject("team");String kind=type==null?"":type.optString("text").toLowerCase(Locale.US);
            JSONObject clock=play.optJSONObject("clock");if(n==1&&clock!=null&&clock.optString("displayValue").startsWith("12:00"))startSeen=true;if("end game".equals(kind))endSeen=true;
            if(seenPlays.add(play.optString("id","row"+i))&&team!=null&&kind.contains("dunk")&&play.optBoolean("scoringPlay")&&play.optInt("scoreValue")>0)dunks.merge(team.optString("id"),1,Integer::sum);
        }
        if("post".equals(result.score.state)&&startSeen&&endSeen&&periods[1]&&periods[2]&&periods[3]&&periods[4])for(Team t:result.teams)t.stats.put("dunksMade",Integer.toString(dunks.getOrDefault(t.id,0)));
        return result;
    }
    /** Explicit diagnostic data, never used for a real broadcast. */
    static GameDetails demo(Match m){
        GameDetails d=new GameDetails();d.eventId="demo";d.league=m.espnLeague();d.fetchedAt=System.currentTimeMillis();d.score=Score.demo(m);
        for(int side=0;side<2;side++){
            Team t=new Team();t.id="demo"+side;t.code=side==0?m.homeCode:m.awayCode;t.name=side==0?m.home:m.away;
            String[] values=side==0?new String[]{"31-68","46","12-30","40","13-17","76","38","8","30","23","7","4","11","14","32","14","9","12"}:new String[]{"29-70","41","11-32","34","13-18","72","35","7","28","19","6","3","13","16","28","12","8","6"};
            for(int i=0;i<values.length;i++)t.stats.put(METRICS[i][0],values[i]);
            int[] points=side==0?new int[]{24,20,18,15,10}:new int[]{22,19,17,14,10};
            for(int i=0;i<5;i++){Player p=new Player();p.id=t.id+"-"+i;p.name="Демо-игрок "+(i+1);p.shortName=p.name;p.jersey=Integer.toString(i+1);p.position="";p.reason="";p.starter=true;
                p.stats.put("MIN","28");p.stats.put("PTS",Integer.toString(points[i]));for(String key:new String[]{"REB","AST","STL","BLK","TO","OREB","DREB","PF","+/-"})p.stats.put(key,Integer.toString(i%4));
                p.stats.put("FG","6-12");p.stats.put("3PT","2-5");p.stats.put("FT","3-4");t.players.add(p);}
            d.teams.add(t);
        }return d;
    }
}
