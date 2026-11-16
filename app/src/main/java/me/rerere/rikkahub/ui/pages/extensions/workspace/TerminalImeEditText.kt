package me.rerere.rikkahub.ui.pages.extensions.workspace

import android.content.Context
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText

/**
 * 终端页的 IME 宿主。
 *
 * `terminal-view` 的 TerminalView 把自己的 inputType 压成 TYPE_NULL/无文本类，Gboard、Fcitx
 * 等输入法只会把拼音候选提交到一个不存在的编辑框里。这里用一个真正的 EditText 接收组合文本，
 * 只在 commitText 时把最终字符写给终端；控制键仍交给 TerminalView 处理。
 */
internal class TerminalImeEditText(context: Context) : EditText(context) {
    var onCommitText: ((String) -> Unit)? = null
    var onDelete: (() -> Unit)? = null
    var onKeyEvent: ((Int, KeyEvent) -> Boolean)? = null

    init {
        background = null
        setTextColor(0x00000000)
        setHintTextColor(0x00000000)
        isFocusable = true
        isFocusableInTouchMode = true
        isSingleLine = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or
            EditorInfo.IME_FLAG_NO_EXTRACT_UI
        return object : BaseInputConnection(this@TerminalImeEditText, true) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                super.commitText(text, newCursorPosition)
                sendEditableToTerminal()
                return true
            }

            override fun finishComposingText(): Boolean {
                super.finishComposingText()
                sendEditableToTerminal()
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                repeat(beforeLength.coerceAtLeast(0)) { onDelete?.invoke() }
                return super.deleteSurroundingText(beforeLength, afterLength)
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action != KeyEvent.ACTION_DOWN) return true
                when (event.keyCode) {
                    KeyEvent.KEYCODE_ENTER -> onCommitText?.invoke("\r")
                    KeyEvent.KEYCODE_DEL -> onDelete?.invoke()
                    KeyEvent.KEYCODE_TAB -> onCommitText?.invoke("\t")
                    else -> return onKeyEvent?.invoke(event.keyCode, event) ?: false
                }
                return true
            }

            private fun sendEditableToTerminal() {
                val value = editable?.toString().orEmpty()
                if (value.isNotEmpty()) onCommitText?.invoke(value)
                editable?.clear()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (onKeyEvent?.invoke(keyCode, event) == true) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (onKeyEvent?.invoke(keyCode, event) == true) return true
        return super.onKeyUp(keyCode, event)
    }
}
