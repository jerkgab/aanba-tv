package pro.aanba.tv;
import org.json.*;
import java.util.*;
public final class SeasonStats {
 public String teamId,code,name,season;public int year,type;public long fetchedAt;
 public final Map<String,String> values=new LinkedHashMap<>();public final Map<String,Double> numbers=new HashMap<>();
 public String value(String key){return values.getOrDefault(key,"—");}
 public static SeasonStats parse(String json,long at)throws JSONException{
  JSONObject root=new JSONObject(json),team=root.getJSONObject("team"),s=root.optJSONObject("requestedSeason");if(s==null)s=root.getJSONObject("season");SeasonStats out=new SeasonStats();out.fetchedAt=at;out.teamId=team.getString("id");out.code=Match.normal(team.getString("abbreviation"));out.name=team.optString("displayName",out.code);out.year=s.getInt("year");out.type=s.optInt("type",2);out.season=s.optString("displayName");
  JSONArray categories=root.getJSONObject("results").getJSONObject("stats").getJSONArray("categories");for(int i=0;i<categories.length();i++){JSONArray stats=categories.getJSONObject(i).getJSONArray("stats");for(int j=0;j<stats.length();j++){JSONObject stat=stats.getJSONObject(j);String key=stat.getString("name");out.values.put(key,stat.optString("displayValue","—"));double value=stat.optDouble("value",Double.NaN);if(Double.isFinite(value))out.numbers.put(key,value);}}
  out.ratio("efgPct",out.number("fieldGoalsMade")+.5*out.number("threePointFieldGoalsMade"),out.number("fieldGoalsAttempted"),100);
  out.ratio("trueShootingPct",out.number("points"),2*(out.number("fieldGoalsAttempted")+.44*out.number("freeThrowsAttempted")),100);
  out.ratio("threeAttemptRate",out.number("threePointFieldGoalsAttempted"),out.number("fieldGoalsAttempted"),100);
  out.ratio("ftAttemptRate",out.number("freeThrowsAttempted"),out.number("fieldGoalsAttempted"),100);
  return out;
 }
 double number(String name){return numbers.getOrDefault(name,Double.NaN);}
 void ratio(String key,double numerator,double denominator,double factor){if(Double.isFinite(numerator)&&Double.isFinite(denominator)&&denominator>0){double value=numerator/denominator*factor;numbers.put(key,value);values.put(key,String.format(Locale.US,"%.1f",value));}}
}
