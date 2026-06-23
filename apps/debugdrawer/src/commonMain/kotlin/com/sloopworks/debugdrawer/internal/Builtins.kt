package com.sloopworks.debugdrawer.internal

import com.sloopworks.debugdrawer.DebugDrawerConfig
import com.sloopworks.debugdrawer.DebugPlugin
import com.sloopworks.debugdrawer.panels.AppInfoPlugin

/**
 * Built-in panels prepended before the consumer's plugins when
 * [DebugDrawerConfig.includeBuiltins] is true. Backend-switch and Logs join here
 * as their plans land.
 */
internal fun builtinPlugins(config: DebugDrawerConfig): List<DebugPlugin> =
  if (!config.includeBuiltins) emptyList()
  else listOf(
    AppInfoPlugin(config.buildInfo),
  )
