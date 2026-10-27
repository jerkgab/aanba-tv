package pro.aanba.tv;

import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.util.*;

/** Standings → team calendar → native box score. All data is loaded on the TV. */
final class LeagueScreen {
    final MainActivity a;LeagueTable table;LeagueTable.Entry team;TeamSchedule schedule;TeamSchedule.Game game;SeasonStats seasonStats;int teamTab=0;
    StatsPanel detailPanel;LinearLayout page;int group=0,year=0,seasonType=2,gameFilter=0,generation=0;long requestedAt=0;
    String status="Загружаем ESPN…";String savedTeamFocus="",savedGameFocus="";
    LeagueScreen(MainActivity a){this.a=a;}
    boolean active(){return a.leagueScreen==this&&!a.isDestroyed()&&!a.isFinishing()&&a.watching==null;}
    void start(){drawTable();loadTable();}
    void refresh(){if(System.currentTimeMillis()-requestedAt<20000)return;if(game!=null)loadGame();else if(System.currentTimeMillis()-requestedAt>300000){if(team==null)loadTable();else loadTeam();}}
    void loadTable(){int token=generation;requestedAt=System.currentTimeMillis();a.repository.standings((value,stale,error)->{if(!active()||token!=generation||team!=null)return;if(value!=null){table=value;year=table.year;}status=error!=null?error:value==null?"Нет данных":(stale?"Сохранённая таблица · ":"ESPN · ")+a.time(value.fetchedAt);drawTable();});}
    void loadTeam(){if(team==null)return;if(teamTab==1){loadSeasonStats();return;}int token=generation,requestedYear=year,requestedType=seasonType;String requestedTeam=team.id;requestedAt=System.currentTimeMillis();a.repository.schedule(team.id,year,seasonType,(value,stale,error)->{if(!active()||token!=generation||game!=null)return;if(value!=null&&(!requestedTeam.equals(value.id)||value.year!=requestedYear||value.seasonType!=requestedType)){status="Источник не вернул выбранный сезон";drawTeam();return;}if(value!=null)schedule=value;status=error!=null?error:value==null?"Нет данных":(stale?"Сохранённый календарь · ":"ESPN · ")+a.time(value.fetchedAt);drawTeam();});}
    void openTeam(LeagueTable.Entry entry){savedTeamFocus="team-"+entry.id;team=entry;year=table.year;seasonType=2;gameFilter=0;teamTab=0;seasonStats=null;schedule=null;game=null;detailPanel=null;generation++;status="Загружаем календарь…";drawTeam();loadTeam();}
    void loadGame(){if(game==null||detailPanel==null)return;int token=generation;String event=game.id;requestedAt=System.currentTimeMillis();a.repository.summary("nba",event,"post".equals(game.state),(value,stale,error)->{
        if(!active()||token!=generation||game==null||!event.equals(game.id))return;
        if(value!=null&&(!value.eventId.equals(event)||!value.matches(game.asMatch()))){status="Источник вернул другой матч";detailPanel.bind(game.asMatch(),null,null,false,status);return;}
        status=error!=null?error:stale?"Сохранённые данные":"";detailPanel.bind(game.asMatch(),value==null?null:value.score,value,false,status);
    });}
    void openGame(TeamSchedule.Game selected){game=selected;savedGameFocus="game-"+selected.id;generation++;status="";drawGame();loadGame();a.ui.removeCallbacks(a.poll);if(a.foreground)a.ui.postDelayed(a.poll,20000);}
    void close(){a.leagueScreen=null;a.renderHome();}
    void back(){generation++;if(game!=null){game=null;detailPanel=null;status="ESPN · календарь команды";drawTeam();}else if(team!=null){team=null;schedule=null;status="ESPN · таблица сезона";drawTable();focus(savedTeamFocus);}else close();}
    void base(String title,String subtitle){
        detailPanel=null;a.root=new FrameLayout(a);a.root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff171b29,MainActivity.BG}));a.setContentView(a.root);
        ScrollView scroll=new ScrollView(a);scroll.setVerticalScrollBarEnabled(false);a.root.addView(scroll,new FrameLayout.LayoutParams(-1,-1));page=a.column();page.setPadding(a.dp(32),a.dp(20),a.dp(32),a.dp(24));scroll.addView(page,new ScrollView.LayoutParams(-1,-2));
        LinearLayout header=a.row();header.setGravity(Gravity.CENTER_VERTICAL);page.addView(header);header.addView(a.button("‹ Назад","league-back",this::back));a.gap(header,18);header.addView(a.text(title,23,MainActivity.TEXT,true));
        header.addView(new Space(a),new LinearLayout.LayoutParams(0,1,1));header.addView(a.button("Обновить","league-refresh",()->{requestedAt=0;if(game!=null)loadGame();else if(team==null)loadTable();else loadTeam();}));
        TextView sub=a.text(subtitle,10,MainActivity.MUTED,false);sub.setPadding(0,a.dp(10),0,a.dp(16));page.addView(sub);
    }
    String focusTag(){View view=a.getCurrentFocus();return view!=null&&view.getTag() instanceof String?(String)view.getTag():"";}
    void focus(String tag){View next=a.root.findViewWithTag(tag);if(next!=null)next.requestFocus();}
    void drawTable(){
        if(!active())return;String restore=focusTag();base("Таблица NBA"+(table==null?"":" · "+table.season),status);
        LinearLayout filters=a.row();page.addView(filters);String[] names={"Восток","Запад","Дивизионы","Аналитика","Избранные"};for(int i=0;i<names.length;i++){final int n=i;Button b=a.button(names[i],"table-group-"+i,()->{group=n;drawTable();focus("table-group-"+n);});if(group==i)b.setBackground(a.focusBackground(0xff4b5060));filters.addView(b);a.gap(filters,8);}a.gap(page,12);
        if(table==null){page.addView(a.text(status,16,MainActivity.MUTED,false));focus("league-refresh");return;}
        if(group<2){LeagueTable.Conference conf=null;for(LeagueTable.Conference c:table.conferences)if((group==0&&c.name.toLowerCase(Locale.US).contains("east"))||(group==1&&c.name.toLowerCase(Locale.US).contains("west")))conf=c;if(conf!=null)tableRows(conf.entries,false);}
        else if(group==3)analytics();else if(group==4)favorites();else for(LeagueTable.Conference c:table.conferences){TextView conf=a.text(c.name.toLowerCase(Locale.US).contains("east")?"ВОСТОК":"ЗАПАД",11,MainActivity.MUTED,true);conf.setPadding(0,a.dp(14),0,a.dp(12));page.addView(conf);for(LeagueTable.Division d:c.divisions){page.addView(a.text(divisionName(d.name),17,MainActivity.TEXT,true));a.gap(page,7);tableRows(d.entries,true);a.gap(page,17);}}
        a.gap(page,12);page.addView(a.text("W / L — победы / поражения · PCT — доля побед · GB — отставание · L10 — последние 10 · STRK — серия · PPG — очки за матч. Ничьих в NBA нет.",10,MainActivity.MUTED,false));
        focus(restore);if(a.getCurrentFocus()==null)focus("table-group-"+group);
    }
    void tableRows(List<LeagueTable.Entry> entries,boolean division){
        LinearLayout labels=a.row();labels.setPadding(a.dp(12),0,a.dp(12),a.dp(8));page.addView(labels);labels.addView(a.text("#   КОМАНДА",10,MainActivity.MUTED,true),new LinearLayout.LayoutParams(0,-2,1));String[] names={"W","L","PCT","GB","L10","STRK","PPG"};for(String name:names)cell(labels,name,10,MainActivity.MUTED,true,58);
        int n=0;for(LeagueTable.Entry e:entries){n++;LinearLayout row=a.row();row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(a.dp(12),a.dp(4),a.dp(12),a.dp(4));row.setFocusable(true);row.setFocusableInTouchMode(true);row.setClickable(true);row.setTag("team-"+e.id);row.setContentDescription(e.name+", "+e.stat("wins")+" побед, "+e.stat("losses")+" поражений");
            GradientDrawable bg=a.round(n%2==0?0xff222530:0xff1b1e27,11);row.setBackground(bg);row.setOnFocusChangeListener((v,f)->bg.setStroke(a.dp(f?2:0),MainActivity.TEXT));row.setOnClickListener(v->openTeam(e));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.bottomMargin=a.dp(3);page.addView(row,rp);
            cell(row,Integer.toString(division?n:e.rank),12,MainActivity.MUTED,true,23);row.addView(badge(e.code,20),new LinearLayout.LayoutParams(a.dp(20),a.dp(20)));a.gap(row,10);TextView name=a.text((a.library.favorite("nba",e.code)?"★ ":"")+e.name,12,MainActivity.TEXT,true);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            for(String key:new String[]{"wins","losses","winPercent","gamesBehind","Last Ten Games","streak","avgPointsFor"})cell(row,e.stat(!division&&key.equals("gamesBehind")?"conferenceGamesBehind":key),12,MainActivity.TEXT,key.equals("wins"),58);
        }
    }
    void drawTeam(){
        if(!active()||team==null||game!=null)return;String restore=focusTag();base(team.name,status);LinearLayout intro=a.row();intro.setGravity(Gravity.CENTER_VERTICAL);page.addView(intro);intro.addView(badge(team.code,52),new LinearLayout.LayoutParams(a.dp(52),a.dp(52)));a.gap(intro,16);LinearLayout info=a.column();intro.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        info.addView(a.text((year-1)+"–"+year+"  /  "+(seasonType==2?"Регулярный сезон":"Плей-офф"),17,MainActivity.TEXT,true));a.gap(info,5);info.addView(a.text(divisionName(team.division)+(schedule==null||schedule.record.isEmpty()?"":" · "+schedule.record),12,MainActivity.MUTED,false));intro.addView(a.button(a.library.favorite("nba",team.code)?"★ В избранном":"☆ В избранное","team-favorite",()->{a.library.toggle("nba",team.code);drawTeam();focus("team-favorite");}));a.gap(page,12);
        LinearLayout choices=a.row();page.addView(choices);choices.addView(a.button("‹ Сезон","season-prev",()->changeSeason(-1)));a.gap(choices,8);choices.addView(a.button("Сезон ›","season-next",()->changeSeason(1)));a.gap(choices,8);choices.addView(a.button(seasonType==2?"Плей-офф":"Регулярный сезон","season-type",()->{seasonType=seasonType==2?3:2;reloadTeam();}));a.gap(page,10);
        LinearLayout tabs=a.row();page.addView(tabs);String[] sections={"Матчи","Показатели"};for(int i=0;i<2;i++){final int selected=i;Button tab=a.button(sections[i],"team-tab-"+i,()->{teamTab=selected;drawTeam();focus("team-tab-"+selected);if(selected==1&&seasonStats==null||selected==0&&schedule==null)loadTeam();});if(i==teamTab)tab.setBackground(a.focusBackground(0xff4b5060));tabs.addView(tab);a.gap(tabs,8);}a.gap(page,10);
        if(teamTab==1){drawSeasonStats();focus(restore);if(a.getCurrentFocus()==null)focus("team-tab-1");return;}
        LinearLayout filters=a.row();page.addView(filters);String[] names={"Ближайшие","Завершённые","Все матчи"};for(int i=0;i<3;i++){final int f=i;Button b=a.button(names[i],"games-filter-"+i,()->{gameFilter=f;drawTeam();focus("games-filter-"+f);});if(gameFilter==i)b.setBackground(a.focusBackground(0xff4b5060));filters.addView(b);a.gap(filters,8);}a.gap(page,15);
        if(schedule==null){page.addView(a.text(status,15,MainActivity.MUTED,false));focus(restore);return;}
        List<TeamSchedule.Game> games=new ArrayList<>(schedule.games);if(gameFilter==1)Collections.reverse(games);int count=0;
        for(TeamSchedule.Game g:games){if(gameFilter==0&&"post".equals(g.state)||gameFilter==1&&!"post".equals(g.state))continue;count++;
            LinearLayout row=a.row();row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(a.dp(12),a.dp(10),a.dp(12),a.dp(10));GradientDrawable bg=a.round(count%2==0?0xff222530:0xff1b1e27,11);row.setBackground(bg);row.setFocusable(true);row.setFocusableInTouchMode(true);row.setClickable(true);row.setTag("game-"+g.id);row.setOnFocusChangeListener((v,f)->bg.setStroke(a.dp(f?2:0),MainActivity.TEXT));row.setOnClickListener(v->openGame(g));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=a.dp(7);page.addView(row,lp);
            cell(row,g.asMatch().startLabel(),12,MainActivity.MUTED,false,130);row.addView(badge(g.awayCode,28),new LinearLayout.LayoutParams(a.dp(28),a.dp(28)));a.gap(row,8);TextView name=a.text(g.away+"  @  "+g.home,13,MainActivity.TEXT,true);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));a.gap(row,8);row.addView(badge(g.homeCode,28),new LinearLayout.LayoutParams(a.dp(28),a.dp(28)));a.gap(row,18);
            cell(row,"pre".equals(g.state)?"СКОРО":g.awayScore+" : "+g.homeScore,12,MainActivity.MUTED,true,110);
        }
        if(count==0)page.addView(a.text(gameFilter==0?"Ближайших матчей в этом календаре нет. Выберите завершённые или другой сезон.":"ESPN не передал матчи для выбранного раздела.",14,MainActivity.MUTED,false));
        focus(restore);if(!savedGameFocus.isEmpty())focus(savedGameFocus);if(a.getCurrentFocus()==null)focus("games-filter-"+gameFilter);
    }
    void changeSeason(int delta){int next=year+delta;if(next<2003||table!=null&&next>table.year)return;year=next;reloadTeam();}
    void reloadTeam(){generation++;schedule=null;seasonStats=null;savedGameFocus="";status=teamTab==1?"Загружаем показатели…":"Загружаем календарь…";drawTeam();loadTeam();}
    void loadSeasonStats(){
        int token=generation,requestedYear=year,requestedType=seasonType;String id=team.id;requestedAt=System.currentTimeMillis();a.repository.season(id,year,seasonType,(value,stale,error)->{if(!active()||token!=generation||team==null||!id.equals(team.id)||game!=null)return;if(value!=null&&(!id.equals(value.teamId)||value.year!=requestedYear||value.type!=requestedType)){status="Источник не вернул показатели выбранного сезона";drawTeam();return;}if(value!=null)seasonStats=value;status=error!=null?error:value==null?"Нет данных":(stale?"Сохранённые показатели · ":"ESPN · ")+a.time(value.fetchedAt);drawTeam();});
    }
    List<LeagueTable.Entry> allTeams(){List<LeagueTable.Entry> out=new ArrayList<>();for(LeagueTable.Conference c:table.conferences)out.addAll(c.entries);return out;}
    void favorites(){
        page.addView(a.text("Выберите любимые команды · OK — добавить или убрать",12,MainActivity.MUTED,false));a.gap(page,12);List<LeagueTable.Entry> entries=allTeams();entries.sort(Comparator.comparing(e->e.name));LinearLayout row=null;int n=0;
        for(LeagueTable.Entry e:entries){if(n%5==0){row=a.row();LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=a.dp(8);page.addView(row,lp);}boolean selected=a.library.favorite("nba",e.code);LinearLayout tile=a.row();tile.setTag("favorite-"+e.id);tile.setGravity(Gravity.CENTER_VERTICAL);tile.setPadding(a.dp(10),a.dp(10),a.dp(10),a.dp(10));tile.setFocusable(true);tile.setFocusableInTouchMode(true);tile.setClickable(true);GradientDrawable bg=a.round(selected?0xff413728:0xff20232d,12);bg.setStroke(a.dp(selected?1:0),0xffd6b57d);tile.setBackground(bg);tile.setOnFocusChangeListener((v,f)->bg.setStroke(a.dp(f?2:selected?1:0),f?MainActivity.TEXT:0xffd6b57d));tile.setOnClickListener(v->{a.library.toggle("nba",e.code);drawTable();focus("favorite-"+e.id);});tile.addView(badge(e.code,27),new LinearLayout.LayoutParams(a.dp(27),a.dp(27)));a.gap(tile,8);TextView name=a.text((selected?"★ ":"")+e.code,12,selected?0xffffd28a:MainActivity.TEXT,true);tile.addView(name,new LinearLayout.LayoutParams(0,-2,1));tile.setContentDescription(e.name+(selected?", в избранном":", добавить в избранное"));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.rightMargin=n%5<4?a.dp(8):0;row.addView(tile,lp);n++;}
        a.gap(page,12);page.addView(a.text("Золотая рамка и звезда отмечают матчи этих команд на главной. Выбор хранится на этом телевизоре.",11,MainActivity.MUTED,false));
    }
    void analytics(){
        page.addView(a.text("Команды по разнице очков за матч · OK — календарь и расширенные показатели",12,MainActivity.MUTED,false));a.gap(page,12);List<LeagueTable.Entry> entries=allTeams();entries.sort((x,y)->Double.compare(statNumber(y,"differential"),statNumber(x,"differential")));
        LinearLayout labels=a.row();labels.setPadding(a.dp(12),0,a.dp(12),a.dp(8));page.addView(labels);labels.addView(a.text("КОМАНДА",10,MainActivity.MUTED,true),new LinearLayout.LayoutParams(0,-2,1));for(String label:new String[]{"PPG","OPP","+/−","HOME","AWAY","L10"})cell(labels,label,10,MainActivity.MUTED,true,75);
        for(LeagueTable.Entry e:entries){LinearLayout row=a.row();row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(a.dp(12),a.dp(5),a.dp(12),a.dp(5));row.setTag("team-"+e.id);row.setFocusable(true);row.setFocusableInTouchMode(true);row.setClickable(true);GradientDrawable bg=a.round(0xff20232d,10);row.setBackground(bg);row.setOnFocusChangeListener((v,f)->bg.setStroke(a.dp(f?2:0),MainActivity.TEXT));row.setOnClickListener(v->openTeam(e));row.addView(badge(e.code,22),new LinearLayout.LayoutParams(a.dp(22),a.dp(22)));a.gap(row,10);TextView name=a.text((a.library.favorite("nba",e.code)?"★ ":"")+e.name,12,MainActivity.TEXT,true);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));for(String key:new String[]{"avgPointsFor","avgPointsAgainst","differential","Home","Road","Last Ten Games"})cell(row,e.stat(key),12,key.equals("differential")?(statNumber(e,key)>=0?0xff8fdfc0:0xffffa2a6):MainActivity.TEXT,key.equals("differential"),75);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=a.dp(3);page.addView(row,lp);}
        a.gap(page,12);page.addView(a.text("PPG — набранные, OPP — пропущенные очки за матч · +/− — их разница · HOME / AWAY — баланс дома / в гостях.",10,MainActivity.MUTED,false));
    }
    static double statNumber(LeagueTable.Entry entry,String key){try{return Double.parseDouble(entry.stat(key));}catch(Exception e){return -999;}}
    void drawSeasonStats(){
        if(seasonStats==null){page.addView(a.text(status,14,MainActivity.MUTED,false));return;}SeasonStats s=seasonStats;page.addView(a.text("В среднем за матч · сыграно "+s.value("gamesPlayed")+" · "+(year-1)+"–"+year,11,MainActivity.MUTED,false));a.gap(page,10);LinearLayout heroes=a.row();page.addView(heroes);String[][] keyStats={{"avgPoints","ОЧКИ"},{"avgRebounds","ПОДБОРЫ"},{"avgAssists","ПЕРЕДАЧИ"}};int i=0;for(String[] stat:keyStats){LinearLayout box=a.column();box.setGravity(Gravity.CENTER);box.setPadding(a.dp(12),a.dp(12),a.dp(12),a.dp(12));box.setBackground(a.round(0xff252a37,13));box.addView(a.text(s.value(stat[0]),27,new int[]{0xfff5f5f7,0xffa8cfff,0xffa5e3cc}[i],true));box.addView(a.text(stat[1],10,MainActivity.MUTED,true));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.rightMargin=i++<2?a.dp(10):0;heroes.addView(box,lp);}a.gap(page,12);
        String[][] metrics={{"efgPct","Эффективный процент бросков · eFG%","%"},{"trueShootingPct","Истинный процент реализации · TS%","%"},{"threeAttemptRate","Доля трёхочковых попыток · 3PAr","%"},{"ftAttemptRate","Штрафные / броски с игры · FTr","%"},{"fieldGoalPct","Броски с игры · FG%","%"},{"threePointPct","Трёхочковые · 3PT%","%"},{"freeThrowPct","Штрафные · FT%","%"},{"assistTurnoverRatio","Передачи / потери · AST/TO",""},{"avgSteals","Перехваты за матч",""},{"avgBlocks","Блоки за матч",""},{"avgTurnovers","Потери за матч",""},{"avgOffensiveRebounds","Подборы в атаке за матч",""}};
        for(int n=0;n<metrics.length;n+=2){LinearLayout row=a.row();page.addView(row);for(int j=n;j<Math.min(n+2,metrics.length);j++){String[] metric=metrics[j];LinearLayout box=a.row();box.setGravity(Gravity.CENTER_VERTICAL);box.setPadding(a.dp(12),a.dp(9),a.dp(12),a.dp(9));box.setBackground(a.round(0xff1c202a,10));TextView title=a.text(metric[1],11,MainActivity.MUTED,false);box.addView(title,new LinearLayout.LayoutParams(0,-2,1));String value=s.value(metric[0]);box.addView(a.text(value+(value.equals("—")?"":metric[2]),16,MainActivity.TEXT,true));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.rightMargin=j%2==0?a.dp(10):0;lp.bottomMargin=a.dp(7);row.addView(box,lp);}}
        a.gap(page,8);page.addView(a.text("eFG% = (FGM + 0,5 × 3PM) / FGA. TS% = PTS / (2 × (FGA + 0,44 × FTA)). Показатели рассчитаны по суммарным броскам выбранного сезона ESPN; отсутствующие значения обозначены тире.",10,MainActivity.MUTED,false));
    }
    void drawGame(){
        a.root=new FrameLayout(a);a.root.setBackgroundColor(MainActivity.BG);a.setContentView(a.root);LinearLayout all=a.column();all.setPadding(a.dp(26),a.dp(12),a.dp(26),a.dp(12));a.root.addView(all,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout top=a.row();top.setGravity(Gravity.CENTER_VERTICAL);all.addView(top);top.addView(a.button("‹ К команде","box-back",this::back));a.gap(top,16);TextView title=a.text(game.title()+" · "+game.asMatch().startLabel(),17,MainActivity.TEXT,true);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));

        Match stream=streamFor(game);if(stream!=null){a.gap(top,10);top.addView(a.button("Смотреть AANBA","box-watch",()->{a.leagueScreen=null;a.openMatch(stream,false);}));}
        a.gap(all,10);detailPanel=new StatsPanel(a);detailPanel.footer.setText("← → раздел · ↑ ↓ прокрутка · Назад к команде");all.addView(detailPanel.view,new LinearLayout.LayoutParams(-1,0,1));detailPanel.bind(game.asMatch(),null,null,false,status);detailPanel.focus();
    }
    Match streamFor(TeamSchedule.Game game){Match descriptor=game.asMatch();for(Match m:a.matches)if(m.key().equals(descriptor.key())&&Math.abs(m.start-game.start)<12*3600000L)return m;return null;}
    boolean handleKey(KeyEvent event){
        if(detailPanel==null||game==null)return false;int key=event.getKeyCode();if(key!=KeyEvent.KEYCODE_DPAD_LEFT&&key!=KeyEvent.KEYCODE_DPAD_RIGHT&&key!=KeyEvent.KEYCODE_DPAD_UP&&key!=KeyEvent.KEYCODE_DPAD_DOWN)return false;
        boolean inside=a.getCurrentFocus()==detailPanel.view;
        if(!inside){if(key==KeyEvent.KEYCODE_DPAD_DOWN){if(event.getAction()==KeyEvent.ACTION_DOWN)detailPanel.focus();return true;}return false;}
        if(event.getAction()==KeyEvent.ACTION_DOWN){if(key==KeyEvent.KEYCODE_DPAD_LEFT)detailPanel.selectTab(detailPanel.tab-1);if(key==KeyEvent.KEYCODE_DPAD_RIGHT)detailPanel.selectTab(detailPanel.tab+1);if(key==KeyEvent.KEYCODE_DPAD_DOWN)detailPanel.move(1);if(key==KeyEvent.KEYCODE_DPAD_UP){if(detailPanel.atTop())focus("box-back");else detailPanel.move(-1);}}return true;
    }
    FrameLayout badge(String code,int size){return a.teamLogo(new Match("badge","","",code,code,"NBA","upcoming","","",0),code,size);}
    void cell(LinearLayout row,String value,int size,int color,boolean bold,int width){TextView t=a.text(value,size,color,bold);t.setGravity(Gravity.CENTER);row.addView(t,new LinearLayout.LayoutParams(a.dp(width),-2));}
    static String divisionName(String name){switch(name){case "Atlantic":return "Атлантический";case "Central":return "Центральный";case "Southeast":return "Юго-восточный";case "Northwest":return "Северо-западный";case "Pacific":return "Тихоокеанский";case "Southwest":return "Юго-западный";default:return name;}}
}
