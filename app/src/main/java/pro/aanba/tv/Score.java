package pro.aanba.tv;

import org.json.*;
import java.time.Instant;
import java.util.*;

public final class Score {
    public String eventId, league, aCode, bCode, aScore, bScore, state, clock, detail;
    public long start, fetchedAt;
    public String aRecord="", bRecord="";
    public int period;
    public final List<String> aPeriods=new ArrayList<>(),bPeriods=new ArrayList<>();
    public String key(){return league+":"+(aCode.compareTo(bCode)<0?aCode+":"+bCode:bCode+":"+aCode);}
    public boolean matches(Match m){return key().equals(m.key())&&Math.abs(start-m.start)<12*3600000L;}
    public String scoreFor(String code){return code.equals(aCode)?aScore:code.equals(bCode)?bScore:"—";}
    public String recordFor(String code){return code.equals(aCode)?aRecord:code.equals(bCode)?bRecord:"";}
    public List<String> periodsFor(String code){return code.equals(aCode)?aPeriods:code.equals(bCode)?bPeriods:Collections.emptyList();}
    public boolean stale(long now){return now-fetchedAt>90000L;}
    public String phase(){
        String lower=detail.toLowerCase(Locale.US);
        if(lower.contains("postpon"))return "Матч перенесён";
        if(lower.contains("cancel"))return "Матч отменён";
        if(lower.contains("delay"))return "Начало задерживается";
        if(lower.contains("suspend"))return "Матч приостановлен";
        if("pre".equals(state))return "Матч ещё не начался";
        if("post".equals(state))return "Матч завершён";
        if("Halftime".equalsIgnoreCase(detail)||detail.toLowerCase(Locale.US).contains("half"))return "Большой перерыв";
        if(period<=0)return "Матч идёт"+(clock.isEmpty()?"":" · "+clock);
        return (period>4?"Овертайм "+(period-4):"Четверть "+period)+(clock.isEmpty()?"":" · "+clock);
    }
    public static List<Score> parse(String json,String league,long fetchedAt) throws JSONException {
        List<Score> result=new ArrayList<>();
        JSONArray events=new JSONObject(json).optJSONArray("events");if(events==null)return result;
        for(int i=0;i<events.length();i++){
            try {
                JSONObject e=events.getJSONObject(i),c=e.getJSONArray("competitions").getJSONObject(0),st=e.getJSONObject("status");
                JSONArray teams=c.getJSONArray("competitors");if(teams.length()!=2)continue;
                JSONObject a=teams.getJSONObject(0),b=teams.getJSONObject(1);
                Score s=new Score();s.eventId=e.getString("id");s.league=league;s.fetchedAt=fetchedAt;
                s.start=java.time.OffsetDateTime.parse(e.getString("date")).toInstant().toEpochMilli();
                s.aCode=Match.normal(a.getJSONObject("team").getString("abbreviation"),league);s.bCode=Match.normal(b.getJSONObject("team").getString("abbreviation"),league);
                s.aScore=a.optString("score","—");s.bScore=b.optString("score","—");
                s.aRecord=overallRecord(a);s.bRecord=overallRecord(b);
                s.state=st.getJSONObject("type").optString("state","pre");s.detail=st.getJSONObject("type").optString("description","");
                s.clock=st.optString("displayClock","");s.period=st.optInt("period",0);
                addPeriods(a,s.aPeriods);addPeriods(b,s.bPeriods);result.add(s);
            }catch(Exception ignored){}
        }
        return result;
    }
    private static void addPeriods(JSONObject t,List<String> out){JSONArray p=t.optJSONArray("linescores");if(p!=null)for(int j=0;j<p.length();j++){JSONObject q=p.optJSONObject(j);out.add(q==null?"—":Integer.toString(q.optInt("value",0)));}}
    static String overallRecord(JSONObject team){
        JSONArray records=team.optJSONArray("records");if(records==null)return "";
        for(int i=0;i<records.length();i++){
            JSONObject record=records.optJSONObject(i);if(record==null)continue;
            if(!"total".equals(record.optString("type"))&&!"overall".equalsIgnoreCase(record.optString("name")))continue;
            String value=record.optString("summary","").trim();
            if(value.matches("\\d{1,3}-\\d{1,3}"))return value;
        }
        return "";
    }
    public static Score demo(Match match){
        Score s=new Score();s.eventId="demo";s.league=match.espnLeague();s.aCode=match.homeCode;s.bCode=match.awayCode;
        s.aScore="87";s.bScore="82";s.state="in";s.clock="06:42";s.detail="In Progress";s.period=4;s.start=match.start;s.fetchedAt=System.currentTimeMillis();
        s.aPeriods.addAll(Arrays.asList("28","24","23","12"));s.bPeriods.addAll(Arrays.asList("22","27","21","12"));return s;
    }
}
