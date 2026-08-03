variable "region" {
  default = "ap-northeast-2"
}

variable "prefix" {
  default = "kiwobollae"
}

variable "instance_type" {
  default = "t3.small"
}

variable "app_domain" {
  description = "백엔드 API 도메인 (Nginx Proxy Manager에 등록할 도메인)"
  type        = string
}

variable "db_name" {
  default = "kiwobollae"
}

variable "db_username" {
  default = "kiwobollae_app"
}

variable "db_instance_class" {
  default = "db.t3.micro"
}

variable "db_deletion_protection" {
  description = "true면 terraform destroy가 RDS 삭제 단계에서 실패함. destroy 전에 false로 바꿔 apply 한 번 실행 후 진행할 것"
  default     = true
}

variable "s3_bucket" {
  default = "4team-storage-495264909330-ap-northeast-2-an"
}

variable "ssh_allowed_cidr" {
  description = "SSH를 허용할 관리자 IP (x.x.x.x/32). 운영에서는 0.0.0.0/0 금지"
  type        = string
}

variable "cors_allowed_origins" {
  default = "https://kiwor.site,https://www.kiwor.site"
}

variable "google_client_id" {
  default = ""
}
variable "google_redirect_uri" {
  default = "https://kiwor.site/oauth/callback/google"
}
variable "kakao_client_id" {
  default = ""
}
variable "kakao_redirect_uri" {
  default = "https://kiwor.site/oauth/callback/kakao"
}
variable "naver_client_id" {
  default = ""
}
variable "naver_redirect_uri" {
  default = "https://kiwor.site/oauth/callback/naver"
}
