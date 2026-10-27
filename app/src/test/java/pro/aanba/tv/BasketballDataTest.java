package pro.aanba.tv;

import org.junit.Test;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

public class BasketballDataTest {
    String fixture(String name)throws Exception{return new String(getClass().getResourceAsStream("/"+name+".json").readAllBytes(),StandardCharsets.UTF_8);}
    @Test public void realFinalHasTeamMetricsPlayersAndCorrectTeamMapping()throws Exception{
        GameDetails d=GameDetails.parse(fixture("summary-final"),"nba",123);
        assertEquals("401909853",d.eventId);assertEquals("post",d.score.state);assertEquals("110",d.score.scoreFor("MIA"));assertEquals("115",d.score.scoreFor("CHA"));assertEquals("23",d.team("CHA").stat("largestLead"));assertEquals("37-86",d.team("MIA").stat("fieldGoalsMade-fieldGoalsAttempted"));
        assertEquals(14,d.team("MIA").players.size());assertEquals("14",d.team("MIA").players.get(0).stat("PTS"));assertNull(d.team("BOS"));assertEquals(123,d.fetchedAt);
    }
    @Test public void pregameNeverShowsSeasonAveragesAsGameStats()throws Exception{
        GameDetails d=GameDetails.parse(fixture("summary"),"nba",1);assertEquals("pre",d.score.state);for(GameDetails.Team t:d.teams){assertTrue(t.stats.isEmpty());assertTrue(t.players.isEmpty());}
    }
    @Test public void labelsAreMappedByNameNotAssumedColumnOrder()throws Exception{
        JSONObject root=new JSONObject(fixture("summary-final")),set=root.getJSONObject("boxscore").getJSONArray("players").getJSONObject(0).getJSONArray("statistics").getJSONObject(0);
        set.put("labels",new JSONArray().put("AST").put("PTS").put("MIN"));JSONObject row=set.getJSONArray("athletes").getJSONObject(0);row.put("stats",new JSONArray().put("17").put("42").put("39"));
        GameDetails.Player p=GameDetails.parse(root.toString(),"nba",1).team("MIA").players.get(0);assertEquals("17",p.stat("AST"));assertEquals("42",p.stat("PTS"));assertEquals("39",p.stat("MIN"));assertEquals("—",p.stat("STL"));
    }
    @Test public void dnpIsNotZeroMinutesOrZeroPoints()throws Exception{
        JSONObject root=new JSONObject(fixture("summary-final"));JSONObject row=root.getJSONObject("boxscore").getJSONArray("players").getJSONObject(0).getJSONArray("statistics").getJSONObject(0).getJSONArray("athletes").getJSONObject(0);row.put("didNotPlay",true);
        GameDetails.Player p=GameDetails.parse(root.toString(),"nba",1).team("MIA").players.get(0);assertTrue(p.dnp);assertEquals("—",p.stat("PTS"));
    }
    @Test public void dunksCountMadeShotsNotMissesOrDuplicateEvents()throws Exception{
        JSONObject root=new JSONObject(fixture("summary-final"));GameDetails d=GameDetails.parse(root.toString(),"nba",1);assertEquals("2",d.team("MIA").stat("dunksMade"));assertEquals("3",d.team("CHA").stat("dunksMade"));
        JSONArray plays=root.getJSONArray("plays");for(int i=0;i<plays.length();i++)if(plays.getJSONObject(i).getJSONObject("type").getString("text").toLowerCase(Locale.US).contains("dunk")){plays.put(plays.getJSONObject(i));break;}
        assertEquals("2",GameDetails.parse(root.toString(),"nba",1).team("MIA").stat("dunksMade"));
        root.remove("plays");assertFalse(GameDetails.parse(root.toString(),"nba",1).team("MIA").stats.containsKey("dunksMade"));
    }
    @Test public void incompleteOrLivePlayLogDoesNotClaimFinalDunkTotal()throws Exception{
        JSONObject root=new JSONObject(fixture("summary-final"));root.getJSONArray("plays").remove(0);assertFalse(GameDetails.parse(root.toString(),"nba",1).team("MIA").stats.containsKey("dunksMade"));
        root=new JSONObject(fixture("summary-final"));JSONObject status=root.getJSONObject("header").getJSONArray("competitions").getJSONObject(0).getJSONObject("status");status.getJSONObject("type").put("state","in");assertFalse(GameDetails.parse(root.toString(),"nba",1).team("MIA").stats.containsKey("dunksMade"));
    }
    @Test public void historyAndRematchesUseEventTeamsAndDate()throws Exception{
        GameDetails d=GameDetails.parse(fixture("summary-historical"),"nba",1);assertEquals("401656363",d.eventId);assertEquals("BOS",d.team("BOS").code);assertFalse(d.team("BOS").players.isEmpty());
        Match m=new Match("x","A","B","BOS","DAL","NBA","finished","","",d.score.start);assertTrue(d.matches(m));assertFalse(d.matches(new Match("y","A","B","BOS","DAL","NBA","finished","","",d.score.start+86400000)));
    }
    @Test public void conferencesDivisionsAndAliasesAreCompleteAndSorted()throws Exception{
        LeagueTable t=LeagueTable.parse(fixture("standings-div"),11);assertEquals(2,t.conferences.size());Set<String> teams=new HashSet<>();int divisions=0;
        for(LeagueTable.Conference c:t.conferences){assertEquals(15,c.entries.size());int previous=0;for(LeagueTable.Entry e:c.entries){assertTrue(e.rank>=previous);previous=e.rank;teams.add(e.code);}divisions+=c.divisions.size();}
        assertEquals(30,teams.size());assertEquals(6,divisions);for(String alias:new String[]{"GSW","SAS","NOP","WAS","NYK","UTA"})assertNotNull(t.team(alias));assertNull(t.team("XX"));assertEquals(2027,t.year);
    }
    @Test public void calendarKeepsPastAndUpcomingAndParsesNestedScores()throws Exception{
        TeamSchedule s=TeamSchedule.parse(fixture("team-schedule"),7);assertEquals("MIN",s.code);assertTrue(s.games.size()>70);assertTrue(s.games.stream().anyMatch(g->g.state.equals("post")));assertTrue(s.games.stream().anyMatch(g->g.state.equals("pre")));long previous=0;
        for(TeamSchedule.Game g:s.games){assertTrue(g.start>=previous);previous=g.start;assertNotNull(g.homeCode);assertNotNull(g.awayCode);assertTrue(g.homeCode.equals("MIN")||g.awayCode.equals("MIN"));if(g.state.equals("post"))assertTrue(g.homeScore.matches("\\d+"));}
    }
    @Test public void zeroAttemptsDoesNotProduceNanPercentage(){GameDetails.Player p=new GameDetails.Player();p.stats.put("FG","0-0");assertEquals("0-0 · —",p.shooting("FG"));p.stats.put("3PT","2-5");assertEquals("2-5 · 40.0%",p.shooting("3PT"));}
    @Test public void finalCacheIsNotMistakenForFreshLiveData()throws Exception{
        GameDetails d=GameDetails.parse(fixture("summary-final"),"nba",100);assertFalse(d.stale(100+3600000));d.score.state="in";assertTrue(d.stale(100+91000));
    }
    @Test public void historicalCalendarUsesRequestedSeasonNotCurrentTeamRecord()throws Exception{
        TeamSchedule s=TeamSchedule.parse(fixture("team-history-2024-2"),1);assertEquals(2024,s.year);assertEquals(2,s.seasonType);assertEquals("2023-24",s.season);assertEquals("",s.record);assertEquals(82,s.games.size());
        TeamSchedule playoffs=TeamSchedule.parse(fixture("team-history-2024-3"),1);assertEquals(2024,playoffs.year);assertEquals(3,playoffs.seasonType);assertEquals(16,playoffs.games.size());assertEquals("",playoffs.record);
    }
    @Test public void liveSummaryCanUseLastPlayClockWithoutInventingTime()throws Exception{
        JSONObject root=new JSONObject(fixture("summary-final"));root.getJSONObject("header").getJSONArray("competitions").getJSONObject(0).getJSONObject("status").getJSONObject("type").put("state","in").put("description","In Progress");
        JSONObject play=new JSONObject().put("id","last").put("period",new JSONObject().put("number",2)).put("clock",new JSONObject().put("displayValue","6:42"));root.put("plays",new JSONArray().put(play));
        GameDetails d=GameDetails.parse(root.toString(),"nba",1);assertTrue(d.clockFromPlay);assertEquals(2,d.score.period);assertEquals("6:42",d.score.clock);
    }

    @Test public void conferenceGamesBehindDoesNotReuseDivisionLeader()throws Exception{
        LeagueTable divisions=LeagueTable.parse(fixture("standings-div"),1),conferences=LeagueTable.parse(fixture("standings-conferences"),1);
        assertEquals("0.5",divisions.team("DET").stat("conferenceGamesBehind"));
        for(LeagueTable.Conference c:conferences.conferences)for(LeagueTable.Entry e:c.entries){String raw=e.stat("gamesBehind");String derived=divisions.team(e.code).stat("conferenceGamesBehind");assertEquals(raw.equals("-")?"—":raw,derived);}
    }

}
