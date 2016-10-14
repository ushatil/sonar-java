/*
 * SonarQube Java
 * Copyright (C) 2012-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.java.checks.debugging;

import com.google.common.collect.ImmutableList;

import org.sonar.check.Rule;
import org.sonar.java.se.DebuggingVisitor;
import org.sonar.java.se.MethodBehavior;
import org.sonar.plugins.java.api.IssuableSubscriptionVisitor;
import org.sonar.plugins.java.api.semantic.Symbol;
import org.sonar.plugins.java.api.tree.MethodTree;
import org.sonar.plugins.java.api.tree.Tree;

import javax.annotation.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Rule(key = "debugging-SE-InterruptedSymbolicExecution")
public class InterruptedSymbolicExecutionReporter extends IssuableSubscriptionVisitor implements DebuggingVisitor {

  Map<Symbol.MethodSymbol, Exception> interrupted;

  @Override
  public void setDebuggingData(Map<Symbol.MethodSymbol, MethodBehavior> behaviors, Map<Symbol.MethodSymbol, Exception> interrupted) {
    this.interrupted = new HashMap<>(interrupted);
  }

  @Override
  public List<Tree.Kind> nodesToVisit() {
    return ImmutableList.of(Tree.Kind.METHOD);
  }

  @Override
  public void visitNode(Tree tree) {
    MethodTree methodTree = (MethodTree) tree;
    Symbol methodSymbol = methodTree.symbol();

    if (methodSymbol != null && methodSymbol.isMethodSymbol()) {
      reportInterruptedException(methodTree.simpleName(), methodSymbol);
    }
  }

  private void reportInterruptedException(Tree reportTree, @Nullable Symbol methodSymbol) {
    Exception e = interrupted.get(methodSymbol);
    if (e != null) {
      reportIssue(reportTree, "SE Interrupted: " + e.getMessage());
    }
  }

}
