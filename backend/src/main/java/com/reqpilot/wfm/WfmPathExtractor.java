package com.reqpilot.wfm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class WfmPathExtractor {

  private static final int DEFAULT_MAX_DEPTH = 20;

  public List<WfmPath> extract(WfmDocument wfm) {
    return extract(wfm, DEFAULT_MAX_DEPTH);
  }

  public List<WfmPath> extract(WfmDocument wfm, int maxDepth) {
    if (wfm == null || wfm.ast() == null || wfm.ast().nodes().isEmpty()) {
      return List.of();
    }

    Map<String, WfmNode> nodesById = new LinkedHashMap<>();
    for (WfmNode node : wfm.ast().nodes()) {
      nodesById.put(node.id(), node);
    }

    WfmNode start =
        wfm.ast().nodes().stream()
            .filter((node) -> node.role() == WfmNodeRole.START)
            .findFirst()
            .orElse(wfm.ast().nodes().getFirst());

    Map<String, List<WfmTransition>> outgoing = new HashMap<>();
    for (WfmTransition transition : wfm.ast().transitions()) {
      outgoing.computeIfAbsent(transition.from(), ignored -> new ArrayList<>()).add(transition);
    }
    outgoing.values().forEach((transitions) -> transitions.sort(Comparator.comparing(WfmTransition::id)));

    List<RawPath> rawPaths = new ArrayList<>();
    List<WfmNode> nodePath = new ArrayList<>();
    List<WfmTransition> transitionPath = new ArrayList<>();
    Map<String, Integer> transitionUseCounts = new HashMap<>();
    Set<String> nodeIdsInPath = new HashSet<>();

    nodePath.add(start);
    nodeIdsInPath.add(start.id());
    walk(
        start,
        nodesById,
        outgoing,
        nodeIdsInPath,
        transitionUseCounts,
        nodePath,
        transitionPath,
        rawPaths,
        Math.max(1, maxDepth),
        false);

    if (rawPaths.isEmpty()) {
      rawPaths.add(new RawPath(List.copyOf(nodePath), List.copyOf(transitionPath), false));
    }

    Map<String, WfmPath> uniquePaths = new LinkedHashMap<>();
    for (RawPath rawPath : rawPaths) {
      WfmPath path = classify(rawPath);
      uniquePaths.putIfAbsent(pathSignature(path), path);
    }
    return List.copyOf(uniquePaths.values());
  }

  private void walk(
      WfmNode current,
      Map<String, WfmNode> nodesById,
      Map<String, List<WfmTransition>> outgoing,
      Set<String> nodeIdsInPath,
      Map<String, Integer> transitionUseCounts,
      List<WfmNode> nodePath,
      List<WfmTransition> transitionPath,
      List<RawPath> paths,
      int maxDepth,
      boolean containsLoop) {
    if (nodePath.size() >= maxDepth || current.role() == WfmNodeRole.END) {
      paths.add(new RawPath(List.copyOf(nodePath), List.copyOf(transitionPath), containsLoop));
      return;
    }

    List<WfmTransition> nextTransitions = outgoing.getOrDefault(current.id(), List.of());
    if (nextTransitions.isEmpty()) {
      paths.add(new RawPath(List.copyOf(nodePath), List.copyOf(transitionPath), containsLoop));
      return;
    }

    for (WfmTransition transition : nextTransitions) {
      WfmNode nextNode = nodesById.get(transition.to());
      if (nextNode == null) {
        continue;
      }

      int useCount = transitionUseCounts.getOrDefault(transition.id(), 0);
      if (useCount > 0) {
        continue;
      }

      boolean isLoop = nodeIdsInPath.contains(nextNode.id());
      transitionUseCounts.put(transition.id(), useCount + 1);
      transitionPath.add(transition);
      nodePath.add(nextNode);

      if (isLoop) {
        paths.add(new RawPath(List.copyOf(nodePath), List.copyOf(transitionPath), true));
      } else {
        nodeIdsInPath.add(nextNode.id());
        walk(
            nextNode,
            nodesById,
            outgoing,
            nodeIdsInPath,
            transitionUseCounts,
            nodePath,
            transitionPath,
            paths,
            maxDepth,
            containsLoop);
        nodeIdsInPath.remove(nextNode.id());
      }

      nodePath.removeLast();
      transitionPath.removeLast();
      if (useCount == 0) {
        transitionUseCounts.remove(transition.id());
      } else {
        transitionUseCounts.put(transition.id(), useCount);
      }
    }
  }

  private WfmPath classify(RawPath rawPath) {
    List<WfmTransitionSemantic> semantics =
        rawPath.transitions().stream().map(WfmTransition::semantic).toList();
    WfmNode terminalNode = rawPath.nodes().isEmpty() ? null : rawPath.nodes().getLast();

    boolean retryPath = semantics.contains(WfmTransitionSemantic.RETRY);
    boolean cancelPath = semantics.contains(WfmTransitionSemantic.CANCEL);
    boolean timeoutPath = semantics.contains(WfmTransitionSemantic.TIMEOUT);
    boolean errorPath =
        timeoutPath
            || semantics.contains(WfmTransitionSemantic.FAILURE)
            || rawPath.nodes().stream().anyMatch((node) -> node.role() == WfmNodeRole.ERROR);
    boolean negativePath =
        cancelPath
            || errorPath
            || retryPath
            || semantics.contains(WfmTransitionSemantic.NO)
            || semantics.contains(WfmTransitionSemantic.FAILURE);
    boolean happyPath =
        !negativePath
            && !timeoutPath
            && (semantics.contains(WfmTransitionSemantic.SUCCESS)
                || semantics.contains(WfmTransitionSemantic.YES)
                || terminalNode != null && terminalNode.role() == WfmNodeRole.END);

    return new WfmPath(
        rawPath.nodes(),
        rawPath.transitions(),
        semanticSummary(retryPath, cancelPath, timeoutPath, errorPath, negativePath, happyPath),
        terminalNode,
        rawPath.containsLoop(),
        happyPath,
        negativePath,
        errorPath,
        cancelPath,
        retryPath,
        timeoutPath);
  }

  private String semanticSummary(
      boolean retryPath,
      boolean cancelPath,
      boolean timeoutPath,
      boolean errorPath,
      boolean negativePath,
      boolean happyPath) {
    List<String> parts = new ArrayList<>();
    if (happyPath) {
      parts.add("happy");
    }
    if (negativePath) {
      parts.add("negative");
    }
    if (errorPath) {
      parts.add("error");
    }
    if (cancelPath) {
      parts.add("cancel");
    }
    if (retryPath) {
      parts.add("retry");
    }
    if (timeoutPath) {
      parts.add("timeout");
    }
    return parts.isEmpty() ? "default" : String.join("/", parts);
  }

  private String pathSignature(WfmPath path) {
    if (!path.transitions().isEmpty()) {
      return path.transitions().stream().map(WfmTransition::id).reduce("", (left, right) -> left + "/" + right);
    }
    return path.nodes().stream().map(WfmNode::id).reduce("", (left, right) -> left + "/" + right);
  }

  private record RawPath(List<WfmNode> nodes, List<WfmTransition> transitions, boolean containsLoop) {}
}
