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

import org.apache.commons.lang.StringUtils;
import org.sonar.check.Rule;
import org.sonar.java.se.DebuggingVisitor;
import org.sonar.java.se.MethodBehavior;
import org.sonar.java.se.MethodYield;
import org.sonar.plugins.java.api.IssuableSubscriptionVisitor;
import org.sonar.plugins.java.api.semantic.Symbol;
import org.sonar.plugins.java.api.tree.MemberSelectExpressionTree;
import org.sonar.plugins.java.api.tree.MethodInvocationTree;
import org.sonar.plugins.java.api.tree.MethodTree;
import org.sonar.plugins.java.api.tree.Tree;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Rule(key = "debugging-SE-MethodBehaviors")
public class MethodBehaviorReporter extends IssuableSubscriptionVisitor implements DebuggingVisitor {

  Map<Symbol.MethodSymbol, MethodBehavior> behaviors;

  @Override
  public void setMethodBehaviors(Map<Symbol.MethodSymbol, MethodBehavior> behaviors) {
    this.behaviors = new HashMap<>(behaviors);
  }

  @Override
  public List<Tree.Kind> nodesToVisit() {
    return ImmutableList.of(Tree.Kind.METHOD, Tree.Kind.METHOD_INVOCATION);
  }

  @Override
  public void visitNode(Tree tree) {
    Symbol methodSymbol;
    Tree reportTree;
    if (tree.is(Tree.Kind.METHOD)) {
      MethodTree methodTree = (MethodTree) tree;
      methodSymbol = methodTree.symbol();
      reportTree = methodTree.simpleName();
    } else {
      MethodInvocationTree mit = (MethodInvocationTree) tree;
      methodSymbol = mit.symbol();
      reportTree = mit.methodSelect();
      if (reportTree.is(Tree.Kind.MEMBER_SELECT)) {
        reportTree = ((MemberSelectExpressionTree) reportTree).identifier();
      }
    }

    if (methodSymbol.isMethodSymbol()) {
      reportYields(reportTree, behaviors.get(methodSymbol));
    }
  }

  private void reportYields(Tree reportTree, MethodBehavior mb) {
    String result = mb != null ? StringUtils.join(mb.yields().stream().map(MethodYield::toString).toArray(), ",") : "";
    if (result.length() == 0) {
      result = "No known yield";
    }
    reportIssue(reportTree, "[" + result + "]");
  }

}
