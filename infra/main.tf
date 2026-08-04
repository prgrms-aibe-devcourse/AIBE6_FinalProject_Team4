terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
    }
  }
}

provider "aws" {
  region = var.region
}

# ---------------------------------------------------------
# 네트워크 (public subnet만 사용, NAT 없이 비용 최소화)
# ---------------------------------------------------------
resource "aws_vpc" "vpc_1" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = { Name = "${var.prefix}-vpc-1" }
}

resource "aws_subnet" "subnet_1" {
  vpc_id                  = aws_vpc.vpc_1.id
  cidr_block              = "10.0.0.0/24"
  availability_zone       = "${var.region}a"
  map_public_ip_on_launch = true

  tags = { Name = "${var.prefix}-subnet-1" }
}

# RDS는 최소 2개 AZ에 걸친 서브넷 그룹이 필요 (NAT 없이 비용 절약을 위해 public subnet 그대로 사용,
# publicly_accessible=false + 보안그룹으로 EC2에서만 접근 가능하게 제한)
resource "aws_subnet" "subnet_2" {
  vpc_id                  = aws_vpc.vpc_1.id
  cidr_block              = "10.0.1.0/24"
  availability_zone       = "${var.region}c"
  map_public_ip_on_launch = true

  tags = { Name = "${var.prefix}-subnet-2" }
}

resource "aws_internet_gateway" "igw_1" {
  vpc_id = aws_vpc.vpc_1.id
  tags   = { Name = "${var.prefix}-igw-1" }
}

resource "aws_route_table" "rt_1" {
  vpc_id = aws_vpc.vpc_1.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.igw_1.id
  }

  tags = { Name = "${var.prefix}-rt-1" }
}

resource "aws_route_table_association" "association_1" {
  subnet_id      = aws_subnet.subnet_1.id
  route_table_id = aws_route_table.rt_1.id
}

resource "aws_route_table_association" "association_2" {
  subnet_id      = aws_subnet.subnet_2.id
  route_table_id = aws_route_table.rt_1.id
}

# ---------------------------------------------------------
# 보안그룹: 80/443(nginx), 22(관리자 IP만), 컨테이너 간 통신은 도커 네트워크로 처리
# ---------------------------------------------------------
resource "aws_security_group" "sg_1" {
  name   = "${var.prefix}-sg-1"
  vpc_id = aws_vpc.vpc_1.id

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.ssh_allowed_cidr]
  }

  # Nginx Proxy Manager 관리자 UI. 관리자 IP만 허용 권장(지금은 ssh_allowed_cidr 값 재사용).
  ingress {
    from_port   = 81
    to_port     = 81
    protocol    = "tcp"
    cidr_blocks = [var.ssh_allowed_cidr]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.prefix}-sg-1" }
}

resource "aws_security_group" "rds_sg_1" {
  name   = "${var.prefix}-rds-sg-1"
  vpc_id = aws_vpc.vpc_1.id

  ingress {
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [aws_security_group.sg_1.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.prefix}-rds-sg-1" }
}

# ---------------------------------------------------------
# RDS MySQL (단일 AZ, 비용 절약)
# ---------------------------------------------------------
resource "aws_db_subnet_group" "this" {
  name       = "${var.prefix}-db-subnet-group"
  subnet_ids = [aws_subnet.subnet_1.id, aws_subnet.subnet_2.id]
  tags       = { Name = "${var.prefix}-db-subnet-group" }
}

resource "aws_db_instance" "this" {
  identifier                = "${var.prefix}-mysql"
  engine                    = "mysql"
  engine_version            = "8.0"
  instance_class            = var.db_instance_class
  allocated_storage         = 20
  storage_type              = "gp3"
  db_name                   = var.db_name
  username                  = var.db_username
  password                  = var.db_password
  db_subnet_group_name      = aws_db_subnet_group.this.name
  vpc_security_group_ids    = [aws_security_group.rds_sg_1.id]
  publicly_accessible       = false
  multi_az                  = false
  backup_retention_period   = 7
  skip_final_snapshot       = false
  final_snapshot_identifier = "${var.prefix}-mysql-final"
  deletion_protection       = var.db_deletion_protection

  tags = { Name = "${var.prefix}-mysql" }
}

# ---------------------------------------------------------
# EC2 역할: SSM(배포용 원격명령) + S3(이미지 업로드 버킷 접근)
# ---------------------------------------------------------
resource "aws_iam_role" "ec2_role_1" {
  name = "${var.prefix}-ec2-role-1"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  tags = { Name = "${var.prefix}-ec2-role-1" }
}

resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.ec2_role_1.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy" "s3_uploads" {
  name = "${var.prefix}-s3-uploads"
  role = aws_iam_role.ec2_role_1.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
      Resource = "arn:aws:s3:::${var.s3_bucket}/*"
    }]
  })
}

resource "aws_iam_instance_profile" "instance_profile_1" {
  name = "${var.prefix}-instance-profile-1"
  role = aws_iam_role.ec2_role_1.name
}

# ---------------------------------------------------------
# EC2: 부팅 시 docker + nginx-proxy-manager + redis 기동 (DB는 RDS 사용)
# 앱 컨테이너(blue/green)는 CI/CD가 SSM으로 배포
# ---------------------------------------------------------
data "aws_ami" "latest_amazon_linux" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-x86_64"]
  }
  filter {
    name   = "architecture"
    values = ["x86_64"]
  }
  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

locals {
  ec2_user_data = <<-END_OF_FILE
    #!/bin/bash
    timedatectl set-timezone Asia/Seoul

    # 필수 환경변수를 /etc/environment 에 심어서 배포 스크립트(SSM)에서 재사용.
    # 전부 따옴표로 감싼다 — DB_URL의 "&"처럼 셸 특수문자가 섞인 값을 이 파일이
    # "source"될 때(아래, 그리고 배포 스크립트에서) 그대로 실행돼버리는 걸 막기 위함.
    # 따옴표 없이 쓰면 "&" 뒤가 백그라운드 실행으로 잘려나가 값이 통째로 안 먹힌다.
    cat >> /etc/environment <<EOF
    DB_URL="jdbc:mysql://${aws_db_instance.this.address}:3306/${var.db_name}?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
    DB_USERNAME="${var.db_username}"
    DB_PASSWORD="${var.db_password}"
    APP_DOMAIN="${var.app_domain}"
    DB_NAME="${var.db_name}"
    JWT_SECRET="${var.jwt_secret}"
    CORS_ALLOWED_ORIGINS="${var.cors_allowed_origins}"
    GOOGLE_CLIENT_ID="${var.google_client_id}"
    GOOGLE_CLIENT_SECRET="${var.google_client_secret}"
    GOOGLE_REDIRECT_URI="${var.google_redirect_uri}"
    KAKAO_CLIENT_ID="${var.kakao_client_id}"
    KAKAO_CLIENT_SECRET="${var.kakao_client_secret}"
    KAKAO_REDIRECT_URI="${var.kakao_redirect_uri}"
    NAVER_CLIENT_ID="${var.naver_client_id}"
    NAVER_CLIENT_SECRET="${var.naver_client_secret}"
    NAVER_REDIRECT_URI="${var.naver_redirect_uri}"
    MAIL_ENABLED="${var.mail_enabled}"
    MAIL_FROM="${var.mail_from}"
    MAIL_HOST="${var.mail_host}"
    MAIL_PORT="${var.mail_port}"
    MAIL_USERNAME="${var.mail_username}"
    MAIL_PASSWORD="${var.mail_password}"
    TOSS_PAYMENTS_BASE_URL="${var.toss_payments_base_url}"
    TOSS_PAYMENTS_SECRET_KEY="${var.toss_payments_secret_key}"
    EOF
    source /etc/environment

    yum install -y docker jq
    systemctl enable --now docker
    docker network create common

    # NPM/Redis 데이터를 EC2 수명과 분리된 별도 EBS 볼륨(/dev/xvdf)에 저장.
    # 인스턴스가 재생성돼도(user_data_replace_on_change) 이 볼륨은 그대로 재부착되므로
    # Let's Encrypt 인증서/프록시 설정이 날아가지 않는다 (Let's Encrypt는 동일 도메인 인증서
    # 재발급을 주당 5회로 제한하므로, 재생성마다 인증서를 새로 받으면 금방 막힌다).
    DEVICE=/dev/xvdf
    if ! blkid "$DEVICE" >/dev/null 2>&1; then
      mkfs -t ext4 "$DEVICE"
    fi
    mkdir -p /dockerProjects
    mount "$DEVICE" /dockerProjects
    grep -q "$DEVICE" /etc/fstab || echo "$DEVICE /dockerProjects ext4 defaults,nofail 0 2" >> /etc/fstab

    docker run -d \
      --name npm_1 \
      --restart unless-stopped \
      --network common \
      -p 80:80 -p 443:443 -p 81:81 \
      -e TZ=Asia/Seoul \
      -v /dockerProjects/npm_1/data:/data \
      -v /dockerProjects/npm_1/letsencrypt:/etc/letsencrypt \
      jc21/nginx-proxy-manager:latest

    docker run -d \
      --name redis_1 \
      --restart unless-stopped \
      --network common \
      -e TZ=Asia/Seoul \
      -v /dockerProjects/redis_1/data:/data \
      redis --requirepass ${var.db_password}

    echo "${var.ghcr_token}" | docker login ghcr.io -u ${var.ghcr_owner} --password-stdin
  END_OF_FILE
}

resource "aws_instance" "ec2_1" {
  ami                         = data.aws_ami.latest_amazon_linux.id
  instance_type               = var.instance_type
  subnet_id                   = aws_subnet.subnet_1.id
  vpc_security_group_ids      = [aws_security_group.sg_1.id]
  associate_public_ip_address = true
  iam_instance_profile        = aws_iam_instance_profile.instance_profile_1.name

  root_block_device {
    volume_type = "gp3"
    volume_size = 20
  }

  user_data                   = local.ec2_user_data
  user_data_replace_on_change = true

  tags = { Name = "${var.prefix}-ec2-1" }
}

# EC2 인스턴스 수명(재생성 포함)과 분리된 영구 볼륨. NPM의 Let's Encrypt 인증서/프록시 설정 보관용.
resource "aws_ebs_volume" "npm_data" {
  # subnet_1과 같은 AZ로 고정 (instance 속성을 참조하면 인스턴스 재생성 때마다 볼륨도 같이
  # 재생성 대상으로 잡힐 수 있어, 볼륨 수명을 인스턴스와 명시적으로 분리한다).
  availability_zone = "${var.region}a"
  size              = 5
  type              = "gp3"
  tags              = { Name = "${var.prefix}-npm-data" }
}

resource "aws_volume_attachment" "npm_data" {
  device_name  = "/dev/xvdf"
  volume_id    = aws_ebs_volume.npm_data.id
  instance_id  = aws_instance.ec2_1.id
  force_detach = true
}

resource "aws_eip" "ec2_1" {
  instance = aws_instance.ec2_1.id
  domain   = "vpc"
  tags     = { Name = "${var.prefix}-eip-1" }
}
