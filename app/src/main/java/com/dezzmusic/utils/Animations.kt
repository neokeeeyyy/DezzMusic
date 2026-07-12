package com.dezzmusic.utils

import android.app.Activity
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import com.dezzmusic.R

object Animations {

    fun slideUp(activity: Activity) {
        activity.overridePendingTransition(R.anim.slide_in_bottom, R.anim.slide_out_bottom)
    }

    fun slideDown(activity: Activity) {
        activity.overridePendingTransition(R.anim.slide_in_top, R.anim.slide_out_top)
    }

    fun slideLeft(activity: Activity) {
        activity.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    fun slideRight(activity: Activity) {
        activity.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    fun fadeIn(activity: Activity) {
        activity.overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    fun scaleUp(activity: Activity) {
        activity.overridePendingTransition(R.anim.scale_in, R.anim.fade_out)
    }

    fun sharedElementEnter(activity: Activity) {
        activity.overridePendingTransition(R.anim.shared_enter, R.anim.shared_exit)
    }

    fun customTransition(activity: Activity, enterAnim: Int, exitAnim: Int) {
        activity.overridePendingTransition(enterAnim, exitAnim)
    }
}
