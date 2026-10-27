package pro.aanba.tv;

/** Seconds, not pixels: one model for recordings, DVR ranges and key-repeat acceleration. */
final class SeekModel {
    double start,end,current,draft;boolean enabled,seeking;
    void update(double start,double end,double current,boolean allowed){
        this.enabled=allowed&&Double.isFinite(start)&&Double.isFinite(end)&&end>start;
        this.start=Double.isFinite(start)?Math.max(0,start):0;this.end=Double.isFinite(end)?Math.max(this.start,end):this.start;
        this.current=clamp(Double.isFinite(current)?current:this.start);if(!seeking)draft=this.current;else draft=clamp(draft);
    }
    double clamp(double value){return Math.max(start,Math.min(end,value));}
    void step(int direction,int repeats){if(!enabled)return;if(!seeking){draft=current;seeking=true;}draft=clamp(draft+direction*Math.min(120,10*(1+Math.max(0,repeats)/4)));}
    double finish(){seeking=false;current=draft=clamp(draft);return current;}
    void cancel(){seeking=false;draft=current;}
    int progress(){return !enabled?0:(int)Math.round((draft-start)/(end-start)*10000);}
    void progress(int progress){if(!enabled)return;draft=start+(end-start)*Math.max(0,Math.min(10000,progress))/10000d;seeking=true;}
}
