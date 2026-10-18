/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 *
 * Adapted from HushGram's "Hide Follow on stories" patch, Copyright 2026 HushGram contributors,
 * GPL-3.0: https://github.com/SysAdminDoc/HushGram
 */

package app.crimera.patches.instagram.misc.stories.hideStoryFollow

import app.crimera.patches.instagram.links.privacy.STORY_HEADER_HOLDER_PARAMETER
import app.crimera.patches.instagram.links.privacy.StoryHeaderBindingFingerprint
import app.crimera.patches.instagram.links.privacy.storyRootViewField
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel

private const val STORY_FOLLOW_BUTTON_DESCRIPTOR = "$PATCHES_DESCRIPTOR/story/StoryFollowButton;"

@Suppress("unused")
val hideStoryFollowButtonPatch =
    bytecodePatch(
        name = "Hide story follow button",
        description = "Hides the Follow button in the story header for accounts you don't follow.",
    ) {
        dependsOn(settingsPatch)

        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            val binder = StoryHeaderBindingFingerprint.method
            val holderClass = binder.parameterTypes[STORY_HEADER_HOLDER_PARAMETER].toString()
            val rootViewField = storyRootViewField(holderClass)

            // v0 is a free local before Instagram's first instruction, and nothing here branches
            // except the null guard, which falls through to Instagram's own code.
            binder.addInstructionsWithLabels(
                0,
                """
                if-eqz p$STORY_HEADER_HOLDER_PARAMETER, :piko_skip_story_follow
                iget-object v0, p$STORY_HEADER_HOLDER_PARAMETER, $rootViewField
                invoke-static {v0}, $STORY_FOLLOW_BUTTON_DESCRIPTOR->bind(Landroid/view/View;)V
                """.trimIndent(),
                ExternalLabel("piko_skip_story_follow", binder.getInstruction(0)),
            )

            enableSettings("hideStoryFollowButton")
        }
    }
