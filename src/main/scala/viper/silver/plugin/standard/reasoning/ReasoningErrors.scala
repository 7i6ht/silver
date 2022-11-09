// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at http://mozilla.org/MPL/2.0/.
//
// Copyright (c) 2011-2022 ETH Zurich.

package viper.silver.plugin.standard.reasoning

import viper.silver.verifier._
import viper.silver.verifier.reasons.ErrorNode

case class ExistentialElimFailed(override val offendingNode: ErrorNode, override val reason: ErrorReason, override val cached: Boolean = false) extends AbstractVerificationError {
  override val id = "existential elimination.failed"
  override val text = " no witness could be found."

  override def withNode(offendingNode: errors.ErrorNode = this.offendingNode): ExistentialElimFailed =
    ExistentialElimFailed(this.offendingNode, this.reason, this.cached)

  override def withReason(r: ErrorReason): ExistentialElimFailed = ExistentialElimFailed(offendingNode, r, cached)
}
