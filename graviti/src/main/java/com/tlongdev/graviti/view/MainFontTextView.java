package com.tlongdev.graviti.view;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.widget.TextView;

public class MainFontTextView extends TextView {
    public MainFontTextView(Context context) {
        super(context);
        init();
    }

    public MainFontTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MainFontTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        Typeface tf = Typeface.createFromAsset(getContext().getAssets(), "retrospective.otf");
        setTypeface(tf);
    }

}
