package pro.aanba.tv;

import org.junit.Test;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static org.junit.Assert.*;

public class DataTest {
    Match match(){return new Match("one","Майами","Шарлотт","MIA","CHA","NBA","upcoming","https://vksport.vkvideo.ru/live-55574239_456428303","",Instant.parse("2026-10-24T22:00:00Z").toEpochMilli());}
    @Test public void parsesCapturedScheduleAndOnlyAanbaEmbeds()throws Exception{
        String html=new String(getClass().getResourceAsStream("/schedule-2026-10-25.html").readAllBytes(),StandardCharsets.UTF_8);
        List<Match> list=ScheduleParser.parse(html);assertTrue(list.size()>=9);
        assertTrue(list.stream().anyMatch(m->m.homeCode.equals("MIA")&&m.awayCode.equals("CHA")));
        assertEquals(list.size(),list.stream().map(m->m.id).distinct().count());
        for(Match m:list)assertTrue(m.embedUrl().startsWith("https://vkvideo.ru/video_ext.php?oid=-55574239&id="));
    }
    @Test public void joinsFlightChunksWithoutRunningScripts()throws Exception{
        String line="7:[\"$\",\"$L1\",null,{\"events\":["+match().toJson()+"]}]\n";int n=line.length()/2;
        String html="<script>self.__next_f.push("+new JSONArray().put(1).put(line.substring(0,n))+")</script><script>self.__next_f.push("+new JSONArray().put(1).put(line.substring(n))+")</script>";
        assertEquals(1,ScheduleParser.parse(html).size());
    }
    @Test public void cacheRoundTripPreservesMatch()throws Exception{
        Match m=match(),restored=ScheduleParser.parse(new JSONArray().put(m.toJson()).toString()).get(0);assertEquals(m.embedUrl(),restored.embedUrl());assertEquals(m.start,restored.start);
    }
    @Test public void rejectsNonAanbaAndInsecureSource()throws Exception{
        JSONObject o=match().toJson();o.put("vkVideoUrl","https://vkvideo.ru/live-123_456");assertNull(Match.fromJson(o));
        o.put("vkVideoUrl","http://vkvideo.ru/live-55574239_456");assertNull(Match.fromJson(o));
        o.put("vkVideoUrl","https://evil.example/live-55574239_456");assertNull(Match.fromJson(o));
    }
    @Test public void espnTimestampWithoutSecondsAndReversedTeamsMatches()throws Exception{
        String json="{\"events\":[{\"id\":\"42\",\"date\":\"2026-10-24T22:00Z\",\"status\":{\"displayClock\":\"6:42\",\"period\":4,\"type\":{\"state\":\"in\",\"description\":\"In Progress\"}},\"competitions\":[{\"competitors\":[{\"team\":{\"abbreviation\":\"CHA\"},\"score\":\"82\",\"linescores\":[{\"value\":22}]},{\"team\":{\"abbreviation\":\"MIA\"},\"score\":\"87\",\"linescores\":[{\"value\":28}]}]}]}]}";
        List<Score> scores=Score.parse(json,"nba",100);assertEquals(1,scores.size());Score s=scores.get(0);
        assertTrue(s.matches(match()));assertEquals("87",s.scoreFor("MIA"));assertEquals("82",s.scoreFor("CHA"));assertEquals("28",s.periodsFor("MIA").get(0));assertEquals("Четверть 4 · 6:42",s.phase());
    }
    @Test public void staleSourceDoesNotShowOldMatchAsLive(){
        Match m=match();assertEquals("pre",m.state(null,m.start-100));assertEquals("post",m.state(null,m.start+6*3600000L));
        Score s=Score.demo(m);s.state="in";assertEquals("in",m.state(s,m.start+1));s.state="post";assertEquals("post",m.state(s,m.start+1));
    }
    @Test public void rematchesAndOtherLeaguesAreNotConfused(){
        Match m=match();Score s=Score.demo(m);s.start+=24*3600000L;assertFalse(s.matches(m));s.start=m.start;s.league="wnba";assertFalse(s.matches(m));
    }
    @Test public void sourceDateUsesAmericanGameDayNotTvDay(){
        Match m=match();assertEquals("20261024",m.espnDate());
        Match midnight=new Match("two","A","B","GS","SA","NBA","upcoming",m.vkUrl,"",Instant.parse("2026-10-25T02:00:00Z").toEpochMilli());
        assertEquals("20261024",midnight.espnDate());assertEquals("GSW",midnight.homeCode);assertEquals("SAS",midnight.awayCode);
    }
    @Test public void parserReportsLayoutChanges()throws Exception{
        try{ScheduleParser.parse("<html>changed</html>");fail("Should report no events");}catch(JSONException expected){}
    }
    @Test public void wnbaAliasesDoNotUseNbaTeamCodes(){
        assertEquals("GSV",Match.normal("GS","wnba"));assertEquals("GSW",Match.normal("GS","nba"));
        assertEquals("NYL",Match.normal("NY","WNBA"));assertEquals("NYK",Match.normal("NY","NBA"));
    }
    @Test public void scoreStalenessIsExplicit(){Score s=Score.demo(match());assertFalse(s.stale(s.fetchedAt+1000));assertTrue(s.stale(s.fetchedAt+91000));}
    @Test public void recordsUseOverallNotHomeOrRoad()throws Exception{
        JSONObject team=new JSONObject("{\"records\":[{\"type\":\"home\",\"summary\":\"1-0\"},{\"type\":\"total\",\"summary\":\"3-15\"},{\"type\":\"road\",\"summary\":\"2-15\"}]}");
        assertEquals("3-15",Score.overallRecord(team));
        assertEquals("",Score.overallRecord(new JSONObject("{\"records\":[{\"type\":\"home\",\"summary\":\"1-0\"}]}")));
    }
    @Test public void missingOrMalformedRecordIsNotInvented()throws Exception{
        assertEquals("",Score.overallRecord(new JSONObject()));
        assertEquals("",Score.overallRecord(new JSONObject("{\"records\":[{\"type\":\"total\",\"summary\":\"unknown\"}]}")));
    }
    @Test public void missingTeamDoesNotReceiveOtherTeamsStats(){
        Score s=Score.demo(match());s.aRecord="3-15";s.bRecord="12-6";
        assertEquals("3-15",s.recordFor(s.aCode));assertEquals("12-6",s.recordFor(s.bCode));
        assertEquals("",s.recordFor("NOP"));assertEquals("—",s.scoreFor("NOP"));assertTrue(s.periodsFor("NOP").isEmpty());
    }
}
