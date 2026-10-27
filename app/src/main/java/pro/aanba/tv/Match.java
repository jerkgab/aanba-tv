package pro.aanba.tv;

import org.json.JSONObject;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Match {
    public final String id, home, away, homeCode, awayCode, league, sourceStatus, vkUrl, commentator;
    public final long start;
    public Match(String id, String home, String away, String hc, String ac, String league, String status, String vk, String commentator, long start) {
        this.id=id; this.home=home; this.away=away; homeCode=normal(hc,league); awayCode=normal(ac,league);
        this.league=league; sourceStatus=status; vkUrl=vk; this.commentator=commentator; this.start=start;
    }
    public static Match fromJson(JSONObject o) {
        JSONObject h=o.optJSONObject("homeTeam"), a=o.optJSONObject("awayTeam");
        if(h==null||a==null) return null;
        String vk=o.optString("vkVideoUrl", "");
        if(!Pattern.compile("https://(?:vksport\\.)?(?:vkvideo\\.ru|vk\\.com)/(?:live|video)-55574239_\\d+(?:[?].*)?").matcher(vk).matches()) return null;
        long start;
        try { start=Instant.parse(o.getString("datetime")).toEpochMilli(); } catch(Exception e) {return null;}
        String l=o.optString("league","NBA");
        if(l.equals("НБА"))l="NBA";
        String c=o.optString("commentator", ""); if(c.startsWith("$"))c="";
        return new Match(o.optString("id",vk),h.optString("name"),a.optString("name"),h.optString("short"),a.optString("short"),l,o.optString("status","upcoming"),vk,c,start);
    }
    public JSONObject toJson() {
        JSONObject o=new JSONObject();
        try {o.put("id",id).put("homeTeam",new JSONObject().put("name",home).put("short",homeCode)).put("awayTeam",new JSONObject().put("name",away).put("short",awayCode)).put("league",league).put("status",sourceStatus).put("datetime",Instant.ofEpochMilli(start).toString()).put("vkVideoUrl",vkUrl).put("commentator",commentator);}catch(Exception ignored){}
        return o;
    }
    public String embedUrl() {
        Matcher m=Pattern.compile("(?:live|video)(-55574239)_(\\d+)").matcher(vkUrl);
        if(!m.find())throw new IllegalStateException("Неверная ссылка VK");
        return "https://vkvideo.ru/video_ext.php?oid="+m.group(1)+"&id="+m.group(2)+"&hd=3&autoplay=1&js_api=1";
    }
    public String startLabel() {return DateTimeFormatter.ofPattern("d MMM · HH:mm",new Locale("ru")).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(start));}
    public String espnLeague(){return league.equalsIgnoreCase("WNBA")?"wnba":"nba";}
    public String espnDate(){return DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.of("America/New_York")).format(Instant.ofEpochMilli(start));}
    public String key(){return espnLeague()+":"+(homeCode.compareTo(awayCode)<0?homeCode+":"+awayCode:awayCode+":"+homeCode);}
    public String state(Score score,long now){
        if(score!=null&&Math.abs(score.start-start)<12*3600000L)return score.state;
        if("finished".equals(sourceStatus)||"ended".equals(sourceStatus)||now>start+5*3600000L)return "post";
        if("live".equals(sourceStatus)&&now>=start&&now<start+5*3600000L)return "in";
        return "pre";
    }
    public static String normal(String code,String league){
        if("WNBA".equalsIgnoreCase(league)){
            switch(code.toUpperCase(Locale.US)){
                case "GS":case "GSW":return "GSV";
                case "NY":return "NYL";
                case "LA":return "LAS";
                case "LV":return "LVA";
                default:return normal(code);
            }
        }
        return normal(code);
    }
    public static String normal(String code){
        switch(code.toUpperCase(Locale.US)) {
            case "GS":return "GSW";case "NY":return "NYK";case "SA":return "SAS";case "NO":return "NOP";
            case "WSH":return "WAS";case "PHO":return "PHX";case "BRK":return "BKN";case "UTAH":return "UTA";
            default:return code.toUpperCase(Locale.US);
        }
    }
}
