/*
 * IdeaVim - Vim emulator for IDEs based on the IntelliJ platform
 * Copyright (C) 2003-2020 The IdeaVim authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package org.jetbrains.plugins.ideavim.propertybased

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.editor.Editor
import com.intellij.testFramework.PlatformTestUtil
import com.maddyhome.idea.vim.KeyHandler
import com.maddyhome.idea.vim.VimPlugin
import com.maddyhome.idea.vim.command.CommandState
import com.maddyhome.idea.vim.helper.EditorDataContext
import com.maddyhome.idea.vim.helper.StringHelper
import com.maddyhome.idea.vim.helper.StringHelper.parseKeys
import com.maddyhome.idea.vim.key.CommandNode
import org.jetbrains.jetCheck.Generator
import org.jetbrains.jetCheck.ImperativeCommand
import org.jetbrains.jetCheck.PropertyChecker
import org.jetbrains.plugins.ideavim.VimTestCase
import org.junit.Ignore
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

class TestPropertyBased : VimTestCase() {
  fun testMovingAround() {
    PropertyChecker.checkScenarios {
      ImperativeCommand { env ->
        val editor = configureByText("""
          I found it in a legendary land
          all rocks and lavender and tufted grass,
          where it was settled on some sodden sand
          hard by the torrent of a mountain pass.
        """.trimIndent())
        try {
          env.executeCommands(Generator.sampledFrom(VimCommand(editor)))
        } finally {
          reset(editor)
        }
      }
    }
  }

  @Ignore
  fun testRandomActions() {
    PropertyChecker.checkScenarios {
      ImperativeCommand { env ->
        val editor = configureByText("""
          ${c}I found it in a legendary land
          all rocks and lavender and tufted grass,
          where it was settled on some sodden sand
          hard by the torrent of a mountain pass.
        """.trimIndent())
        try {
          env.executeCommands(Generator.sampledFrom(TrieBasedActions(editor)))
        } finally {
          reset(editor)
        }
      }
    }
  }

  private fun reset(editor: Editor) {
    KeyHandler.getInstance().fullReset(editor)
    VimPlugin.getRegister().resetRegisters()
    editor.caretModel.runForEachCaret { it.moveToOffset(0) }

    CommandState.getInstance(editor).resetDigraph()
  }
}

private class VimCommand(private val editor: Editor) : ImperativeCommand {
  override fun performCommand(env: ImperativeCommand.Environment) {
    val motion = env.generateValue(Generator.sampledFrom("h", "j", "k", "l").map { parseKeys(it).single() }, "Generator motion: %s")
    KeyHandler.getInstance().handleKey(editor, motion, EditorDataContext(editor))
  }
}

private class TrieBasedActions(private val editor: Editor) : ImperativeCommand {
  override fun performCommand(env: ImperativeCommand.Environment) {
    val currentNode = CommandState.getInstance(editor).commandBuilder.getCurrentTrie()
    val possibleKeys = currentNode.keys.toList().sortedBy { StringHelper.toKeyNotation(it) }
    val keyGenerator = Generator.integers(0, possibleKeys.lastIndex).suchThat { StringHelper.toKeyNotation(possibleKeys[it]) !in stinkyKeysList }.map { possibleKeys[it] }
    val usedKey = env.generateValue(keyGenerator, null)
    val node = currentNode[usedKey]
    env.logMessage("Use command: ${StringHelper.toKeyNotation(usedKey)}. ${if (node is CommandNode) "Action: ${node.actionHolder.actionId}" else ""}")
    VimTestCase.typeText(listOf(usedKey), editor, editor.project)

    IdeEventQueue.getInstance().flushQueue()
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }
}

private val stinkyKeysList = arrayListOf("K", "u", "H", "<C-Y>",
  StringHelper.toKeyNotation(KeyStroke.getKeyStroke(KeyEvent.VK_UNDO, 0)), "L", "!", "<C-D>", "z", "<C-W>",
  "g", "<C-U>",

  // Temporally disabled due to issues in the platform
  "<C-V>", "<C-Q>"
)
