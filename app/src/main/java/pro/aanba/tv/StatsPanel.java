package pro.aanba.tv;

import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Native TV box score: fixed tabs, scrollable content, no web content or player reloads. */
final class StatsPanel {
    final MainActivity a;final LinearLayout view,header,tabs,body;final ScrollView scroll;final TextView footer;
    Match match;Score score;GameDetails details;boolean demo;String message="";int tab=0;
    StatsPanel(MainActivity a){
        this.a=a;view=a.column();view.setFocusable(true);view.setFocusableInTouchMode(true);view.setPadding(a.dp(18),a.dp(16),a.dp(18),a.dp(12));
        view.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff1e2028,0xff0d0f16}));
        header=a.column();view.addView(header);tabs=a.row();tabs.setPadding(0,a.dp(12),0,a.dp(12));view.addView(tabs);
        scroll=new ScrollView(a);scroll.setVerticalScrollBarEnabled(false);scroll.setFillViewport(false);body=a.column();scroll.addView(body,new ScrollView.LayoutParams(-1,-2));view.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        footer=a.text("← → раздел · ↑ ↓ прокрутка · Назад закрыть",9,MainActivity.MUTED,false);footer.setPadding(0,a.dp(10),0,0);view.addView(footer);
    }
    void bind(Match match,Score score,GameDetails details,boolean demo,String message){
        String oldId=this.match==null?"":this.match.id;this.match=match;this.score=score!=null?score:details==null?null:details.score;this.details=details;this.demo=demo;this.message=message==null?"":message;
        if(!oldId.equals(match.id)){tab=0;scroll.scrollTo(0,0);}render(true);
    }
    void selectTab(int value){tab=(value+3)%3;render(false);scroll.scrollTo(0,0);view.requestFocus();UiMotion.enter(body,a.dp(5));}
    void move(int direction){scroll.smoothScrollBy(0,a.dp(direction*100));}
    boolean atTop(){return scroll.getScrollY()<=2;}
    void focus(){view.requestFocus();}
    void render(boolean preserve){
        int y=preserve?scroll.getScrollY():0;header.removeAllViews();tabs.removeAllViews();body.removeAllViews();if(match==null)return;
        LinearLayout title=a.row();title.setGravity(Gravity.CENTER_VERTICAL);header.addView(title);
        title.addView(a.text(demo?"Статистика · ДЕМО":"Статистика",19,MainActivity.TEXT,true));title.addView(new Space(a),new LinearLayout.LayoutParams(0,1,1));
        title.addView(a.text(match.league,10,MainActivity.MUTED,true));
        LinearLayout summary=a.row();summary.setGravity(Gravity.CENTER_VERTICAL);summary.setPadding(0,a.dp(12),0,a.dp(6));header.addView(summary);
        summary.addView(a.teamLogo(match,match.homeCode,30),new LinearLayout.LayoutParams(a.dp(30),a.dp(30)));a.gap(summary,7);summary.addView(a.text(match.homeCode,12,MainActivity.TEXT,true));
        TextView total=a.text(score==null?"— : —":score.scoreFor(match.homeCode)+" : "+score.scoreFor(match.awayCode),27,MainActivity.TEXT,true);total.setGravity(Gravity.CENTER);summary.addView(total,new LinearLayout.LayoutParams(0,-2,1));
        summary.addView(a.text(match.awayCode,12,MainActivity.TEXT,true));a.gap(summary,7);summary.addView(a.teamLogo(match,match.awayCode,30),new LinearLayout.LayoutParams(a.dp(30),a.dp(30)));
        String phase=score==null?"Ожидаем данные":score.phase()+(details!=null&&score==details.score&&details.clockFromPlay?" · последнее событие":"");header.addView(a.text(phase,11,MainActivity.MUTED,false));
        String[] labels={"Команды",match.homeCode,match.awayCode};for(int i=0;i<3;i++){final int n=i;Button b=a.button(labels[i],"stats-tab-"+i,()->selectTab(n));b.setTextSize(11);b.setMinHeight(a.dp(32));b.setMinimumHeight(a.dp(32));b.setPadding(a.dp(8),a.dp(5),a.dp(8),a.dp(5));b.setFocusable(false);if(tab==i)b.setBackground(a.round(0xff4b5060,11));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,a.dp(32),1);if(i<2)lp.rightMargin=a.dp(8);tabs.addView(b,lp);}
        if(score!=null&&"pre".equals(score.state)){notice("Матч ещё не начался","Здесь появятся показатели именно этого матча. Средние за сезон не подставляются.");}
        else if(details==null){notice("Статистика загружается",message.isEmpty()?"Получаем командные показатели и box score ESPN…":message);}
        else if(tab==0)teamStats();else players(tab==1?match.homeCode:match.awayCode);
        if(details!=null){a.gap(body,12);String status=demo?"Пример данных · не настоящий матч":"ESPN · "+a.time(details.fetchedAt)+(details.stale(System.currentTimeMillis())?" · сохранённые данные":"");body.addView(a.text(status,9,MainActivity.MUTED,false));if(!message.isEmpty())body.addView(a.text(message,10,MainActivity.MUTED,false));}
        scroll.post(()->scroll.scrollTo(0,y));
    }
    void notice(String title,String message){a.gap(body,16);body.addView(a.text(title,16,MainActivity.TEXT,true));a.gap(body,10);body.addView(a.text(message,12,MainActivity.MUTED,false));}
    void teamStats(){
        GameDetails.Team home=details.team(match.homeCode),away=details.team(match.awayCode);if(home==null||away==null){notice("Нет данных","Источник ещё не передал командную статистику.");return;}
        LinearLayout highlights=a.row();body.addView(highlights);hero(highlights,"ПОДБОРЫ",home.stat("totalRebounds"),away.stat("totalRebounds"),0xff90bcff);hero(highlights,"ПЕРЕДАЧИ",home.stat("assists"),away.stat("assists"),0xffffcc8b);hero(highlights,"3PT %",home.stat("threePointFieldGoalPct"),away.stat("threePointFieldGoalPct"),0xffa9dfc1);a.gap(body,14);
        metric(match.homeCode,"БРОСКИ И КОМАНДНЫЕ ПОКАЗАТЕЛИ",match.awayCode,true);
        int count=0;for(String[] m:GameDetails.METRICS)if(home.stats.containsKey(m[0])||away.stats.containsKey(m[0])){metric(home.stat(m[0]),m[1],away.stat(m[0]),false);count++;}
        if(count==0)notice("Показатели пока недоступны","Повторим запрос автоматически. Видео продолжает работать.");
        if(home.stats.containsKey("dunksMade")){a.gap(body,8);body.addView(a.text("Данки посчитаны по забитым броскам в ленте событий ESPN завершённого матча.",9,MainActivity.MUTED,false));}
        if(score!=null){List<String> h=score.periodsFor(match.homeCode),b=score.periodsFor(match.awayCode);if(!h.isEmpty()||!b.isEmpty()){a.gap(body,15);metric(match.homeCode,"ПО ЧЕТВЕРТЯМ",match.awayCode,true);for(int i=0;i<Math.max(h.size(),b.size());i++)metric(i<h.size()?h.get(i):"—",i<4?"Четверть "+(i+1):"Овертайм "+(i-3),i<b.size()?b.get(i):"—",false);}}
    }
    void metric(String home,String label,String away,boolean heading){
        LinearLayout row=a.row();row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(a.dp(8),a.dp(9),a.dp(8),a.dp(9));if(body.getChildCount()%2==0)row.setBackground(a.round(0x15ffffff,9));body.addView(row,new LinearLayout.LayoutParams(-1,-2));
        TextView h=a.text(home,heading?10:15,heading?MainActivity.MUTED:MainActivity.TEXT,true);h.setGravity(Gravity.CENTER);row.addView(h,new LinearLayout.LayoutParams(a.dp(62),-2));
        TextView name=a.text(label,heading?8:11,MainActivity.MUTED,false);name.setGravity(Gravity.CENTER);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        TextView b=a.text(away,heading?10:15,heading?MainActivity.MUTED:MainActivity.TEXT,true);b.setGravity(Gravity.CENTER);row.addView(b,new LinearLayout.LayoutParams(a.dp(62),-2));
    }
    void hero(LinearLayout parent,String label,String home,String away,int accent){LinearLayout box=a.column();box.setPadding(a.dp(8),a.dp(10),a.dp(8),a.dp(10));box.setBackground(a.round(0xff2a2e39,12));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.rightMargin=a.dp(5);parent.addView(box,lp);TextView name=a.text(label,8,accent,true);name.setGravity(Gravity.CENTER);box.addView(name);a.gap(box,6);TextView value=a.text(home+" : "+away,18,MainActivity.TEXT,true);value.setGravity(Gravity.CENTER);box.addView(value);}
    void playerMetric(LinearLayout parent,String label,String value,int color){LinearLayout box=a.column();box.setGravity(Gravity.CENTER);parent.addView(box,new LinearLayout.LayoutParams(0,-2,1));box.addView(a.text(value,22,color,true));a.gap(box,3);box.addView(a.text(label,8,MainActivity.MUTED,true));}
    void players(String code){
        GameDetails.Team team=details.team(code);if(team==null||team.players.isEmpty()){notice("Box score пока недоступен","Игроки появятся, когда ESPN передаст строки матча.");return;}
        body.addView(a.text("MIN — минуты · REB — подборы · AST — передачи",9,MainActivity.MUTED,false));a.gap(body,5);body.addView(a.text("STL — перехваты · BLK — блоки · TO — потери",9,MainActivity.MUTED,false));a.gap(body,10);
        for(GameDetails.Player p:team.players){
            LinearLayout card=a.column();card.setPadding(a.dp(10),a.dp(10),a.dp(10),a.dp(10));card.setBackground(a.round(0x28ffffff,11));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=a.dp(9);body.addView(card,cp);
            String name=(p.jersey.isEmpty()?"":"#"+p.jersey+"  ")+p.name+(p.starter?"  · старт":"")+(p.active?"  ●":"");TextView title=a.text(name,13,MainActivity.TEXT,true);title.setSingleLine(false);card.addView(title);a.gap(card,8);
            if(p.dnp){card.addView(a.text("Не играл"+(p.reason.isEmpty()?"":" · "+p.reason),10,MainActivity.MUTED,false));continue;}
            LinearLayout main=a.row();card.addView(main);playerMetric(main,"ОЧКИ",p.stat("PTS"),0xffff9eaa);playerMetric(main,"ПОДБОРЫ",p.stat("REB"),0xff90bcff);playerMetric(main,"ПЕРЕДАЧИ",p.stat("AST"),0xffffcc8b);a.gap(card,8);
            card.addView(a.text("MIN "+p.stat("MIN")+"    STL "+p.stat("STL")+"    BLK "+p.stat("BLK")+"    TO "+p.stat("TO")+"    +/− "+p.stat("+/-"),10,MainActivity.TEXT,false));
            a.gap(card,8);card.addView(a.text("С игры  "+p.shooting("FG")+"     3PT  "+p.shooting("3PT"),10,MainActivity.MUTED,false));a.gap(card,4);card.addView(a.text("Штрафные  "+p.shooting("FT")+"     OR / DR  "+p.stat("OREB")+" / "+p.stat("DREB")+"     Фолы  "+p.stat("PF"),10,MainActivity.MUTED,false));
        }
    }
}
