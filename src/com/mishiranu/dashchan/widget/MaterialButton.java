package com.mishiranu.dashchan.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.os.Build;
import android.widget.Button;

import androidx.annotation.RequiresApi;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.ResourceUtils;


@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
public class MaterialButton extends Button {
	float density = ResourceUtils.obtainDensity(this);

	public MaterialButton(Context context) {
		super(context, null, 0, C.API_MARSHMALLOW ? android.R.style.Widget_Material_Button_Colored : android.R.style.Widget_Material_Button);
		if (!C.API_LOLLIPOP_MR1) {
			// GradientDrawable doesn't support tints
			float radius = 2f * density;
			float[] radiusArray = {radius, radius, radius, radius, radius, radius, radius, radius};
			ShapeDrawable background = new ShapeDrawable() {
				@Override
				public void getOutline(Outline outline) {
					// Lollipop has broken RoundRectShape.getOutline
					Rect bounds = getBounds();
					outline.setRoundRect(bounds.left, bounds.top, bounds.right, bounds.bottom, radius);
				}
			};
			background.setShape(new RoundRectShape(radiusArray, null, null));
			setBackground(new InsetDrawable(background, (int) (4f * density),
					(int) (6f * density), (int) (4f * density), (int) (6f * density)));
		}

		setTextColor(ResourceUtils.getColorStateList(getContext(),
				android.R.attr.textColorPrimaryInverse));

		ThemeEngine.Theme theme = ThemeEngine.getTheme(getContext());
		int colorControlDisabled = GraphicsUtils.applyAlpha(theme.controlNormal21, theme.disabledAlpha21);
		int[][] states = {{-android.R.attr.state_enabled}, {}};
		int[] colors = {colorControlDisabled, theme.accent};
		setBackgroundTintList(new ColorStateList(states, colors));
		setSingleLine(true);
		setAllCaps(true);

	}

	@Override
	public void setTranslationZ(float translationZ) {
		float maxTranslationZ = (int) (2f * density);
		super.setTranslationZ(Math.min(translationZ, maxTranslationZ));
	}

}
