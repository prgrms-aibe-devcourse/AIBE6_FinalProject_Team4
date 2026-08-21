package com.kiwobollae.api.ai.knowledge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 저장소에 검토·반입한 공식 문서 코퍼스의 classpath 구현체다.
 *
 * <p>상위 서비스는 {@link PlantCareDocumentCorpus}에만 의존하므로 문서가 커지면 DB, 검색 엔진 또는 벡터 검색 adapter로 교체할 수 있다.
 * 이 구현은 정규화된 정식명·별칭 인덱스로 검색하며 다른 종의 유사 문서를 섞지 않는다.
 */
@Component
public class ClasspathOfficialPlantCareDocumentCorpus implements PlantCareDocumentCorpus {

  static final String DEFAULT_RESOURCE_PATH = "ai/plant-care/official-document-corpus.json";

  private final PlantSpeciesNameNormalizer speciesNameNormalizer;
  private final String corpusVersion;
  private final Map<String, SubjectMatch> subjectByLookupKey;
  private final Map<String, List<PlantCareEvidence>> evidenceBySubjectId;

  @Autowired
  public ClasspathOfficialPlantCareDocumentCorpus(
      ObjectMapper objectMapper, PlantSpeciesNameNormalizer speciesNameNormalizer) {
    this(objectMapper, speciesNameNormalizer, DEFAULT_RESOURCE_PATH);
  }

  ClasspathOfficialPlantCareDocumentCorpus(
      ObjectMapper objectMapper,
      PlantSpeciesNameNormalizer speciesNameNormalizer,
      String resourcePath) {
    this.speciesNameNormalizer = speciesNameNormalizer;
    LoadedCorpus loaded = load(objectMapper, resourcePath);
    this.corpusVersion = loaded.corpusVersion();
    this.subjectByLookupKey = loaded.subjectByLookupKey();
    this.evidenceBySubjectId = loaded.evidenceBySubjectId();
  }

  @Override
  public PlantCareDocumentSearchResult search(PlantCareDocumentQuery query) {
    if (query == null
        || query.requestedSpeciesName() == null
        || query.requestedSpeciesName().isBlank()
        || query.speciesLookupKey() == null
        || query.speciesLookupKey().isBlank()) {
      throw new IllegalArgumentException("공식 문서 검색 종명이 필요합니다.");
    }

    SubjectMatch match = subjectByLookupKey.get(query.speciesLookupKey());
    if (match == null) {
      return new PlantCareDocumentSearchResult(
          query.requestedSpeciesName(), PlantCareSpeciesMatchType.NONE, corpusVersion, List.of());
    }
    return new PlantCareDocumentSearchResult(
        match.subject().normalizedCanonicalName(),
        match.matchType(),
        corpusVersion,
        evidenceBySubjectId.getOrDefault(match.subject().subjectId(), List.of()));
  }

  private LoadedCorpus load(ObjectMapper objectMapper, String resourcePath) {
    try (InputStream inputStream = new ClassPathResource(resourcePath).getInputStream()) {
      String json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
      CorpusFile corpus = objectMapper.readValue(json, CorpusFile.class);
      return validateAndIndex(corpus);
    } catch (IOException | JacksonException exception) {
      throw new IllegalStateException("공식 재배 문서 코퍼스를 읽을 수 없습니다.", exception);
    }
  }

  private LoadedCorpus validateAndIndex(CorpusFile corpus) {
    if (corpus == null
        || blank(corpus.corpusVersion())
        || corpus.subjects() == null
        || corpus.subjects().isEmpty()
        || corpus.documents() == null) {
      throw new IllegalStateException("공식 재배 문서 코퍼스 구조가 올바르지 않습니다.");
    }

    Map<String, IndexedSubject> subjectById = new LinkedHashMap<>();
    Map<String, SubjectMatch> lookupIndex = new HashMap<>();
    for (CorpusSubject subject : corpus.subjects()) {
      IndexedSubject indexedSubject = indexSubject(subject);
      if (subjectById.putIfAbsent(indexedSubject.subjectId(), indexedSubject) != null) {
        throw new IllegalStateException("공식 재배 문서 subjectId가 중복되었습니다: " + subject.subjectId());
      }
      putLookupKey(
          lookupIndex,
          indexedSubject.canonicalLookupKey(),
          new SubjectMatch(indexedSubject, PlantCareSpeciesMatchType.CANONICAL_NAME));
      for (String aliasLookupKey : indexedSubject.aliasLookupKeys()) {
        putLookupKey(
            lookupIndex,
            aliasLookupKey,
            new SubjectMatch(indexedSubject, PlantCareSpeciesMatchType.ALIAS));
      }
      for (String cultivarLookupKey : indexedSubject.cultivarLookupKeys()) {
        putLookupKey(
            lookupIndex,
            cultivarLookupKey,
            new SubjectMatch(indexedSubject, PlantCareSpeciesMatchType.CULTIVAR));
      }
    }

    Map<String, List<PlantCareEvidence>> evidenceBySubject = new HashMap<>();
    Set<String> sourceIds = new HashSet<>();
    for (CorpusDocument document : corpus.documents()) {
      if (!sourceIds.add(document.sourceId())) {
        throw new IllegalStateException("공식 재배 문서 sourceId가 중복되었습니다: " + document.sourceId());
      }
      if (document.subjectIds() == null || document.subjectIds().isEmpty()) {
        throw new IllegalStateException("공식 재배 문서에 대상 subjectId가 필요합니다.");
      }
      PlantCareEvidence evidence =
          new PlantCareEvidence(
              document.sourceId(),
              document.sourceName(),
              document.sourceUrl(),
              document.version(),
              document.content());
      for (String subjectId : document.subjectIds()) {
        if (!subjectById.containsKey(subjectId)) {
          throw new IllegalStateException("공식 재배 문서가 알 수 없는 subjectId를 참조합니다: " + subjectId);
        }
        evidenceBySubject.computeIfAbsent(subjectId, ignored -> new ArrayList<>()).add(evidence);
      }
    }

    return new LoadedCorpus(
        corpus.corpusVersion(),
        Map.copyOf(lookupIndex),
        evidenceBySubject.entrySet().stream()
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    Map.Entry::getKey,
                    entry ->
                        entry.getValue().stream()
                            .sorted(java.util.Comparator.comparing(PlantCareEvidence::sourceId))
                            .toList())));
  }

  private IndexedSubject indexSubject(CorpusSubject subject) {
    if (subject == null || blank(subject.subjectId()) || blank(subject.canonicalName())) {
      throw new IllegalStateException("공식 재배 문서 subject의 ID와 정식명이 필요합니다.");
    }
    PlantSpeciesNameNormalizer.NormalizedSpeciesName canonical =
        speciesNameNormalizer.normalize(subject.canonicalName());
    List<String> aliases = subject.aliases() == null ? List.of() : subject.aliases();
    Set<String> aliasLookupKeys = new HashSet<>();
    for (String alias : aliases) {
      String aliasLookupKey = speciesNameNormalizer.normalize(alias).lookupKey();
      if (!aliasLookupKey.equals(canonical.lookupKey())) {
        aliasLookupKeys.add(aliasLookupKey);
      }
    }
    Set<String> cultivarLookupKeys = new HashSet<>();
    List<CorpusCultivar> cultivars = subject.cultivars() == null ? List.of() : subject.cultivars();
    for (CorpusCultivar cultivar : cultivars) {
      validateCultivar(cultivar);
      cultivarLookupKeys.add(speciesNameNormalizer.normalize(cultivar.canonicalName()).lookupKey());
      List<String> cultivarAliases = cultivar.aliases() == null ? List.of() : cultivar.aliases();
      for (String cultivarAlias : cultivarAliases) {
        cultivarLookupKeys.add(speciesNameNormalizer.normalize(cultivarAlias).lookupKey());
      }
    }
    cultivarLookupKeys.remove(canonical.lookupKey());
    cultivarLookupKeys.removeAll(aliasLookupKeys);
    return new IndexedSubject(
        subject.subjectId(),
        canonical.cacheName(),
        canonical.lookupKey(),
        Set.copyOf(aliasLookupKeys),
        Set.copyOf(cultivarLookupKeys));
  }

  private void validateCultivar(CorpusCultivar cultivar) {
    if (cultivar == null
        || blank(cultivar.canonicalName())
        || blank(cultivar.authoritySourceName())
        || blank(cultivar.authoritySourceUrl())
        || !cultivar.authoritySourceUrl().startsWith("https://")
        || blank(cultivar.authorityVersion())) {
      throw new IllegalStateException("품종명에는 검증 가능한 공식 분류 근거가 필요합니다.");
    }
  }

  private void putLookupKey(
      Map<String, SubjectMatch> lookupIndex, String lookupKey, SubjectMatch candidate) {
    SubjectMatch existing = lookupIndex.putIfAbsent(lookupKey, candidate);
    if (existing == null
        || existing.subject().subjectId().equals(candidate.subject().subjectId())) {
      return;
    }
    throw new IllegalStateException(
        "서로 다른 식물의 정식명·별칭이 충돌합니다: "
            + existing.subject().subjectId()
            + ", "
            + candidate.subject().subjectId());
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  public record CorpusFile(
      String corpusVersion, List<CorpusSubject> subjects, List<CorpusDocument> documents) {}

  public record CorpusSubject(
      String subjectId,
      String canonicalName,
      List<String> aliases,
      List<CorpusCultivar> cultivars) {}

  public record CorpusCultivar(
      String canonicalName,
      List<String> aliases,
      String authoritySourceName,
      String authoritySourceUrl,
      String authorityVersion) {}

  public record CorpusDocument(
      List<String> subjectIds,
      String sourceId,
      String sourceName,
      String sourceUrl,
      String version,
      String content) {}

  private record IndexedSubject(
      String subjectId,
      String normalizedCanonicalName,
      String canonicalLookupKey,
      Set<String> aliasLookupKeys,
      Set<String> cultivarLookupKeys) {}

  private record SubjectMatch(IndexedSubject subject, PlantCareSpeciesMatchType matchType) {}

  private record LoadedCorpus(
      String corpusVersion,
      Map<String, SubjectMatch> subjectByLookupKey,
      Map<String, List<PlantCareEvidence>> evidenceBySubjectId) {}
}
