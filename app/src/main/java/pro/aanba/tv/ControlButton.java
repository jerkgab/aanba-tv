package pro.aanba.tv;
import android.graphics.*;
import android.view.*;
import android.widget.Button;
final class ControlButton extends View {
 final MainActivity a;final int kind;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final Path gear=new Path(),triangle=new Path();boolean state;
 ControlButton(MainActivity a,int kind,String label,Runnable action){super(a);this.a=a;this.kind=kind;setFocusable(true);setFocusableInTouchMode(true);setClickable(true);setContentDescription(label);setOnClickListener(v->action.run());
  triangle.moveTo(19,15);triangle.lineTo(34,24);triangle.lineTo(19,33);triangle.close();
  for(int i=0;i<32;i++){double angle=i*Math.PI/16-Math.PI/2;float r=i%4<2?12:9;float x=24+(float)Math.cos(angle)*r,y=24+(float)Math.sin(angle)*r;if(i==0)gear.moveTo(x,y);else gear.lineTo(x,y);}gear.close();
 }
 void state(boolean state,String label){this.state=state;setContentDescription(label);invalidate();}
 @Override protected void onFocusChanged(boolean gain,int direction,Rect old){super.onFocusChanged(gain,direction,old);animate().scaleX(gain?1.08f:1).scaleY(gain?1.08f:1).setDuration(140).start();invalidate();}
 @Override public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName(Button.class.getName());}
 @Override protected void onDraw(Canvas c){super.onDraw(c);c.save();c.scale(getWidth()/48f,getHeight()/48f);p.setStyle(Paint.Style.FILL);p.setColor(isFocused()?0xfff5f5f7:0x80252a34);c.drawCircle(24,24,22,p);p.setColor(isFocused()?0xff111319:0xfff5f5f7);p.setStrokeWidth(2.2f);p.setStrokeJoin(Paint.Join.ROUND);p.setStrokeCap(Paint.Cap.ROUND);
  if(kind==0){if(state){c.drawPath(triangle,p);}else{c.drawRoundRect(17,15,21,33,1,1,p);c.drawRoundRect(27,15,31,33,1,1,p);}}
  if(kind==1){p.setStyle(Paint.Style.STROKE);c.drawRect(12,20,18,28,p);c.drawLine(18,20,25,15,p);c.drawLine(25,15,25,33,p);c.drawLine(25,33,18,28,p);if(state){c.drawLine(30,19,37,29,p);c.drawLine(37,19,30,29,p);}else{c.drawArc(22,17,36,31,-60,120,false,p);c.drawArc(20,12,42,36,-53,106,false,p);}}
  if(kind==2){p.setStyle(Paint.Style.STROKE);c.drawPath(gear,p);c.drawCircle(24,24,4.2f,p);}
  if(kind==3){p.setStyle(Paint.Style.STROKE);c.drawArc(14,14,35,35,-65,300,false,p);c.drawLine(14,14,14,23,p);c.drawLine(14,23,22,23,p);}
  c.restore();
 }
}
