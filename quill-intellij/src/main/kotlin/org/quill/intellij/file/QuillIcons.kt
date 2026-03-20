package org.quill.intellij.file

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * Icons used in the Quill plugin.
 */
object QuillIcons {
    @JvmField
    val FILE: Icon = IconLoader.getIcon("/icons/quill.svg", QuillIcons::class.java)

    @JvmField
    val FUNCTION: Icon = IconLoader.getIcon("/icons/function.svg", QuillIcons::class.java)

    @JvmField
    val CLASS: Icon = IconLoader.getIcon("/icons/class.svg", QuillIcons::class.java)

    @JvmField
    val VARIABLE: Icon = IconLoader.getIcon("/icons/variable.svg", QuillIcons::class.java)

    @JvmField
    val CONSTANT: Icon = IconLoader.getIcon("/icons/constant.svg", QuillIcons::class.java)
}
