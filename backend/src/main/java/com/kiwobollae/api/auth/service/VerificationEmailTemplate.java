package com.kiwobollae.api.auth.service;

/**
 * Renders the signup verification-code email as inline-styled HTML (email
 * clients largely ignore &lt;style&gt; blocks/external CSS, so every rule
 * here is inline on purpose). Colors mirror the frontend's Tailwind palette
 * (see frontend/tailwind.config.js): brand #7CB342/#558B2F, gold #FFD54F,
 * ink #3E4A3D, sub #8a9587, paper #FDFBF4, line #e6eadd.
 */
final class VerificationEmailTemplate {

	private VerificationEmailTemplate() {
	}

	static String renderSignup(String code, int expirationMinutes) {
		return render("이메일 인증코드예요", "아래 코드를 회원가입 화면에 입력해 주세요.", code, expirationMinutes);
	}

	static String renderPasswordReset(String code, int expirationMinutes) {
		return render("비밀번호 재설정 인증코드예요", "아래 코드를 비밀번호 재설정 화면에 입력해 주세요.", code, expirationMinutes);
	}

	private static String render(String title, String subtitle, String code, int expirationMinutes) {
		return """
				<!DOCTYPE html>
				<html lang="ko">
				<body style="margin:0;padding:0;background-color:#FDFBF4;font-family:'Apple SD Gothic Neo','Malgun Gothic',sans-serif;">
					<table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color:#FDFBF4;padding:32px 16px;">
						<tr>
							<td align="center">
								<table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:480px;background-color:#ffffff;border-radius:20px;box-shadow:0 4px 20px rgba(124,179,66,.12);overflow:hidden;">
									<tr>
										<td style="padding:32px 32px 8px 32px;text-align:center;">
											<div style="font-size:22px;font-weight:800;color:#558B2F;">키워볼래 🌱</div>
										</td>
									</tr>
									<tr>
										<td style="padding:8px 32px 0 32px;text-align:center;">
											<p style="margin:0 0 6px 0;font-size:18px;font-weight:800;color:#3E4A3D;">%s</p>
											<p style="margin:0;font-size:14px;color:#8a9587;">%s</p>
										</td>
									</tr>
									<tr>
										<td style="padding:24px 32px;">
											<div style="background-color:#FFF6D6;border-radius:14px;padding:20px;text-align:center;">
												<span style="font-size:32px;font-weight:800;letter-spacing:8px;color:#8a6d00;font-family:'Courier New',monospace;">%s</span>
											</div>
										</td>
									</tr>
									<tr>
										<td style="padding:0 32px 32px 32px;text-align:center;">
											<p style="margin:0;font-size:13px;color:#8a9587;">이 코드는 <b style="color:#3E4A3D;">%d분</b> 동안만 유효해요.</p>
											<p style="margin:16px 0 0 0;font-size:12px;color:#b0b8a6;">본인이 요청하지 않았다면 이 메일은 무시해 주세요.</p>
										</td>
									</tr>
									<tr>
										<td style="padding:16px 32px;background-color:#EEF3E4;text-align:center;">
											<p style="margin:0;font-size:12px;color:#4b7a1e;">© 키워볼래 · 식물을 키우고, 기록하고, 진짜 열매를 받아보세요.</p>
										</td>
									</tr>
								</table>
							</td>
						</tr>
					</table>
				</body>
				</html>
				""".formatted(title, subtitle, code, expirationMinutes);
	}
}
