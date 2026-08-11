package com.kiwobollae.api.timelapse.service;

// previousVideoUrl: 재요청 시점에 PlantTimelapse.restart()가 videoUrl을 즉시 null로 지워버리기
// 때문에, 워커가 나중에 같은 행에서 "이전 영상이 뭐였는지"를 다시 읽을 방법이 없다 — restart 직전
// 값을 여기 실어서 워커까지 전달한다. 기존 영상이 없었으면 null.
public record PlantTimelapseRequestedEvent(Long profileId, String previousVideoUrl) {}
