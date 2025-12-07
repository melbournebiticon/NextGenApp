package com.finale.nextgen.teacher;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.HorizontalScrollView;

/**
 * HorizontalScrollView that exposes a scroll listener callback so header and rows
 * can be synchronized.
 */
public class ObservableHorizontalScrollView extends HorizontalScrollView {

    public interface OnScrollChangedListener {
        void onScrollChanged(ObservableHorizontalScrollView scrollView, int x, int y, int oldx, int oldy);
    }

    private OnScrollChangedListener listener;

    public ObservableHorizontalScrollView(Context context) {
        super(context);
    }

    public ObservableHorizontalScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ObservableHorizontalScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setOnScrollChangedListener(OnScrollChangedListener l) {
        this.listener = l;
    }

    @Override
    protected void onScrollChanged(int x, int y, int oldx, int oldy) {
        super.onScrollChanged(x, y, oldx, oldy);
        if (listener != null) listener.onScrollChanged(this, x, y, oldx, oldy);
    }
}