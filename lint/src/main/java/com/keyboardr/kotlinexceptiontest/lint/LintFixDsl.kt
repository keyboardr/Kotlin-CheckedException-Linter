@file:Suppress("UnstableApiUsage", "unused", "SameParameterValue")

package com.keyboardr.kotlinexceptiontest.lint

import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.LintFix.GroupType
import com.android.tools.lint.detector.api.LintFix.ReplaceStringBuilder
import com.android.tools.lint.detector.api.Location

@DslMarker
annotation class LintFixDsl

fun lintFix(block: LintFixBuilder.() -> LintFix): LintFix = LintFixBuilder().block()

interface LintFixDslScope {
    /**
     * Sets options related to auto-applying this fix. Convenience method for setting both
     * [ReplaceOptions.robot] and [ReplaceOptions.independent]
     */
    val autoFix: ReplaceOptions.() -> Unit
        get() = {
            robot = true
            independent = true
        }
}

private fun group(
    type: GroupType, name: String? = null, family: String? = null,
    block: GroupBuilder.() -> Unit
) =
    lintFix {
        name?.let { this@lintFix.name = name }
        family?.let { this@lintFix.family = family }
        when (type) {
            GroupType.COMPOSITE -> composite(block)
            GroupType.ALTERNATIVES -> group(block)
        }
    }

@LintFixDsl
class LintFixBuilder : LintFixDslScope {
    private val builder = LintFix.create()

    /**
     * Sets display name. If not supplied a default will be created based on the type of
     * quickfix.
     */
    var name = ""
        set(value) {
            field = value
            builder.name(value)
        }

    /**
     * Sets the "family" name; the shared name to use to apply *all* fixes of the same family
     * name in a single go. For example, lint may have registered a quickfix to update library
     * version from "1.3" to "1.4", and the display name for this quickfix is "Update version
     * from 1.3 to 1.4". When lint is run on a file, there may be a handful of libraries that
     * all are offered different version updates. If the lint fix provides a shared family name
     * for all of these, such as "Update Dependencies", then the IDE will show this as a single
     * action for the whole file and allow a single click to invoke all the actions in a single
     * go.
     */
    var family = ""
        set(value) {
            field = value
            builder.family(value)
        }

    /**
     * Create a group that represents several alternatives the user could choose from
     */
    fun group(block: GroupBuilder.() -> Unit): LintFix =
        GroupBuilderImpl(builder, GroupType.ALTERNATIVES)
            .apply(block).build()

    /**
     * Create a group where all fixes should be applied as one fix
     */
    fun composite(block: GroupBuilder.() -> Unit): LintFix =
        GroupBuilderImpl(builder, GroupType.COMPOSITE)
            .apply(block).build()

    /**
     * Replace a string or regular expression
     * @param target
     * @param with The text to replace the old text or pattern with. Note that the special syntax
     * \k&lt;n&gt; can be used to reference the n'th group, if and only if this replacement is
     * using {@link #pattern(String)}}.
     * @param range Sets a location range to use for searching for the text or pattern. Useful if you want to
     * make a replacement that is larger than the error range highlighted as the problem range.
     * @param options
     */
    fun replace(
        target: ReplaceTarget, with: String? = null,
        range: Location? = null,
        options: ReplaceOptions.() -> Unit = {}
    ): LintFix {
        return builder.replace().apply {
            target.apply(this)
            with?.let { with(with) }
            range?.let { range(range) }
            val builtOptions = ReplaceOptions().apply(options)
            if (builtOptions.shortenNames) {
                shortenNames()
            }
            if (builtOptions.reformat) {
                reformat(true)
            }
            autoFix(builtOptions.robot, builtOptions.independent)
        }.build()
    }
}

@LintFixDsl
interface GroupBuilder : LintFixDslScope {
    /**
     * Create a group where all fixes should be applied as one fix
     */
    fun composite(name: String? = null, family: String? = null, block: GroupBuilder.() -> Unit)

    /**
     * Replace a string or regular expression
     */
    fun replace(
        target: ReplaceTarget, with: String? = null,
        range: Location? = null,
        name: String? = null, family: String? = null,
        options: ReplaceOptions.() -> Unit = {}
    )
}

@LintFixDsl
class GroupBuilderImpl(parent: LintFix.Builder, type: GroupType) : GroupBuilder {

    private val builder = when (type) {
        GroupType.ALTERNATIVES -> parent.group()
        GroupType.COMPOSITE -> parent.composite()
    }

    fun build(): LintFix = builder.build()

    override fun composite(name: String?, family: String?, block: GroupBuilder.() -> Unit) {
        builder.add(group(GroupType.COMPOSITE, name, family, block))
    }

    override fun replace(
        target: ReplaceTarget, with: String?,
        range: Location?,
        name: String?, family: String?,
        options: ReplaceOptions.() -> Unit
    ) {
        builder.add(LintFixBuilder().replace(target, with, range, options))
    }
}

sealed class ReplaceTarget(val apply: ReplaceStringBuilder.() -> ReplaceStringBuilder) {

    /** Replaces this entire range */
    object All : ReplaceTarget({ all() })

    /** Inserts into the beginning of the range */
    object Beginning : ReplaceTarget({ beginning() })

    /** Inserts after the end of the range */
    object End : ReplaceTarget({ end() })

    /** Replaces the given literal text */
    data class Text(val text: String) : ReplaceTarget({ text(text) })

    /** Replaces the given pattern match (or the first group within it, if any) */
    data class Pattern(val pattern: Regex) : ReplaceTarget({ pattern(pattern.pattern) })

    /**
     * Sets a pattern to select; if it contains parentheses, group(1) will be selected. To just
     * set the caret, use an empty group.
     */
    data class Select(val pattern: Regex) : ReplaceTarget({ select(pattern.pattern) })
}

@LintFixDsl
class ReplaceOptions : LintFixDslScope {
    var shortenNames = false
    var reformat = false
    var robot = false
    var independent = false
}
