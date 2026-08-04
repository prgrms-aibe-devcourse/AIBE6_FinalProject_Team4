# 민감값은 여기 기본값을 채우지 말고 terraform.tfvars(gitignore 대상) 또는
# TF_VAR_* 환경변수로 주입하세요.

variable "db_password" {
  type      = string
  sensitive = true
}

variable "jwt_secret" {
  type      = string
  sensitive = true
}

variable "ghcr_owner" {
  description = "GHCR 로그인용 GitHub 계정(소문자)"
  type        = string
}

variable "ghcr_token" {
  description = "GHCR 로그인용 GitHub 토큰 (read:packages 권한만, 만료기간 짧게)"
  type        = string
  sensitive   = true
}

variable "google_client_secret" {
  type      = string
  sensitive = true
  default   = ""
}

variable "kakao_client_secret" {
  type      = string
  sensitive = true
  default   = ""
}

variable "naver_client_secret" {
  type      = string
  sensitive = true
  default   = ""
}

variable "mail_password" {
  type      = string
  sensitive = true
  default   = ""
}

# TossPaymentProvider가 기동 시점에 "test_"로 시작하는 값인지 검증한다 — 운영 결제
# 연동 전까지는 반드시 Toss 테스트 시크릿 키(test_sk_...)를 넣을 것. default 없이
# 강제해 배포 때 빠뜨리면 terraform plan 단계에서부터 드러나게 한다.
variable "toss_payments_secret_key" {
  type      = string
  sensitive = true
}
