package pro.aanba.tv;
import android.content.SharedPreferences;
import java.util.*;
final class UserLibrary {
 final SharedPreferences prefs;final WatchHistory history;final Set<String> favorites;
 UserLibrary(SharedPreferences prefs){this.prefs=prefs;history=new WatchHistory(prefs.getString("watch-history","[]"));favorites=new HashSet<>(prefs.getStringSet("favorite-teams",Collections.emptySet()));}
 static String teamKey(String league,String code){return league.toLowerCase(Locale.US)+":"+Match.normal(code,league);}
 boolean favorite(String league,String code){return favorites.contains(teamKey(league,code));}
 boolean favorite(Match match){return favorite(match.espnLeague(),match.homeCode)||favorite(match.espnLeague(),match.awayCode);}
 void toggle(String league,String code){String key=teamKey(league,code);if(!favorites.remove(key))favorites.add(key);prefs.edit().putStringSet("favorite-teams",new HashSet<>(favorites)).apply();}
 void progress(Match m,double time,double duration){if(history.save(WatchHistory.key(m),time,duration,System.currentTimeMillis()))prefs.edit().putString("watch-history",history.serialize()).apply();}
 void restart(Match m,double duration){progress(m,0,duration);}
}
