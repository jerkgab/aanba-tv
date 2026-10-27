package pro.aanba.tv;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    static final int BG=0xff090a0f, CARD=0xff191b22, TEXT=0xfff5f5f7, MUTED=0xffa4a8b4, ACCENT=0xfff5f5f7;
    static final int HIDDEN=0, PANEL=2;
    final Handler ui=new Handler(Looper.getMainLooper());
    final ExecutorService io=Executors.newFixedThreadPool(3);
    final List<Match> matches=new ArrayList<>();
    final Map<String,Score> scores=new HashMap<>();
    android.content.SharedPreferences prefs;
    FrameLayout root,videoHost;
    LinearLayout sidebar;
    PlaybackControls controls;
    WebView web;
    View customVideo;
    WebChromeClient.CustomViewCallback customCallback;
    TeamBranding branding;UserLibrary library;ThumbLoader thumbnails;FocusMotion focusMotion;YoutubeScreen youtubeScreen;YoutubeFeed youtube;boolean youtubeLoading,youtubeBundled;String youtubeMessage="";
    JSONObject lastPlayerState;boolean resumePending;long resumeReadyAt,lastHistoryAt,resumeAppliedAt;
    StatsRepository repository;
    StatsPanel statsPanel;
    LeagueScreen leagueScreen;
    GameDetails watchingDetails;
    String detailsMessage="";String playerAdapterScript;
    final RemoteGestures gestures=new RemoteGestures();
    boolean vkNavigation;long vkActionAt;
    final Runnable openStatsOnHold=this::openStatsHeld;
    Toast visibleToast;
    Match watching;
    boolean foreground,loading,statsLoading,demo,controlsVisible,reloadOnResume;
    int mode=HIDDEN,filter=0,loadCount=0;
    String sourceMessage="Загружаем расписание…";
    long scheduleAt=0;
    final Runnable hideControls=()->showControls(false);
    final Runnable poll=new Runnable(){public void run(){
        if(!foreground)return;
        if(watching!=null){if(!demo){loadScores(Collections.singletonList(watching));if(mode==PANEL)loadDetails();}}
        else if(leagueScreen!=null)leagueScreen.refresh();
        else refreshSchedule(false);
        ui.postDelayed(this,watching!=null||leagueScreen!=null&&leagueScreen.game!=null?20000:60000);
    }};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        branding=new TeamBranding(this,io,ui);repository=new StatsRepository(this,io,ui);
        prefs=getSharedPreferences("aanba",MODE_PRIVATE);library=new UserLibrary(prefs);thumbnails=new ThumbLoader(this,io,ui);
        if(BuildConfig.QA_ENABLED&&getIntent().getBooleanExtra("qa_mode",false)){startDemo();return;}
        initYoutube();String cached=prefs.getString("schedule","");
        if(!cached.isEmpty())try{matches.addAll(ScheduleParser.parse(cached));scheduleAt=prefs.getLong("scheduleAt",0);sourceMessage="Сохранённое расписание · "+time(scheduleAt);}catch(Exception ignored){}
        renderHome();refreshSchedule(true);loadYoutube();
    }
    @Override protected void onResume(){super.onResume();foreground=true;ui.removeCallbacks(poll);ui.postDelayed(poll,20000);
        if(web!=null){web.onResume();web.resumeTimers();if(reloadOnResume){reloadOnResume=false;resumePending=!demo&&library.history.get(watching)!=null;resumeReadyAt=0;lastPlayerState=null;loadPlayer();}ui.post(playerPoll);}
    }
    @Override protected void onPause(){saveProgress();foreground=false;ui.removeCallbacks(poll);ui.removeCallbacks(hideControls);
        if(web!=null){web.evaluateJavascript("window.AanbaTv&&window.AanbaTv.pause()",null);web.onPause();}
        ui.removeCallbacks(openStatsOnHold);ui.removeCallbacks(playerPoll);gestures.cancelUp();super.onPause();
    }
    @Override protected void onStop(){
        if(web!=null){exitCustomVideo();web.loadUrl("about:blank");reloadOnResume=true;}
        super.onStop();
    }
    @Override protected void onDestroy(){ui.removeCallbacksAndMessages(null);destroyPlayer();io.shutdownNow();if(thumbnails!=null)thumbnails.close();super.onDestroy();}

    void refreshSchedule(boolean explicit){
        if(loading||io.isShutdown()||isDestroyed())return;loading=true;
        if(explicit&&matches.isEmpty()){sourceMessage="Загружаем расписание…";renderHome();}
        io.execute(()->{
            try{
                String html=Network.get("https://aanba.pro/schedule");List<Match> list=ScheduleParser.parse(html);long now=System.currentTimeMillis();
                JSONArray cache=new JSONArray();for(Match m:list)cache.put(m.toJson());
                prefs.edit().putString("schedule",cache.toString()).putLong("scheduleAt",now).apply();
                ui.post(()->{if(isDestroyed()||isFinishing())return;loading=false;matches.clear();matches.addAll(list);scheduleAt=now;sourceMessage="Обновлено "+time(now)+" · время вашего ТВ";if(watching==null)renderHome();loadScores(list);loadYoutube();});
            }catch(Exception e){ui.post(()->{if(isDestroyed()||isFinishing())return;loading=false;sourceMessage=(matches.isEmpty()?"Не удалось загрузить расписание":"Сохранённое расписание · "+time(scheduleAt))+". Нажмите «Обновить».";if(watching==null)renderHome();if(explicit)toast("Расписание недоступно. Проверьте интернет и повторите.");});}
        });
    }
    void loadScores(List<Match> list){
        if(statsLoading||demo||io.isShutdown()||isDestroyed())return;statsLoading=true;
        Set<String> requests=new LinkedHashSet<>();long now=System.currentTimeMillis();
        for(Match m:list)if(m.start>now-48*3600000L&&m.start<now+8*24*3600000L)requests.add(m.espnLeague()+"/"+m.espnDate());
        // A selected recording still gets its final box score.
        if(watching!=null)requests.add(watching.espnLeague()+"/"+watching.espnDate());
        io.execute(()->{
            List<Score> got=new ArrayList<>();boolean failed=false;
            for(String request:requests){try{
                String[] a=request.split("/");String url="https://site.api.espn.com/apis/site/v2/sports/basketball/"+a[0]+"/scoreboard?dates="+a[1];
                got.addAll(Score.parse(Network.get(url),a[0],System.currentTimeMillis()));
            }catch(Exception e){failed=true;}}
            final boolean hadFailure=failed;
            ui.post(()->{if(isDestroyed()||isFinishing())return;statsLoading=false;for(Score s:got)scores.put(s.key()+":"+s.start,s);
                if(watching!=null){updateStats();if(mode==PANEL)loadDetails();if(hadFailure&&scoreFor(watching)==null)toast("Статистика временно недоступна. Видео продолжает работать.");}
                else if(!got.isEmpty())renderHome();
            });
        });
    }
    Score scoreFor(Match match){
        if(demo&&watching==match)return watchingDetails==null?Score.demo(match):watchingDetails.score;
        for(Score s:scores.values())if(s.matches(match))return s;return null;
    }
    List<Match> ordered(){
        List<Match> list=new ArrayList<>(matches);long now=System.currentTimeMillis();
        list.sort((a,b)->{String sa=a.state(scoreFor(a),now),sb=b.state(scoreFor(b),now);int ra=rank(sa),rb=rank(sb);if(ra!=rb)return Integer.compare(ra,rb);return "post".equals(sa)?Long.compare(b.start,a.start):Long.compare(a.start,b.start);});return list;
    }
    int rank(String s){return "in".equals(s)?0:"pre".equals(s)?1:2;}
    String stateLabel(Match m){String s=m.state(scoreFor(m),System.currentTimeMillis());return "in".equals(s)?"● LIVE":"post".equals(s)?"ЗАПИСЬ":"СКОРО";}

    void renderHome(){
        if(isFinishing()||isDestroyed()||watching!=null||leagueScreen!=null||youtubeScreen!=null)return;
        boolean entering=root==null||!"home".equals(root.getTag());Object restore=getCurrentFocus()==null?null:getCurrentFocus().getTag();
        root=new FrameLayout(this);root.setTag("home");root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff161a27,BG,BG}));setContentView(root);
        LinearLayout page=column();page.setPadding(dp(32),dp(20),dp(32),dp(16));root.addView(page,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout header=row();header.setGravity(Gravity.CENTER_VERTICAL);page.addView(header);header.addView(text("AANBA",25,TEXT,true));gap(header,10);header.addView(text("TV",12,MUTED,true));header.addView(new Space(this),new LinearLayout.LayoutParams(0,1,1));
        header.addView(button("Таблица NBA","standings",this::openLeague));gap(header,8);header.addView(button("Обновить","refresh",()->refreshSchedule(true)));gap(header,8);header.addView(button("Помощь","help",this::help));
        TextView status=text(sourceMessage,10,MUTED,false);status.setPadding(0,dp(7),0,dp(12));page.addView(status);
        LinearLayout filters=row();page.addView(filters);String[] names={"Все эфиры","Сейчас","Далее","Записи"};for(int i=0;i<names.length;i++){final int f=i;Button button=button(names[i],"filter"+i,()->{filter=f;renderHome();});if(filter==i)button.setBackground(focusBackground(0xff3b3e49));filters.addView(button);gap(filters,8);}gap(page,12);
        ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);scroll.setClipToPadding(false);scroll.setPadding(dp(2),dp(2),dp(2),dp(2));page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));LinearLayout grid=column();scroll.addView(grid,new ScrollView.LayoutParams(-1,-2));
        int widthDp=Math.round(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density);int columns=widthDp>=1280?4:widthDp>=800?3:2;List<LinearLayout> lines=new ArrayList<>();
        List<Match> ordered=ordered();LinearLayout line=null;int n=0;
        for(Match m:ordered){String state=m.state(scoreFor(m),System.currentTimeMillis());if(filter>0&&!state.equals(filter==1?"in":filter==2?"pre":"post"))continue;
            if(n%columns==0){line=row();LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.bottomMargin=dp(12);grid.addView(line,rp);lines.add(line);}
            LinearLayout card=matchCard(m);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(112),1);cp.rightMargin=n%columns<columns-1?dp(12):0;line.addView(card,cp);n++;
        }
        if(line!=null)while(n%columns!=0){LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,1,1);cp.rightMargin=n%columns<columns-1?dp(12):0;line.addView(new Space(this),cp);n++;}
        if(lines.isEmpty()){TextView empty=text(matches.isEmpty()?"Здесь появятся трансляции. Нажмите «Обновить».":"В этом разделе пока нет матчей.",17,MUTED,false);empty.setPadding(0,dp(24),0,dp(24));grid.addView(empty);}
        // Fill an integer number of complete rows. Focus borders remain inside their cards.
        final int[] lastHeight={-1};scroll.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{int available=b-t-scroll.getPaddingTop()-scroll.getPaddingBottom();if(available<=0||available==lastHeight[0])return;lastHeight[0]=available;int rows=Math.max(1,(available+dp(12))/dp(124)),height=(available-dp(12)*(rows-1))/rows;
            for(LinearLayout row:lines)for(int i=0;i<row.getChildCount();i++){View card=row.getChildAt(i);if(card instanceof LinearLayout){LinearLayout.LayoutParams cp=(LinearLayout.LayoutParams)card.getLayoutParams();cp.height=height;card.setLayoutParams(cp);}}
        });
        if(filter==0){gap(page,9);page.addView(youtubePreview(),new LinearLayout.LayoutParams(-1,-2));}
        TextView footer=text("Стрелки — выбор · OK — смотреть · удерживать ↑ в плеере — статистика",10,MUTED,false);footer.setPadding(0,dp(10),0,0);page.addView(footer);
        focusMotion=new FocusMotion(this,root);root.addView(focusMotion,new FrameLayout.LayoutParams(-1,-1));root.getViewTreeObserver().addOnGlobalFocusChangeListener((old,next)->{if(next!=null&&!(next.getTag() instanceof String&&((String)next.getTag()).startsWith("yt:"))&&!(next instanceof ProgressCard))focusMotion.hide();});if(entering)UiMotion.enter(page,dp(6));
        View focus=restore==null?null:root.findViewWithTag(restore);if(focus!=null)focus.requestFocus();else if(!ordered.isEmpty()&&root.findViewWithTag(ordered.get(0).id)!=null)root.findViewWithTag(ordered.get(0).id).requestFocus();else root.findViewWithTag("refresh").requestFocus();
    }
    FrameLayout teamLogo(Match match,String code,int size){
        FrameLayout badge=new FrameLayout(this);
        TextView fallback=text(code,Math.max(10,size/4),0xffb7bfce,true);fallback.setGravity(Gravity.CENTER);badge.addView(fallback,new FrameLayout.LayoutParams(-1,-1));
        ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setContentDescription(code);badge.addView(image,new FrameLayout.LayoutParams(-1,-1));
        // Only show the monogram until the real packaged mark is ready.
        image.setBackgroundColor(Color.TRANSPARENT);branding.load(match,code,image);
        image.getViewTreeObserver().addOnDrawListener(()->{if(image.getDrawable()!=null&&fallback.getVisibility()!=View.INVISIBLE)fallback.post(()->fallback.setVisibility(View.INVISIBLE));});
        return badge;
    }
    LinearLayout teamColumn(Match match,String code,String name,String record){
        LinearLayout team=column();team.setGravity(Gravity.CENTER);
        team.addView(teamLogo(match,code,32),new LinearLayout.LayoutParams(dp(32),dp(32)));
        TextView label=text(branding.nickname(name),11,TEXT,true);label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);label.setGravity(Gravity.CENTER);label.setPadding(0,dp(3),0,0);team.addView(label,new LinearLayout.LayoutParams(-1,-2));
        TextView balance=text(record.isEmpty()?"":"("+record+")",10,MUTED,false);balance.setGravity(Gravity.CENTER);balance.setPadding(0,dp(1),0,0);team.addView(balance,new LinearLayout.LayoutParams(-1,dp(13)));
        return team;
    }
    LinearLayout matchCard(Match m){
        WatchHistory.Entry progress=library.history.get(m);boolean favorite=library.favorite(m);LinearLayout card=new ProgressCard(this,progress);card.setFocusable(true);card.setClickable(true);card.setTag(m.id);card.setContentDescription(m.home+" — "+m.away+", "+stateLabel(m));card.setPadding(dp(12),dp(8),dp(12),dp(7));
        int home=blend(branding.color(m,m.homeCode)),away=blend(branding.color(m,m.awayCode));
        GradientDrawable art=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{home,CARD,away});art.setCornerRadius(dp(16));art.setStroke(dp(favorite?2:1),favorite?0xffd6b57d:0x20ffffff);card.setBackground(art);
        card.setOnFocusChangeListener((v,f)->{art.setStroke(dp(f||favorite?2:1),f?0xfff5f5f7:favorite?0xffd6b57d:0x20ffffff);v.setScaleX(1);v.setScaleY(1);if(f&&focusMotion!=null)focusMotion.move(v);View teams=v.findViewWithTag("team-art");if(teams!=null)teams.animate().scaleX(f?1.025f:1).scaleY(f?1.025f:1).setDuration(170).start();});card.setOnClickListener(v->openMatch(m,false));
        String state=m.state(scoreFor(m),System.currentTimeMillis());
        LinearLayout top=row();top.setGravity(Gravity.CENTER_VERTICAL);card.addView(top);
        TextView badge=text((favorite?"★ ":"")+stateLabel(m),9,favorite?0xffffd28a:"in".equals(state)?0xffff7c82:MUTED,true);top.addView(badge);top.addView(new Space(this),new LinearLayout.LayoutParams(0,1,1));top.addView(text(m.league,9,MUTED,true));
        Score sc=scoreFor(m);boolean record=sc!=null&&!sc.stale(System.currentTimeMillis())&&!"post".equals(state);
        LinearLayout teams=row();teams.setTag("team-art");teams.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,0,1);tp.topMargin=dp(4);card.addView(teams,tp);
        teams.addView(teamColumn(m,m.homeCode,m.home,record?sc.recordFor(m.homeCode):""),new LinearLayout.LayoutParams(0,-2,1));
        TextView vs=text(sc!=null&&!"pre".equals(sc.state)?sc.scoreFor(m.homeCode)+" : "+sc.scoreFor(m.awayCode):"VS",12,MUTED,true);vs.setGravity(Gravity.CENTER);vs.setSingleLine(true);teams.addView(vs,new LinearLayout.LayoutParams(dp(72),-2));
        teams.addView(teamColumn(m,m.awayCode,m.away,record?sc.recordFor(m.awayCode):""),new LinearLayout.LayoutParams(0,-2,1));
        TextView meta=text(progress!=null&&progress.resumable()?"Продолжить "+PlaybackControls.format(progress.seconds)+" · "+m.startLabel():progress!=null&&progress.completed?"Просмотрено · "+m.startLabel():sc!=null&&"in".equals(sc.state)?sc.phase():m.startLabel(),progress!=null&&progress.resumable()?9:10,MUTED,false);meta.setSingleLine(true);meta.setGravity(Gravity.CENTER);card.addView(meta,new LinearLayout.LayoutParams(-1,dp(14)));
        return card;
    }
    int blend(int color){return Color.rgb((Color.red(color)+Color.red(CARD)*5)/6,(Color.green(color)+Color.green(CARD)*5)/6,(Color.blue(color)+Color.blue(CARD)*5)/6);}
    void openMatch(Match m,boolean isDemo){
        destroyPlayer();watching=m;demo=isDemo;mode=HIDDEN;lastPlayerState=null;resumePending=!isDemo&&library.history.get(m)!=null;resumeReadyAt=0;lastHistoryAt=0;resumeAppliedAt=0;vkNavigation=false;gestures.cancelUp();gestures.resetBack();loadCount=0;
        root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);root.setFocusable(true);root.setFocusableInTouchMode(true);setContentView(root);
        videoHost=new FrameLayout(this);videoHost.setBackgroundColor(Color.BLACK);root.addView(videoHost,new FrameLayout.LayoutParams(-1,-1));
        try{web=new WebView(this);}catch(RuntimeException unavailable){watching=null;renderHome();toast("Обновите или включите Android System WebView в настройках телевизора.");return;}web.setBackgroundColor(Color.BLACK);web.setFocusable(true);web.setFocusableInTouchMode(true);web.setPointerIcon(PointerIcon.getSystemIcon(this,PointerIcon.TYPE_NULL));web.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        WebSettings w=web.getSettings();WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);w.setJavaScriptEnabled(true);w.setDomStorageEnabled(true);w.setMediaPlaybackRequiresUserGesture(false);w.setAllowFileAccess(false);w.setAllowContentAccess(false);w.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);w.setSupportMultipleWindows(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
        web.setWebViewClient(new WebViewClient(){
            @Override public void onPageCommitVisible(WebView view,String url){installPlayerAdapter(view);}
            @Override public void onPageFinished(WebView view,String url){
                installPlayerAdapter(view);
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                if(demo&&"aanba.pro".equals(request.getUrl().getHost())&&"/local-test.mp4".equals(request.getUrl().getPath())){
                    try{return new WebResourceResponse("video/mp4",null,getAssets().open("test-pattern.mp4"));}catch(IOException ignored){}
                }
                return null;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                if(!request.isForMainFrame())return false;
                String scheme=request.getUrl().getScheme(),host=request.getUrl().getHost();
                if(!"https".equals(scheme))return true;
                return request.isForMainFrame()&&!(demo&&"aanba.pro".equals(host))&&!("vkvideo.ru".equals(host)&&"/video_ext.php".equals(request.getUrl().getPath()));
            }
            @Override public void onReceivedError(WebView v,WebResourceRequest req,WebResourceError err){if(req.isForMainFrame())toast("Плеер недоступен. Вернитесь к матчам и откройте эфир снова.");}
            @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){if(view==web){goHome();toast("Плеер остановился. Откройте запись снова; при повторении обновите Android System WebView.");}return true;}
            @Override public void onReceivedSslError(WebView v,android.webkit.SslErrorHandler handler,android.net.http.SslError error){handler.cancel();toast("Не удалось безопасно подключиться к плееру.");}
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onShowCustomView(View view,CustomViewCallback cb){
                if(customVideo!=null){cb.onCustomViewHidden();return;}customVideo=view;customCallback=cb;web.setVisibility(View.INVISIBLE);videoHost.addView(view,new FrameLayout.LayoutParams(-1,-1));applyMode();
            }
            @Override public void onHideCustomView(){exitCustomVideo();}
            @Override public boolean onConsoleMessage(ConsoleMessage m){return true;}
            @Override public void onPermissionRequest(PermissionRequest request){
                String host=request.getOrigin().getHost();boolean trusted=host!=null&&(host.equals("vkvideo.ru")||host.endsWith(".vkvideo.ru")||host.equals("vk.com")||host.equals("vk.ru"));
                java.util.List<String> allowed=new java.util.ArrayList<>();
                if(trusted)for(String resource:request.getResources())if(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID.equals(resource))allowed.add(resource);
                if(allowed.isEmpty())request.deny();else request.grant(allowed.toArray(new String[0]));
            }
            @Override public Bitmap getDefaultVideoPoster(){Bitmap b=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888);b.eraseColor(Color.BLACK);return b;}
        });
        videoHost.addView(web,new FrameLayout.LayoutParams(-1,-1));
        sidebar=column();sidebar.setBackgroundColor(BG);statsPanel=new StatsPanel(this);sidebar.addView(statsPanel.view,new LinearLayout.LayoutParams(-1,-1));root.addView(sidebar);
        controls=new PlaybackControls(this);root.addView(controls.view,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol||b-t!=ob-ot)applyMode();});
        loadPlayer();applyMode();showControls(false);if(!demo)loadScores(Collections.singletonList(m));ui.removeCallbacks(poll);if(foreground)ui.postDelayed(poll,20000);ui.removeCallbacks(playerPoll);ui.post(playerPoll);
    }
    void loadPlayer(){
        if(web==null||watching==null)return;loadCount++;
        try{if(demo)web.loadDataWithBaseURL("https://aanba.pro/",readAsset("player.html"),"text/html","UTF-8",null);else web.loadUrl(watching.embedUrl());}
        catch(Exception e){toast("Не удалось открыть плеер");}
    }
    void installPlayerAdapter(WebView view){
        if(view!=web||watching==null)return;android.net.Uri uri=android.net.Uri.parse(view.getUrl()==null?"":view.getUrl());String host=uri.getHost();
        if(!demo&&!("vkvideo.ru".equals(host)&&"/video_ext.php".equals(uri.getPath())))return;
        try{if(playerAdapterScript==null)playerAdapterScript=readAsset("player-tv.js");view.evaluateJavascript(playerAdapterScript,null);}catch(Exception ignored){}
    }
    final Runnable playerPoll=new Runnable(){public void run(){
        if(web==null||watching==null||!foreground)return;
        WebView observed=web;Match observedMatch=watching;observed.evaluateJavascript("window.AanbaTv?window.AanbaTv.state():null",value->{if(web!=observed||watching!=observedMatch)return;if((value==null||"null".equals(value))&&web!=null)installPlayerAdapter(web);if(controls!=null&&watching!=null&&value!=null&&!"null".equals(value))try{JSONObject state=new JSONObject(value);handleProgress(state);controls.update(state);if(vkNavigation&&SystemClock.uptimeMillis()-vkActionAt>700&&!state.optBoolean("menuOpen")){vkNavigation=false;playerJs("hideChrome()");}}catch(Exception ignored){}});
        ui.postDelayed(this,500);
    }};
    void openStatsHeld(){if(watching!=null&&gestures.upHeld(SystemClock.uptimeMillis())){dismissVk();setMode(mode==PANEL?HIDDEN:PANEL);showControls(false);}}
    void setMode(int value){boolean changed=mode!=value;mode=value;applyMode();if(mode==PANEL){if(changed){sidebar.setAlpha(0);sidebar.setTranslationX(dp(18));sidebar.animate().alpha(1).translationX(0).setDuration(200).start();}loadDetails();}}
    void applyMode(){
        if(watching==null||videoHost==null)return;int width=root.getWidth(),height=root.getHeight();if(width==0||height==0){root.post(this::applyMode);return;}
        int panelWidth=mode==PANEL?Math.min(dp(448),width/2):0;int videoHeight=Math.min(height,Math.round((width-panelWidth)*9f/16f)),videoWidth=Math.min(width-panelWidth,Math.round(videoHeight*16f/9f));
        videoHost.setLayoutParams(new FrameLayout.LayoutParams(videoWidth,videoHeight,Gravity.LEFT|Gravity.CENTER_VERTICAL));
        sidebar.setVisibility(mode==PANEL?View.VISIBLE:View.GONE);sidebar.setLayoutParams(new FrameLayout.LayoutParams(panelWidth,-1,Gravity.RIGHT));
        updateStats();if(controls!=null)controls.view.bringToFront();
    }
    void updateStats(){if(watching==null||statsPanel==null)return;if(demo&&watchingDetails==null)watchingDetails=GameDetails.demo(watching);statsPanel.bind(watching,scoreFor(watching),watchingDetails,demo,detailsMessage);}
    void handleProgress(JSONObject state){
        if(watching==null||demo)return;lastPlayerState=state;long now=SystemClock.uptimeMillis();
        if(resumePending&&state.optBoolean("ready")&&state.optBoolean("canSeek")&&!state.optBoolean("live")){
            if(resumeReadyAt==0)resumeReadyAt=now;if(now-resumeReadyAt>=650){WatchHistory.Entry entry=library.history.get(watching);double target=library.history.restore(entry,state.optDouble("start",0),state.optDouble("end",0));resumePending=false;if(target>=0){playerJs("seekTo("+target+")");resumeAppliedAt=now;try{state.put("time",target);}catch(Exception ignored){}if(entry!=null&&entry.resumable())toast("Продолжаем с "+PlaybackControls.format(target));}}
        }
        if(!resumePending&&now-resumeAppliedAt>2000&&now-lastHistoryAt>=5000)saveProgress();
    }
    void saveProgress(){if(library==null||watching==null||demo||resumePending||lastPlayerState==null||!lastPlayerState.optBoolean("ready")||lastPlayerState.optBoolean("live")||!"post".equals(watching.state(scoreFor(watching),System.currentTimeMillis())))return;library.progress(watching,lastPlayerState.optDouble("time",-1),lastPlayerState.optDouble("end",0));lastHistoryAt=SystemClock.uptimeMillis();}
    void restartRecording(){if(watching==null||controls==null||!controls.position.enabled||controls.live)return;resumePending=false;playerJs("seekTo("+controls.position.start+")");if(!demo)library.restart(watching,controls.position.end);if(lastPlayerState!=null)try{lastPlayerState.put("time",controls.position.start);}catch(Exception ignored){}toast("С начала записи");}
    void initYoutube(){try{youtube=YoutubeFeed.bundled(readAsset("youtube-picks.json"));youtubeBundled=true;youtubeMessage="Подборка из сборки · обновляем ленту";}catch(Exception ignored){youtubeMessage="Загружаем видео AANBA…";}}
    void loadYoutube(){loadYoutube(false);}
    void loadYoutube(boolean force){if(repository==null||youtubeLoading||isDestroyed())return;youtubeLoading=true;if(force){youtubeMessage="Обновляем видео…";if(youtubeScreen!=null)youtubeScreen.render();else renderHome();}repository.youtube(force,(value,stale,error)->{if(isDestroyed())return;youtubeLoading=stale&&error==null;if(value!=null){youtube=value;youtubeBundled=false;}youtubeMessage=error!=null?(youtubeBundled?"YouTube недоступен · подборка из сборки":youtube!=null?"YouTube недоступен · сохранённые видео":"Лента YouTube недоступна"):stale?"Сохранённые видео · обновляем ленту":"Последние видео канала AANBA";if(youtubeScreen!=null)youtubeScreen.render();else if(watching==null&&leagueScreen==null)renderHome();});}
    Button smallButton(String title,String tag,Runnable action){Button b=button(title,tag,action);b.setTextSize(10);b.setMinHeight(dp(26));b.setMinimumHeight(dp(26));b.setPadding(dp(10),dp(3),dp(10),dp(3));return b;}
    LinearLayout youtubePreview(){
        LinearLayout section=column();LinearLayout heading=row();heading.setGravity(Gravity.CENTER_VERTICAL);section.addView(heading);heading.addView(text("Обзоры и видео AANBA",11,TEXT,true));gap(heading,10);TextView state=text(youtubeMessage,9,MUTED,false);state.setSingleLine(true);state.setEllipsize(android.text.TextUtils.TruncateAt.END);heading.addView(state,new LinearLayout.LayoutParams(0,-2,1));
        heading.addView(smallButton("Канал ↗","youtube-channel",()->openYoutube(SafeLinks.YOUTUBE_CHANNEL)),new LinearLayout.LayoutParams(-2,dp(26)));gap(heading,6);heading.addView(smallButton("Обновить","youtube-refresh",()->loadYoutube(true)),new LinearLayout.LayoutParams(-2,dp(26)));gap(heading,6);heading.addView(smallButton("Все видео →","youtube-all",()->{youtubeScreen=new YoutubeScreen(this);youtubeScreen.render();}),new LinearLayout.LayoutParams(-2,dp(26)));gap(section,5);
        LinearLayout row=row();section.addView(row);if(youtube==null||youtube.videos.isEmpty()){TextView empty=text("Лента не загрузилась. Откройте канал в YouTube или нажмите «Обновить».",11,MUTED,false);empty.setGravity(Gravity.CENTER_VERTICAL);row.addView(empty,new LinearLayout.LayoutParams(-1,dp(79)));}else{int n=0;for(YoutubeFeed.Video video:youtube.featured()){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(79),1);lp.rightMargin=n++<2?dp(10):0;row.addView(YoutubeScreen.card(this,video,true),lp);}}return section;
    }
    void openYoutube(String url){if(!SafeLinks.youtube(url))return;for(String pkg:new String[]{"com.google.android.youtube.tv","com.teamsmart.videomanager.tv","com.teamsmart.videomanager.tv.beta","com.google.android.youtube"})try{Intent intent=new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url));intent.setPackage(pkg);intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(intent);return;}catch(ActivityNotFoundException ignored){}toast("Для роликов установите YouTube или SmartTube. Матчи работают без них.");}
    void openLeague(){leagueScreen=new LeagueScreen(this);leagueScreen.start();}
    void loadDetails(){
        if(watching==null||demo||repository==null||mode!=PANEL)return;
        Score score=scoreFor(watching);if(score==null)return;Match selected=watching;String event=score.eventId;
        repository.summary(selected.espnLeague(),event,"post".equals(score.state),(value,stale,error)->{
            if(isDestroyed()||watching!=selected)return;
            if(value!=null&&value.eventId.equals(event)&&value.matches(selected)){watchingDetails=value;detailsMessage=error!=null?error:stale?"Сохранённые данные · обновляем…":"";}
            else if(value!=null)detailsMessage="Источник вернул другой матч";
            else detailsMessage=error==null?"Подробная статистика пока недоступна":error;
            updateStats();
        });
    }

    GradientDrawable glass(int radius){GradientDrawable g=round(0xc9242731,radius);g.setStroke(dp(1),0x35ffffff);return g;}
    void keepControls(){ui.removeCallbacks(hideControls);if(controlsVisible)ui.postDelayed(hideControls,8000);}
    void showControls(boolean show){
        if(controls==null||watching==null)return;controlsVisible=show&&mode!=PANEL;controls.show(controlsVisible);ui.removeCallbacks(hideControls);
        if(controlsVisible){dismissVk();playerJs("hideChrome()");controls.focusTimeline();keepControls();}
        else if(mode==PANEL&&statsPanel!=null)statsPanel.focus();else if(web!=null)web.requestFocus();
    }
    void playerJs(String command){if(web!=null)web.evaluateJavascript("window.AanbaTv&&window.AanbaTv."+command,null);}
    void mediaCommand(String command){if(web!=null)web.evaluateJavascript("window.AanbaTv?window.AanbaTv."+command+"():false",result->{if(!"true".equals(result))toast("Плеер ещё не готов");});}
    void openVkSettings(){
        if(web==null)return;if(customVideo!=null)exitCustomVideo();vkActionAt=SystemClock.uptimeMillis();controlsVisible=false;controls.view.setVisibility(View.GONE);ui.removeCallbacks(hideControls);vkNavigation=true;web.requestFocus();
        web.evaluateJavascript("window.AanbaTv?window.AanbaTv.settings():false",result->{if(!"true".equals(result)){vkNavigation=false;showControls(true);controls.focusSettings();toast("Настройки VK пока недоступны. Дождитесь загрузки плеера.");}});
    }
    void dismissVk(){if(vkNavigation){playerJs("dismiss()");vkNavigation=false;}playerJs("hideChrome()");}
    void clearPlayerUi(){ui.removeCallbacks(openStatsOnHold);gestures.cancelUp();dismissVk();mode=HIDDEN;applyMode();showControls(false);}
    void shortUp(){if(mode==PANEL){statsPanel.move(-1);return;}if(vkNavigation){playerJs("navigate('up')");return;}showControls(true);controls.focusTimeline();}
    void help(){new AlertDialog.Builder(this).setTitle("AANBA TV · "+BuildConfig.VERSION_NAME)
        .setMessage("Удерживайте ↑ — статистика. В ней ← / → — команды и игроки, ↑ / ↓ — прокрутка.\n\n← / → — шкала записи; удержание ускоряет перемотку. ↓ — кнопки плеера: пауза и настройки VK. В настройках стрелки выбирают пункт, OK нажимает.\n\nОдин Назад — скрыть интерфейс, два быстрых Назад — к матчам.")
        .setPositiveButton("Понятно",null).setNeutralButton("Проверить экран",(d,n)->startDemo()).show();}
    void startDemo(){if(prefs==null)prefs=getSharedPreferences("aanba",MODE_PRIVATE);
        Match m=new Match("demo","Бостон Селтикс","Лос-Анджелес Лейкерс","BOS","LAL","NBA","live","https://vkvideo.ru/live-55574239_456428280","",System.currentTimeMillis()-3600000L);openMatch(m,true);}
    void exitCustomVideo(){if(customVideo!=null){videoHost.removeView(customVideo);customVideo=null;if(web!=null)web.setVisibility(View.VISIBLE);WebChromeClient.CustomViewCallback cb=customCallback;customCallback=null;if(cb!=null)cb.onCustomViewHidden();}}
    void destroyPlayer(){
        saveProgress();
        if(visibleToast!=null){visibleToast.cancel();visibleToast=null;}
        exitCustomVideo();ui.removeCallbacks(hideControls);
        if(web!=null){if(web.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)web.getParent()).removeView(web);web.stopLoading();web.loadUrl("about:blank");web.destroy();web=null;}
        controls=null;sidebar=null;videoHost=null;ui.removeCallbacks(playerPoll);statsPanel=null;watchingDetails=null;detailsMessage="";watching=null;lastPlayerState=null;resumePending=false;vkNavigation=false;reloadOnResume=false;ui.removeCallbacks(openStatsOnHold);gestures.cancelUp();gestures.resetBack();
    }
    void goHome(){destroyPlayer();demo=false;renderHome();loadYoutube();if(matches.isEmpty())refreshSchedule(true);}
    @Override public void onBackPressed(){
        if(watching==null){if(youtubeScreen!=null)youtubeScreen.close();else if(leagueScreen!=null)leagueScreen.back();else super.onBackPressed();return;}
        if(gestures.back(SystemClock.uptimeMillis()))goHome();else clearPlayerUi();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(watching==null){if(leagueScreen!=null&&leagueScreen.handleKey(event))return true;return super.dispatchKeyEvent(event);}
        int key=event.getKeyCode();boolean down=event.getAction()==KeyEvent.ACTION_DOWN;
        if(key==KeyEvent.KEYCODE_VOLUME_UP||key==KeyEvent.KEYCODE_VOLUME_DOWN||key==KeyEvent.KEYCODE_VOLUME_MUTE)return super.dispatchKeyEvent(event);
        if(key==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled())onBackPressed();return true;}
        if(down&&event.getRepeatCount()==0)gestures.resetBack();
        if(key==KeyEvent.KEYCODE_DPAD_UP){
            if(down){gestures.upDown(SystemClock.uptimeMillis());if(event.getRepeatCount()==0)ui.postDelayed(openStatsOnHold,RemoteGestures.HOLD_MS);}
            else{ui.removeCallbacks(openStatsOnHold);int action=event.isCanceled()?RemoteGestures.NONE:gestures.upUp(SystemClock.uptimeMillis());if(action==RemoteGestures.PANEL){dismissVk();setMode(mode==PANEL?HIDDEN:PANEL);showControls(false);}else if(action==RemoteGestures.STEP)shortUp();gestures.cancelUp();}return true;
        }
        if(key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE||key==KeyEvent.KEYCODE_MEDIA_PLAY||key==KeyEvent.KEYCODE_MEDIA_PAUSE){if(down&&event.getRepeatCount()==0)mediaCommand(key==KeyEvent.KEYCODE_MEDIA_PLAY?"play":key==KeyEvent.KEYCODE_MEDIA_PAUSE?"pause":"toggle");return true;}
        if(key==KeyEvent.KEYCODE_MENU||key==KeyEvent.KEYCODE_INFO){if(down&&event.getRepeatCount()==0){if(mode==PANEL)setMode(HIDDEN);showControls(true);controls.focusSettings();}return true;}
        if(mode==PANEL){if(down){if(key==KeyEvent.KEYCODE_DPAD_LEFT)statsPanel.selectTab(statsPanel.tab-1);if(key==KeyEvent.KEYCODE_DPAD_RIGHT)statsPanel.selectTab(statsPanel.tab+1);if(key==KeyEvent.KEYCODE_DPAD_DOWN)statsPanel.move(1);if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER)mediaCommand("toggle");}return true;}
        if(vkNavigation){if(down){vkActionAt=SystemClock.uptimeMillis();if(key==KeyEvent.KEYCODE_DPAD_LEFT)playerJs("navigate('left')");if(key==KeyEvent.KEYCODE_DPAD_RIGHT)playerJs("navigate('right')");if(key==KeyEvent.KEYCODE_DPAD_DOWN)playerJs("navigate('down')");if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER)playerJs("activate()");}return true;}
        if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){if(!controlsVisible){showControls(true);controls.focusTimeline();}keepControls();if(getCurrentFocus()==controls.seek){controls.seekKey(event);return true;}return super.dispatchKeyEvent(event);}
        if(key==KeyEvent.KEYCODE_DPAD_DOWN&&!controlsVisible){if(down){showControls(true);controls.focusButtons();}return true;}
        if((key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER)&&!controlsVisible){if(down&&event.getRepeatCount()==0){mediaCommand("toggle");showControls(true);controls.focusButtons();}return true;}
        if(controlsVisible)keepControls();return super.dispatchKeyEvent(event);
    }
    String readAsset(String name)throws IOException{try(InputStream in=getAssets().open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString(StandardCharsets.UTF_8.name());}}
    String time(long timestamp){return DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(timestamp));}
    int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    void gap(LinearLayout parent,int size){Space s=new Space(this);parent.addView(s,new LinearLayout.LayoutParams(parent.getOrientation()==LinearLayout.HORIZONTAL?dp(size):1,parent.getOrientation()==LinearLayout.VERTICAL?dp(size):1));}
    TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setIncludeFontPadding(false);if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));return t;}
    Button button(String label,String tag,Runnable action){
        Button b=new Button(this);b.setText(label);b.setTextSize(12);b.setTextColor(TEXT);b.setAllCaps(false);b.setMinHeight(dp(38));b.setMinimumHeight(dp(38));b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(13),dp(7),dp(13),dp(7));b.setBackground(focusBackground(0xff202936));b.setTag(tag);b.setContentDescription(label);b.setFocusable(true);b.setFocusableInTouchMode(true);b.setOnFocusChangeListener((v,f)->{b.setTextColor(f?BG:TEXT);v.animate().scaleX(f?1.025f:1f).scaleY(f?1.025f:1f).setDuration(120).start();});b.setOnClickListener(v->action.run());return b;
    }
    GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    StateListDrawable focusBackground(int base){StateListDrawable s=new StateListDrawable();GradientDrawable f=round(0xfff5f5f7,12);s.addState(new int[]{android.R.attr.state_focused},f);s.addState(new int[]{android.R.attr.state_pressed},round(0xff717582,12));s.addState(new int[]{},round(base,12));return s;}
    void toast(String text){if(visibleToast!=null)visibleToast.cancel();visibleToast=Toast.makeText(this,text,Toast.LENGTH_LONG);visibleToast.show();}
}
