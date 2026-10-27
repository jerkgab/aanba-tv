package pro.aanba.tv;
import android.graphics.*;
import android.widget.LinearLayout;
final class ProgressCard extends LinearLayout {
 final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final MainActivity a;float progress;boolean completed;
 ProgressCard(MainActivity a,WatchHistory.Entry entry){super(a);this.a=a;setOrientation(VERTICAL);setWillNotDraw(false);if(entry!=null){progress=(float)entry.fraction();completed=entry.completed;}}
 @Override protected void dispatchDraw(Canvas canvas){super.dispatchDraw(canvas);if(progress<=0&&!completed)return;float left=a.dp(16),right=getWidth()-a.dp(16),y=getHeight()-a.dp(5),height=a.dp(2);paint.setColor(0x30ffffff);canvas.drawRoundRect(left,y,right,y+height,height,height,paint);paint.setColor(completed?0xff77d6aa:0xfff5f5f7);canvas.drawRoundRect(left,y,left+(right-left)*(completed?1:progress),y+height,height,height,paint);}
}
