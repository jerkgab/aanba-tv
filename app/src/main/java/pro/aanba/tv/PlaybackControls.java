package pro.aanba.tv;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.Locale;

/** TV controller with a full-width timeline and sound/settings at the right edge. */
final class PlaybackControls {
 final MainActivity a;final LinearLayout view,buttons;final SeekBar seek;final TextView time,hint,title,subtitle,preview;final ControlButton play,restart,sound,settings;final SeekModel position=new SeekModel();boolean live,paused;
 PlaybackControls(MainActivity a){
  this.a=a;view=a.column();view.setPadding(a.dp(30),a.dp(28),a.dp(30),a.dp(16));view.setVisibility(View.GONE);view.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00000000,0xd9090a0f,0xfa090a0f}));
  LinearLayout heading=a.row();heading.setGravity(Gravity.CENTER_VERTICAL);view.addView(heading);title=a.text("AANBA TV",18,MainActivity.TEXT,true);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));hint=a.text("Удерживать ↑ — статистика",10,MainActivity.MUTED,false);heading.addView(hint);subtitle=a.text("AANBA  /  VK Видео",10,MainActivity.MUTED,false);subtitle.setPadding(0,a.dp(5),0,a.dp(2));view.addView(subtitle);
  preview=a.text("",17,MainActivity.TEXT,true);preview.setGravity(Gravity.CENTER);preview.setPadding(0,a.dp(3),0,0);view.addView(preview,new LinearLayout.LayoutParams(-1,a.dp(23)));
  seek=new SeekBar(a);seek.setTag("timeline");seek.setMax(10000);seek.setFocusable(true);seek.setFocusableInTouchMode(true);seek.setContentDescription("Шкала перемотки");seek.setProgressTintList(ColorStateList.valueOf(0xffff5361));seek.setProgressBackgroundTintList(ColorStateList.valueOf(0xff626672));seek.setSecondaryProgressTintList(ColorStateList.valueOf(0xffa2a6af));seek.setThumbTintList(ColorStateList.valueOf(MainActivity.TEXT));view.addView(seek,new LinearLayout.LayoutParams(-1,a.dp(28)));
  seek.setOnFocusChangeListener((v,f)->{seek.animate().scaleY(f?1.15f:1).setDuration(140).start();});
  seek.setOnKeyListener((v,key,event)->{if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){seekKey(event);return true;}if((key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER)&&event.getAction()==KeyEvent.ACTION_DOWN){if(position.seeking)commit();else a.mediaCommand("toggle");a.keepControls();return true;}return false;});
  seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){position.progress(b.getProgress());}public void onProgressChanged(SeekBar b,int value,boolean user){if(user){position.progress(value);labels();a.keepControls();}}public void onStopTrackingTouch(SeekBar b){commit();}});
  buttons=a.row();buttons.setClipChildren(false);buttons.setGravity(Gravity.CENTER_VERTICAL);view.addView(buttons,new LinearLayout.LayoutParams(-1,a.dp(52)));
  play=new ControlButton(a,0,"Пауза",()->{a.mediaCommand("toggle");a.keepControls();});restart=new ControlButton(a,3,"С начала",()->{a.restartRecording();a.keepControls();});sound=new ControlButton(a,1,"Звук",()->{a.playerJs("muteToggle()");a.keepControls();});settings=new ControlButton(a,2,"Настройки VK",a::openVkSettings);
  add(play);a.gap(buttons,10);add(restart);a.gap(buttons,13);time=a.text("Плеер загружается…",12,MainActivity.TEXT,true);buttons.addView(time);buttons.addView(new Space(a),new LinearLayout.LayoutParams(0,1,1));add(sound);a.gap(buttons,12);add(settings);
  seek.setId(View.generateViewId());for(View button:new View[]{play,restart,sound,settings}){button.setId(View.generateViewId());button.setNextFocusUpId(seek.getId());}seek.setNextFocusDownId(play.getId());play.setNextFocusLeftId(play.getId());play.setNextFocusRightId(restart.getId());restart.setNextFocusLeftId(play.getId());restart.setNextFocusRightId(sound.getId());sound.setNextFocusLeftId(restart.getId());sound.setNextFocusRightId(settings.getId());settings.setNextFocusLeftId(sound.getId());settings.setNextFocusRightId(settings.getId());
 }
 void add(View v){buttons.addView(v,new LinearLayout.LayoutParams(a.dp(42),a.dp(42)));}
 void show(boolean show){UiMotion.visibility(view,show,a.dp(14));}
 void update(JSONObject state){
  paused=state.optBoolean("paused",true);live=state.optBoolean("live");position.update(state.optDouble("start",0),state.optDouble("end",0),state.optDouble("time",0),state.optBoolean("canSeek"));seek.setEnabled(position.enabled);seek.setProgress(position.progress());double buffered=state.optDouble("buffered",position.current);seek.setSecondaryProgress(position.enabled?(int)Math.max(0,Math.min(10000,(buffered-position.start)/(position.end-position.start)*10000)):0);
  play.state(paused,paused?"Смотреть":"Пауза");sound.state(state.optBoolean("muted"),state.optBoolean("muted")?"Включить звук":"Выключить звук");restart.setVisibility(live?View.GONE:View.VISIBLE);play.setNextFocusRightId(live?sound.getId():restart.getId());sound.setNextFocusLeftId(live?play.getId():restart.getId());
  if(a.watching!=null){String name=a.watching.home+" — "+a.watching.away;if(!name.contentEquals(title.getText()))title.setText(name);}subtitle.setText(live?"AANBA  /  Прямой эфир":"AANBA  /  Запись трансляции");labels();
 }
 void seekKey(KeyEvent event){a.keepControls();if(event.getAction()==KeyEvent.ACTION_DOWN){position.step(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1,event.getRepeatCount());seek.setProgress(position.progress());labels();}else if(event.getAction()==KeyEvent.ACTION_UP){if(event.isCanceled())position.cancel();else if(position.seeking)commit();}}
 void commit(){if(!position.enabled)return;a.playerJs("seekTo("+position.finish()+")");labels();a.keepControls();}
 void labels(){time.setText(position.enabled?format(position.draft)+" / "+format(position.end)+(live?" · LIVE":""):live?"Прямой эфир":"Плеер загружается…");preview.setText(position.seeking?format(position.draft):"");}
 void focusTimeline(){if(seek.isEnabled())seek.requestFocus();else play.requestFocus();}void focusButtons(){play.requestFocus();}void focusSettings(){settings.requestFocus();}
 static String format(double value){long s=(long)Math.max(0,value);return s>=3600?String.format(Locale.US,"%d:%02d:%02d",s/3600,s/60%60,s%60):String.format(Locale.US,"%d:%02d",s/60,s%60);}
}
