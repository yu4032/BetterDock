package com.hellovoid.betterdock;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.Locale;
import androidx.preference.Preference;

public class SeekBarPreference extends Preference implements SeekBar.OnSeekBarChangeListener {

    private int value, min, max;
    private SeekBar seekBar;
    private EditText valueInput;
    private TextView summaryView;
    private boolean bindingView, updating;
    private final CharSequence summaryTemplate;

    public SeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SeekBarPreference);
        min = a.getInt(R.styleable.SeekBarPreference_min, 0);
        max = a.getInt(R.styleable.SeekBarPreference_max, 100);
        a.recycle();
        summaryTemplate = getSummary();
        setLayoutResource(R.layout.pref_seekbar);
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        value = defaultValue instanceof Integer ? (Integer) defaultValue : min;
        if (shouldPersist()) value = getPersistedInt(value);
    }

    @Override
    public void onBindViewHolder(androidx.preference.PreferenceViewHolder holder) {
        bindingView = true;
        super.onBindViewHolder(holder);
        seekBar = (SeekBar) holder.findViewById(R.id.seekbar);
        valueInput = (EditText) holder.findViewById(R.id.value_input);
        summaryView = (TextView) holder.findViewById(android.R.id.summary);

        if (seekBar != null) {
            seekBar.setOnSeekBarChangeListener(null);
            seekBar.setMax(max - min);
            seekBar.setProgress(value - min);
            seekBar.setTag(this);
            seekBar.setOnSeekBarChangeListener(this);
        }
        if (valueInput != null) {
            Object oldWatcher = valueInput.getTag(R.id.value_input);
            if (oldWatcher instanceof TextWatcher)
                valueInput.removeTextChangedListener((TextWatcher) oldWatcher);
            valueInput.setText(String.valueOf(value));
            valueInput.addTextChangedListener(watcher);
            valueInput.setTag(R.id.value_input, watcher);
        }
        applySummary();
        bindingView = false;
    }

    @Override
    public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
        if (bindingView || updating || sb.getTag() != this) return;
        value = progress + min;
        persistInt(value);
        applySummary();
        if (valueInput != null) {
            updating = true;
            valueInput.setText(String.valueOf(value));
            updating = false;
        }
    }

    @Override public void onStartTrackingTouch(SeekBar sb) {}
    @Override public void onStopTrackingTouch(SeekBar sb) {}

    private void applySummary() {
        if (summaryView == null) return;
        if (summaryTemplate != null && summaryTemplate.toString().contains("%d"))
            summaryView.setText(String.format(Locale.getDefault(), summaryTemplate.toString(), value));
        else summaryView.setText(String.valueOf(value));
    }

    private final TextWatcher watcher = new TextWatcher() {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable s) {
            if (updating) return;
            try {
                int v = Integer.parseInt(s.toString().trim());
                if (v >= min && v <= max && v != value) {
                    value = v;
                    updating = true;
                    if (seekBar != null) seekBar.setProgress(v - min);
                    updating = false;
                    persistInt(value);
                    applySummary();
                }
            } catch (NumberFormatException ignored) {}
        }
    };

}
