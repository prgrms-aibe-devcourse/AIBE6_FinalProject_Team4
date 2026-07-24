package com.kiwobollae.api.global.common;

/**
 * API 버전 프리픽스 상수.
 * 컨트롤러의 @RequestMapping 에서 문자열 "/api/v1" 을 직접 하드코딩하지 않고
 * 이 상수를 참조하도록 해서, 이후 v2가 추가되거나 v1 경로 규칙이 바뀔 때
 * 한 곳만 수정하면 되도록 한다.
 *
 * 새 버전을 추가할 때는:
 *   1) 여기에 V2 = "/api/v2" 상수 추가
 *   2) v2로 갈 컨트롤러만 새 상수를 참조하도록 변경 (v1 컨트롤러는 그대로 유지)
 *   3) 완전히 다른 응답 형태가 필요하면 컨트롤러를 v1/v2 하위 패키지로 분리하는 것도 고려
 */
public final class ApiVersion {

	public static final String V1 = "/api/v1";

	private ApiVersion() {
	}
}
