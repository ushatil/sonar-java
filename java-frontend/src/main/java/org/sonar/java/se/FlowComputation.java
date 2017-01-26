/*
 * SonarQube Java
 * Copyright (C) 2012-2017 SonarSource SA
 * mailto:info AT sonarsource DOT com
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
package org.sonar.java.se;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.sonar.java.se.checks.SyntaxTreeNameFinder;
import org.sonar.java.se.constraint.Constraint;
import org.sonar.java.se.symbolicvalues.BinarySymbolicValue;
import org.sonar.java.se.symbolicvalues.SymbolicValue;
import org.sonar.plugins.java.api.JavaFileScannerContext;
import org.sonar.plugins.java.api.semantic.Symbol;
import org.sonar.plugins.java.api.semantic.Type;
import org.sonar.plugins.java.api.tree.ExpressionTree;
import org.sonar.plugins.java.api.tree.MethodInvocationTree;
import org.sonar.plugins.java.api.tree.Tree;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FlowComputation {

  private final Predicate<Constraint> addToFlow;
  private final Predicate<Constraint> terminateTraversal;
  private final List<JavaFileScannerContext.Location> flow = new ArrayList<>();
  private final SymbolicValue symbolicValue;
  private final Set<ExplodedGraph.Node> visited = new HashSet<>();

  private FlowComputation(@Nullable SymbolicValue symbolicValue, Predicate<Constraint> addToFlow, Predicate<Constraint> terminateTraversal) {
    this.addToFlow = addToFlow;
    this.terminateTraversal = terminateTraversal;
    this.symbolicValue = symbolicValue;
  }

  public static List<JavaFileScannerContext.Location> flow(ExplodedGraph.Node currentNode, @Nullable SymbolicValue currentVal) {
    return flow(currentNode, currentVal, constraint -> true);
  }

  public static List<JavaFileScannerContext.Location> flow(ExplodedGraph.Node currentNode, @Nullable SymbolicValue currentVal, Predicate<Constraint> addToFlow) {
    return flow(currentNode, currentVal, addToFlow, c -> false);
  }

  public static List<JavaFileScannerContext.Location> flow(ExplodedGraph.Node currentNode, @Nullable SymbolicValue currentVal, Predicate<Constraint> addToFlow,
    Predicate<Constraint> terminateTraversal) {
    FlowComputation flowComputation = new FlowComputation(currentVal, addToFlow, terminateTraversal);

    Symbol trackSymbol = currentNode.programState.getLastEvaluated();
    if (currentVal instanceof BinarySymbolicValue) {
      Set<JavaFileScannerContext.Location> binSVFlow = flowComputation.flowFromBinarySV(currentNode, (BinarySymbolicValue) currentVal, trackSymbol);
      flowComputation.flow.addAll(binSVFlow);
    }
    flowComputation.run(currentNode, trackSymbol);
    return flowComputation.flow;
  }

  private Set<JavaFileScannerContext.Location> flowFromBinarySV(ExplodedGraph.Node currentNode, BinarySymbolicValue binarySV, Symbol trackSymbol) {
    Set<JavaFileScannerContext.Location> binSVFlow = new LinkedHashSet<>();
    FlowComputation left = fork(binarySV.getLeftOp());
    left.run(currentNode.parent(), trackSymbol);
    binSVFlow.addAll(left.flow);
    FlowComputation right = fork(binarySV.getRightOp());
    right.run(currentNode.parent(), trackSymbol);
    binSVFlow.addAll(right.flow);
    return binSVFlow;
  }

  private FlowComputation fork(SymbolicValue symbolicValue) {
    return new FlowComputation(symbolicValue, addToFlow, terminateTraversal);
  }

  private void run(@Nullable final ExplodedGraph.Node currentNode, @Nullable final Symbol trackSymbol) {
    if (currentNode == null || visited.contains(currentNode)) {
      return;
    }
    visited.add(currentNode);

    Symbol newTrackSymbol = trackSymbol;
    if (currentNode.programPoint.syntaxTree() != null) {
      newTrackSymbol = addFlowFromLearnedSymbols(currentNode, trackSymbol);
      List<Constraint> learnedConstraints = addFlowFromLearnedConstraints(currentNode);
      if (learnedConstraints.stream().anyMatch(terminateTraversal)) {
        return;
      }
    }
    for (ExplodedGraph.Node parent : currentNode.getParents()) {
      run(parent, newTrackSymbol);
    }
  }

  private List<Constraint> addFlowFromLearnedConstraints(ExplodedGraph.Node currentNode) {
    List<Constraint> learnedConstraints = currentNode.getLearnedConstraints().stream()
      .filter(lc -> lc.getSv().equals(symbolicValue))
      .map(ExplodedGraph.Node.LearnedConstraint::getConstraint)
      .collect(Collectors.toList());

    final ExplodedGraph.Node parent = currentNode.parent();
    learnedConstraints.stream()
      .filter(addToFlow.and(Objects::nonNull))
      .flatMap(lc -> learnedConstraintFlow(lc, currentNode, parent))
      .forEach(flow::add);

    return learnedConstraints;
  }

  private static Stream<JavaFileScannerContext.Location> learnedConstraintFlow(Constraint learnedConstraint, ExplodedGraph.Node currentNode, ExplodedGraph.Node parent) {
    Tree nodeTree = parent.programPoint.syntaxTree();
    if (nodeTree.is(Tree.Kind.METHOD_INVOCATION)) {
      return methodInvocationFlow(learnedConstraint, currentNode, parent);
    }
    if (nodeTree.is(Tree.Kind.NEW_CLASS)) {
      return Stream.of(location(parent, String.format("Constructor call creates '%s'", learnedConstraint.valueAsString())));
    }
    String name = SyntaxTreeNameFinder.getName(nodeTree);
    String message = name == null ? learnedConstraint.valueAsString() : String.format("Implies '%s' is %s", name, learnedConstraint.valueAsString());
    return Stream.of(location(parent, message));
  }

  private static Stream<JavaFileScannerContext.Location> methodInvocationFlow(Constraint learnedConstraint, ExplodedGraph.Node currentNode, ExplodedGraph.Node parent) {
    MethodInvocationTree mit = (MethodInvocationTree) parent.programPoint.syntaxTree();
    MethodYield selectedMethodYield = currentNode.selectedMethodYield(parent);
    if (selectedMethodYield == null) {
      return Stream.of(location(parent, String.format("'%s' invoked", mit.symbol().name())));
    }
    Stream.Builder<JavaFileScannerContext.Location> flow = Stream.builder();
    if (selectedMethodYield.isExceptional()) {
      Type exceptionType = selectedMethodYield.exceptionType();
      String exception = exceptionType == null || exceptionType.isUnknown() ? "Exception" : exceptionType.name();
      flow.add(location(parent, String.format("'%s' thrown by '%s'", exception, mit.symbol().name())));
    }
    if (selectedMethodYield.resultConstraint == learnedConstraint) {
      flow.add(location(parent, String.format("'%s' returns %s", mit.symbol().name(), learnedConstraint.valueAsString())));
    }
    for (int i = 0; i < mit.arguments().size(); i++) {
      ExpressionTree arg = mit.arguments().get(i);
      Constraint constraint = selectedMethodYield.parametersConstraints[i];
      if (constraint == learnedConstraint) {
        flow.accept(new JavaFileScannerContext.Location(String.format("Implies arg #%d is %s", i+1, learnedConstraint.valueAsString()), arg));
        selectedMethodYield.flow(i).forEach(flow::add);
      }
    }
    return flow.build();
  }

  @Nullable
  private Symbol addFlowFromLearnedSymbols(ExplodedGraph.Node currentNode, @Nullable Symbol trackSymbol) {
    ExplodedGraph.Node parent = currentNode.parent();
    if (trackSymbol == null || parent == null) {
      return null;
    }
    Optional<ExplodedGraph.Node.LearnedValue> learnedValue = currentNode.getLearnedSymbols().stream()
      .filter(lv -> lv.getSymbol().equals(trackSymbol))
      .findFirst();
    if (learnedValue.isPresent()) {
      ExplodedGraph.Node.LearnedValue lv = learnedValue.get();
      Constraint constraint = parent.programState.getConstraint(lv.getSv());
      JavaFileScannerContext.Location location = constraint == null ? location(parent) :
        location(parent, String.format("'%s' is assigned %s", lv.getSymbol().name(), constraint.valueAsString()));
      flow.add(location);
      return parent.programState.getLastEvaluated();
    }
    return trackSymbol;
  }

  private static JavaFileScannerContext.Location location(ExplodedGraph.Node node) {
    return location(node, "...");
  }

  private static JavaFileScannerContext.Location location(ExplodedGraph.Node node, String message) {
    return new JavaFileScannerContext.Location(message, node.programPoint.syntaxTree());
  }

  public static Set<List<JavaFileScannerContext.Location>> singleton(String msg, Tree tree) {
    return ImmutableSet.of(ImmutableList.of(new JavaFileScannerContext.Location(msg, tree)));
  }
}
