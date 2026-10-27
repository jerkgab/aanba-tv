package pro.aanba.tv;

/** Monotonic remote gestures, independent of Android key-repeat speed. */
final class RemoteGestures {
    static final long HOLD_MS=550, DOUBLE_BACK_MS=1200;
    static final int NONE=0, STEP=1, PANEL=2;
    private long upAt=-1, backAt=-1;
    private boolean upConsumed;
    void upDown(long now){if(upAt<0){upAt=now;upConsumed=false;}}
    boolean upHeld(long now){
        if(upAt<0||upConsumed||now-upAt<HOLD_MS)return false;
        upConsumed=true;return true;
    }
    int upUp(long now){
        if(upAt<0)return NONE;
        int action=upConsumed?NONE:now-upAt>=HOLD_MS?PANEL:STEP;
        cancelUp();return action;
    }
    void cancelUp(){upAt=-1;upConsumed=false;}
    boolean back(long now){
        boolean exit=backAt>=0&&now>=backAt&&now-backAt<=DOUBLE_BACK_MS;
        backAt=exit?-1:now;return exit;
    }
    void resetBack(){backAt=-1;}
}
