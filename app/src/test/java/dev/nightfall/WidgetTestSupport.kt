// SPDX-License-Identifier: MIT
package dev.nightfall

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView

/**
 * What the launcher would inflate from these `RemoteViews`, unwrapped so a test can read it.
 *
 * `apply` runs the real action list against real views, which is why the label and the spoken
 * description are read from the views and not from the state that produced them.
 */
internal fun RemoteViews.inflated(context: Context): View = apply(context, FrameLayout(context))

internal fun RemoteViews.stateLabel(context: Context): String =
    inflated(context).findViewById<TextView>(R.id.widget_state).text.toString()

internal fun RemoteViews.spoken(context: Context): String? =
    inflated(context).findViewById<View>(R.id.widget_root).contentDescription?.toString()

/**
 * The drawable id a `RemoteViews` told an `ImageView` to show. Android keeps the action list
 * private, and an inflated vector drawable does not answer "which resource are you", so the test
 * reads the recorded action instead — the same list the launcher replays. A missing field returns
 * null, which fails every assertion that uses it rather than passing quietly.
 */
internal fun RemoteViews.imageResourceId(viewId: Int): Int? {
    val actions = RemoteViews::class.java.getDeclaredField("mActions").apply { isAccessible = true }
    for (action in actions.get(this) as List<*>) {
        if (action == null) continue
        // The action classes are nested: the view id lives on RemoteViews.Action, the method and
        // the value on its reflection subclasses.
        var type: Class<*>? = action.javaClass
        val fields = mutableMapOf<String, Any?>()
        while (type != null) {
            for (field in type.declaredFields) {
                if (fields.containsKey(field.name)) continue
                fields[field.name] =
                    runCatching { field.apply { isAccessible = true }.get(action) }.getOrNull()
            }
            type = type.superclass
        }
        if (fields["mViewId"] != viewId) continue
        // setImageViewResource records itself as a call to ImageView.setImageResource.
        if (fields["mMethodName"] != "setImageResource") continue
        return fields["mValue"] as? Int
    }
    return null
}
