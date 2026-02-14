package com.mishiranu.dashchan.text.style;

import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.UpdateAppearance;

import com.mishiranu.dashchan.graphics.ColorScheme;
import com.mishiranu.dashchan.util.GraphicsUtils;

public class NeuroslopSpan extends CharacterStyle implements UpdateAppearance, ColorScheme.Span {

    private int backgroundColor;

    @Override
    public void updateDrawState(TextPaint paint) {
        paint.bgColor = backgroundColor;
    }

    @Override
    public void applyColorScheme(ColorScheme colorScheme) {
        if (colorScheme != null) {
            backgroundColor = colorScheme.neuroslopColor;
        }
    }

}
