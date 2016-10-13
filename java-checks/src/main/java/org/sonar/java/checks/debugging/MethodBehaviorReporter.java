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
import org.sonar.java.se.constraint.Constraint;
import org.sonar.plugins.java.api.IssuableSubscriptionVisitor;
import org.sonar.plugins.java.api.semantic.Symbol;
import org.sonar.plugins.java.api.tree.MemberSelectExpressionTree;
import org.sonar.plugins.java.api.tree.MethodInvocationTree;
import org.sonar.plugins.java.api.tree.MethodTree;
import org.sonar.plugins.java.api.tree.Tree;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    if (methodSymbol != null && methodSymbol.isMethodSymbol()) {
      reportYields(reportTree, behaviors.get(methodSymbol));
    }
  }

  private void reportYields(Tree reportTree, MethodBehavior mb) {
    if (mb != null && !mb.yields().isEmpty()) {
      List<MethodYield> yields = mb.yields();
      List<MethodYield> happyPath = yields.stream().filter(y -> !y.isExceptional()).collect(Collectors.toList());
      List<MethodYield> exceptionalPath = yields.stream().filter(MethodYield::isExceptional).collect(Collectors.toList());
      StringBuilder sb = new StringBuilder()
        .append("Happy Path (")
        .append(happyPath.size())
        .append("): [" + prettyPrint(happyPath) + "]")
        .append(", Exceptional path (")
        .append(exceptionalPath.size())
        .append("): [" + prettyPrint(exceptionalPath) + "]");
      reportIssue(reportTree, sb.toString());
    }
  }

  private static String prettyPrint(List<MethodYield> yields) {
    return StringUtils.join(yields.stream().map(MethodBehaviorReporter::prettyPrint).toArray(), ",");
  }

  private static String prettyPrint(MethodYield yield) {
    String result = "{params: [";
    result += StringUtils.join(Stream.of(yield.parameterConstraints()).map(c -> c == null ? "NO_CONSTRAINT" : c.toString()).toArray(), ",");
    result += "], result: ";
    Constraint resultConstraint = yield.resultConstraint();
    result += resultConstraint == null ? "NO_CONSTRAINT" : resultConstraint.toString();
    int resultIndex = yield.resultIndex();
    if (resultIndex >= 0) {
      result += " (arg" + resultIndex + ")";
    }
    return result + "}";
  }

}
