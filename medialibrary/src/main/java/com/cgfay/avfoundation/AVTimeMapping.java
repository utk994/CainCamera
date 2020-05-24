package com.cgfay.avfoundation;

import androidx.annotation.NonNull;

/**
 * 时间映射关系
 */
public class AVTimeMapping {

    // 源时间区间
    private AVTimeRange source;

    // 目的时间区间
    private AVTimeRange target;

    public AVTimeMapping(@NonNull AVTimeRange source, @NonNull AVTimeRange target) {
        this.source = source;
        this.target = target;
    }

    public AVTimeRange getSource() {
        return source;
    }

    public AVTimeRange getTarget() {
        return target;
    }

    @NonNull
    @Override
    public String toString() {
        return "{" + source.toString() + ", " + target.toString() + "}";
    }
}