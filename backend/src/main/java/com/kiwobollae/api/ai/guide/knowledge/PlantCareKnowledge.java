package com.kiwobollae.api.ai.guide.knowledge;

import java.util.List;

/** 선택한 식물 종에 정확히 결속된 재배 근거 묶음. */
public record PlantCareKnowledge(List<PlantCareEvidence> evidence) {

  public PlantCareKnowledge {
    evidence = List.copyOf(evidence);
  }

  public boolean isEmpty() {
    return evidence.isEmpty();
  }

  public String promptContext() {
    StringBuilder context = new StringBuilder();
    for (int i = 0; i < evidence.size(); i++) {
      PlantCareEvidence item = evidence.get(i);
      context
          .append("[근거 ")
          .append(i + 1)
          .append("] 출처: ")
          .append(item.sourceName())
          .append(" | URL: ")
          .append(item.sourceUrl())
          .append(" | 버전: ")
          .append(item.version())
          .append('\n')
          .append(item.content())
          .append("\n\n");
    }
    return context.toString();
  }

  public String fingerprintMaterial() {
    StringBuilder material = new StringBuilder();
    for (PlantCareEvidence item : evidence) {
      String itemMaterial = item.fingerprintMaterial();
      material.append(itemMaterial.length()).append('\n');
      material.append(itemMaterial).append('\n');
    }
    return material.toString();
  }
}
