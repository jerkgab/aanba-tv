package pro.aanba.tv;
import android.animation.ValueAnimator;
import android.graphics.*;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import java.lang.ref.WeakReference;
final class FocusMotion extends View {
 final Rect rect=new Rect();final RectF end=new RectF();final int[] at=new int[2];final View root;final float radius;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final RectF from=new RectF(),current=new RectF();WeakReference<View> target=new WeakReference<>(null);ValueAnimator animation;float progress=1;boolean visible;
 FocusMotion(MainActivity a,View root){super(a);this.root=root;radius=a.dp(16);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);setFocusable(false);setClickable(false);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(a.dp(1.5f));paint.setColor(0x99ffffff);}
 void move(View view){view.post(()->{if(!view.isAttachedToWindow())return;if(animation!=null)animation.cancel();from.set(current);boolean had=visible;target=new WeakReference<>(view);visible=true;progress=had?0:1;animation=ValueAnimator.ofFloat(progress,1);animation.setDuration(had?210:120);animation.setInterpolator(new DecelerateInterpolator(1.5f));animation.addUpdateListener(v->{progress=(float)v.getAnimatedValue();invalidate();});animation.start();});}
 void hide(){visible=false;invalidate();}
 @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);View selected=target.get();if(!visible||selected==null)return;if(!selected.getGlobalVisibleRect(rect))return;root.getLocationOnScreen(at);end.set(rect);end.offset(-at[0],-at[1]);if(progress>=1||from.isEmpty())current.set(end);else current.set(from.left+(end.left-from.left)*progress,from.top+(end.top-from.top)*progress,from.right+(end.right-from.right)*progress,from.bottom+(end.bottom-from.bottom)*progress);current.inset(1,1);canvas.drawRoundRect(current,Math.min(current.height()/2,radius),Math.min(current.height()/2,radius),paint);}
 @Override protected void onDetachedFromWindow(){if(animation!=null)animation.cancel();super.onDetachedFromWindow();}
}
