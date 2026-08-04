# infra

Terraform으로 AWS에 kiwobollae 백엔드를 배포하는 코드입니다.

## 구성

- **VPC**: public subnet 2개(AZ a/c), IGW, 라우팅 (NAT 없음, 비용 절약)
- **EC2** (`t3.small`, public subnet): 부팅 시 Docker + Nginx Proxy Manager + Redis 자동 기동. Elastic IP 고정.
- **EBS 볼륨(`aws_ebs_volume.npm_data`)**: NPM의 Let's Encrypt 인증서/프록시 설정을 EC2 인스턴스 수명과 분리해서 보관. 인스턴스가 재생성돼도 이 볼륨은 그대로 재부착됨.
- **RDS**: MySQL(`db.t3.micro`), EC2 보안그룹에서만 접근 가능
- **보안그룹**: 80/443(공개), 81(NPM 관리 UI, 관리자 IP만), 22(SSH, 관리자 IP만)
- **IAM Role**: SSM(원격 배포 명령용) + S3(업로드 버킷) 권한만

애플리케이션 컨테이너(스프링 백엔드)는 이 infra가 아니라 `.github/workflows/deploy-backend.yml`이 SSM으로 배포합니다.

## 사전 준비

1. `cp terraform.tfvars.example terraform.tfvars` 후 값 채우기
2. 민감값은 tfvars 대신 환경변수로 주입:
   ```bash
   export TF_VAR_db_password=...
   export TF_VAR_jwt_secret=...
   export TF_VAR_ghcr_token=...
   export TF_VAR_google_client_secret=...
   export TF_VAR_kakao_client_secret=...
   export TF_VAR_naver_client_secret=...
   ```

## 실행

```bash
cd infra
terraform init
terraform plan
terraform apply
```

`apply` 후 `terraform output ec2_public_ip`로 나온 Elastic IP를 도메인 A레코드에 연결하세요 (최초 1회만 필요 — 이후 인스턴스가 바뀌어도 이 IP는 유지됩니다).

## EC2 최초 설정 (수동, 1회)

1. `http://<ec2_public_ip>:81` 접속해서 Nginx Proxy Manager 관리자 계정 설정 (초기값 `admin@example.com`/`changeme`)
2. 프록시 호스트 등록: 도메인 `api.kiwor.site`, Forward Hostname `kiwobollae_backend_1`, Port `8080` (아직 컨테이너가 없어도 저장 가능)
3. 설정한 관리자 계정을 GitHub Secrets(`NPM_ADMIN_EMAIL`, `NPM_ADMIN_PASSWORD`)에도 등록

## user_data(환경변수) 변경 시 주의

`user_data_replace_on_change = true`라서, `db_password`/`jwt_secret`/OAuth 값 등을 바꾸고 `apply`하면 **EC2가 통째로 재생성**됩니다. NPM 데이터는 별도 EBS 볼륨에 있어 살아남지만, 재생성 자체는 몇 분간 서비스가 끊긴다는 뜻이니 트래픽이 적은 시간에 진행하세요.

## RDS 삭제(destroy)하려면

`deletion_protection = true`(기본값)라서 `terraform destroy`가 RDS 단계에서 실패합니다. 삭제하려면:
```bash
# terraform.tfvars에 db_deletion_protection = false 추가 후
terraform apply
terraform destroy
```

## 무중단 배포 흐름 (CI/CD)

1. GitHub Actions가 `main`의 `backend/**` push를 감지해 Docker 이미지를 빌드 → GHCR push
2. AWS SSM으로 EC2에 접속해 blue/green 컨테이너 중 비어있는 슬롯에 새 이미지 기동
3. `/actuator/health` 200 확인 후 Nginx Proxy Manager API로 업스트림을 새 컨테이너로 전환
4. 전환 성공 후에만 이전 컨테이너 종료 (전환 실패 시 새 컨테이너만 정리하고 기존 컨테이너 유지)
