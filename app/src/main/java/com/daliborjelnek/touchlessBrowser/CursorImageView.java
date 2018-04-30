package com.daliborjelnek.touchlessBrowser;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.util.AttributeSet;

/**
 * Created by Dalibor Jelínek on 13.04.2018.
 */


public class CursorImageView extends android.support.v7.widget.AppCompatImageView {


    public CursorImageView(Context context, AttributeSet attrs) {
        super(context, attrs);

    }


    public void moveTo(int x,int y){
            ObjectAnimator animX = ObjectAnimator.ofFloat(this, "x", x);
            ObjectAnimator animY = ObjectAnimator.ofFloat(this, "y", y);
            AnimatorSet animSetXY = new AnimatorSet();
            animSetXY.playTogether(animX, animY);
            animSetXY.start();
            animSetXY.setDuration(500);

    }


}
