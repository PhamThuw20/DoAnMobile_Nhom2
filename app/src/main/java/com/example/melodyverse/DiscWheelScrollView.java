package com.example.melodyverse;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;

/**
 * ScrollView của trang Khám phá. Khi lướt xuống trên vòng xoay đĩa than, trang cuộn hết trước
 * (để vòng xoay hiện trọn), sau đó vòng xoay mới bắt đầu quay. Lướt lên thì vòng xoay quay về trước, rồi tới trang.
 */
public class DiscWheelScrollView extends NestedScrollView {

    public DiscWheelScrollView(@NonNull Context context) {
        super(context);
    }

    public DiscWheelScrollView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onNestedPreScroll(@NonNull View target, int dx, int dy, @NonNull int[] consumed, int type) {
        if (dy > 0 && canScrollVertically(1)) {
            int before = getScrollY();
            scrollBy(0, dy);
            consumed[1] += getScrollY() - before;
            return;
        }
        super.onNestedPreScroll(target, dx, dy, consumed, type);
    }

    @Override
    public boolean onNestedPreFling(@NonNull View target, float velocityX, float velocityY) {
        if (velocityY > 0 && canScrollVertically(1)) {
            fling((int) velocityY);
            return true;
        }
        return super.onNestedPreFling(target, velocityX, velocityY);
    }
}
