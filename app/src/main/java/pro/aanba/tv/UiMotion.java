package pro.aanba.tv;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
final class UiMotion {
 static void enter(View v,float dy){if(!ValueAnimator.areAnimatorsEnabled())return;v.animate().cancel();v.setAlpha(0);v.setTranslationY(dy);v.animate().alpha(1).translationY(0).setInterpolator(new DecelerateInterpolator(1.5f)).setDuration(190).start();}
 static void visibility(View v,boolean show,float offset){v.animate().cancel();if(show){if(v.getVisibility()!=View.VISIBLE){v.setAlpha(0);v.setTranslationY(offset);v.setVisibility(View.VISIBLE);}v.animate().alpha(1).translationY(0).setDuration(180).setInterpolator(new DecelerateInterpolator()).start();}else if(v.getVisibility()==View.VISIBLE)v.animate().alpha(0).translationY(offset).setDuration(140).withEndAction(()->v.setVisibility(View.GONE)).start();}
}
