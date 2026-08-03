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
