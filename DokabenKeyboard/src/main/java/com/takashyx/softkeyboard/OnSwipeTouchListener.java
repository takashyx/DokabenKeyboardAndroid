package com.takashyx.softkeyboard;

import android.content.Context;
import android.inputmethodservice.KeyboardView;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;

/**
 * Created by takashyx on 2017/06/30.
 */

public class OnSwipeTouchListener implements OnTouchListener {

    // 最低スワイプ距離
    private static final int SWIPE_MIN_DISTANCE = 10;
    // 最低スワイプスピード
    private static final int SWIPE_THRESHOLD_VELOCITY = 10;

    private final GestureDetector gestureDetector;

    public OnSwipeTouchListener(Context ctx) {
        gestureDetector = new GestureDetector(ctx, new GestureListener());
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        return gestureDetector.onTouchEvent(event);
    }

    private final class GestureListener extends SimpleOnGestureListener {

        private static final int SWIPE_THRESHOLD = 10;
        private static final int SWIPE_VELOCITY_THRESHOLD = 10;

        /* do not override onDown to handle key
        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }
        */

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            boolean result = false;
            try {

                // 移動距離・スピードを出力
                float distance_x = Math.abs((e1.getX() - e2.getX()));
                float velocity_x = Math.abs(velocityX);

                // 開始位置から終了位置の移動距離が指定値より大きい
                // X軸の移動速度が指定値より大きい
                if(Math.abs(velocityX) > Math.abs(velocityY))
                {
                    if (e1.getX() - e2.getX() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                        onKeySwipeLeft();
                    }
                    // 終了位置から開始位置の移動距離が指定値より大きい
                    // X軸の移動速度が指定値より大きい
                    else if (e2.getX() - e1.getX() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                        onKeySwipeRight();
                    }
                }
                else
                {
                    if (e1.getY() - e2.getY() > SWIPE_MIN_DISTANCE && Math.abs(velocityY) > SWIPE_THRESHOLD_VELOCITY) {
                        onKeySwipeUp();

                    }
                    // 終了位置から開始位置の移動距離が指定値より大きい
                    // X軸の移動速度が指定値より大きい
                    else if (e2.getY() - e1.getY() > SWIPE_MIN_DISTANCE && Math.abs(velocityY) > SWIPE_THRESHOLD_VELOCITY) {
                        onKeySwipeDown();
                    }
                }
            }
            catch (Exception exception) {
                exception.printStackTrace();
            }
            return result;
        }
    }
    public void onKeySwipeRight() {};

    public void onKeySwipeLeft() {};

    public void onKeySwipeUp() {};

    public void onKeySwipeDown() {};
}