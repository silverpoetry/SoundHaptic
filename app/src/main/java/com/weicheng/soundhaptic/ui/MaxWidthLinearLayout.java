package com.weicheng.soundhaptic.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import com.weicheng.soundhaptic.R;

/** Centers the settings form while keeping readable line lengths on tablets and freeform windows. */
public final class MaxWidthLinearLayout extends LinearLayout {
    private final int maxWidth;

    public MaxWidthLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        maxWidth = getResources().getDimensionPixelSize(R.dimen.content_max_width);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int available = MeasureSpec.getSize(widthMeasureSpec);
        int width = Math.min(available, maxWidth);
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                heightMeasureSpec);
    }
}
