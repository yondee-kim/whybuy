# WHYBUY AWS

Version 1.0

---

# 인프라 원칙

## 1. 작게 시작한다

WHYBUY의 서버는

로그인과 백업만 담당한다.

트래픽이 크지 않다.

과설계하지 않는다.

---

## 2. 비용이 생존 조건이다

개인 프로젝트이다.

월 고정비가 커지면

서비스가 먼저 죽는다.

---

## 3. 나중에 키울 수 있게 만든다

지금은 단순하게.

하지만

확장을 막는 구조는 만들지 않는다.

---

# 구성도

## Phase 1 (MVP, 출시 시점)

```text
        [Android App]
              │
              │ HTTPS
              ▼
      ┌───────────────┐
      │  Route 53     │
      │ api.whybuy.kr │
      └───────┬───────┘
              ▼
      ┌───────────────┐
      │      ALB      │  ← ACM (SSL)
      └───────┬───────┘
              ▼
   ┌──────────────────────┐
   │   EC2 t3.small       │
   │   Docker             │
   │   Spring Boot        │
   │   (Public Subnet)    │
   └──────┬────────┬──────┘
          │        │
          ▼        ▼
   ┌───────────┐ ┌────────────┐
   │ RDS MySQL │ │     S3     │
   │ t4g.micro │ │  backup    │
   │ (Private) │ │  static    │
   └───────────┘ └─────┬──────┘
                       │
                       ▼
                 ┌────────────┐
                 │ CloudFront │
                 │ 약관/공지   │
                 └────────────┘
```

---

## Phase 2 (사용자 증가 시)

```text
ALB → EC2 x2 (Auto Scaling)
RDS Multi-AZ
ElastiCache (선택)
```

---

# 리소스 상세

## VPC

| 항목 | 값 |
|---|---|
| CIDR | 10.0.0.0/16 |
| Public Subnet A | 10.0.1.0/24 (ap-northeast-2a) |
| Public Subnet C | 10.0.2.0/24 (ap-northeast-2c) |
| Private Subnet A | 10.0.11.0/24 |
| Private Subnet C | 10.0.12.0/24 |

---

Public Subnet은

ALB와 EC2가 사용한다.

Private Subnet은

RDS가 사용한다.

---

NAT Gateway는 만들지 않는다.

월 4만원 이상 나온다.

MVP에는 필요 없다.

---

## EC2

| 항목 | 값 |
|---|---|
| 타입 | t3.small |
| OS | Amazon Linux 2023 |
| 스토리지 | gp3 30GB |
| 리전 | ap-northeast-2 (서울) |

---

### t3.small을 고른 이유

t2.micro (프리티어)는

Spring Boot 구동 시

메모리 1GB로는 불안정하다.

t3.small은 2GB이다.

---

프리티어 기간에는

t2.micro로 개발하고

출시 시점에 t3.small로 올린다.

---

### 설치

```bash
sudo dnf update -y
sudo dnf install -y docker git
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user

sudo curl -L \
  "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" \
  -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose
```

---

### 스왑

t3.small에서

빌드 시 메모리가 부족할 수 있다.

---

```bash
sudo dd if=/dev/zero of=/swapfile bs=128M count=16
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile swap swap defaults 0 0' | sudo tee -a /etc/fstab
```

---

## RDS

| 항목 | 값 |
|---|---|
| 엔진 | MySQL 8.0 |
| 타입 | db.t4g.micro |
| 스토리지 | gp3 20GB |
| Multi-AZ | 비활성 (Phase 1) |
| 퍼블릭 액세스 | 비활성 |
| 백업 보관 | 7일 |

---

### 파라미터

| 항목 | 값 |
|---|---|
| character_set_server | utf8mb4 |
| collation_server | utf8mb4_unicode_ci |
| time_zone | Asia/Seoul |

---

### 접속

EC2 보안그룹에서만 3306 허용

로컬에서 접속할 때는

SSH 터널을 사용한다.

---

```bash
ssh -i whybuy.pem -L 3307:whybuy-db.xxx.rds.amazonaws.com:3306 \
  ec2-user@<EC2_IP>
```

---

## S3

### 버킷

| 버킷 | 용도 | 퍼블릭 |
|---|---|---|
| whybuy-backup | 대용량 백업 | X |
| whybuy-static | 약관, 공지 HTML | CloudFront만 |
| whybuy-assets | 앱 아이콘, 이미지 | CloudFront만 |

---

### 백업 버킷 정책

- 퍼블릭 액세스 전면 차단
- 서버 측 암호화 (SSE-S3)
- 버전 관리 비활성
- 라이프사이클: 90일 후 삭제

---

라이프사이클을 두는 이유

탈퇴한 사용자의 잔여 객체를

자동으로 정리하기 위해서다.

---

### 접근 방식

EC2에 IAM Role을 붙인다.

액세스 키를 코드에 넣지 않는다.

---

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject",
        "s3:DeleteObject"
      ],
      "Resource": "arn:aws:s3:::whybuy-backup/*"
    }
  ]
}
```

---

## CloudFront

### 용도

앱 내 WebView가 여는 문서

---

- 이용약관
- 개인정보처리방침
- 공지사항
- FAQ

---

### 왜 CloudFront인가

이 문서들은

앱 업데이트 없이 바꿔야 한다.

S3에 HTML을 올리고

CloudFront로 서빙하면

배포가 파일 업로드로 끝난다.

---

### 설정

| 항목 | 값 |
|---|---|
| Origin | whybuy-static (OAC) |
| 기본 캐시 | 1시간 |
| HTML | 5분 |
| 이미지 | 30일 |
| 압축 | 활성 |
| HTTPS | 리다이렉트 |

---

### 도메인

| 도메인 | 대상 |
|---|---|
| api.whybuy.kr | ALB |
| static.whybuy.kr | CloudFront |
| whybuy.kr | 랜딩 페이지 (S3) |

---

### 무효화

문서 수정 후

```bash
aws cloudfront create-invalidation \
  --distribution-id XXXX \
  --paths "/terms.html" "/privacy.html"
```

---

## ALB

### 왜 필요한가

EC2 하나뿐이라면

ALB 없이 Nginx로도 된다.

---

그래도 ALB를 쓰는 이유

1. ACM 인증서 자동 갱신
2. 나중에 인스턴스를 늘릴 때 구조 변경이 없다
3. 헬스체크

---

월 약 2만원.

이 비용이 부담되면

Phase 1에서는

EC2 + Nginx + Let's Encrypt로 대체한다.

---

### 헬스체크

| 항목 | 값 |
|---|---|
| 경로 | /actuator/health |
| 간격 | 30초 |
| 임계값 | 2회 |

---

## Route 53

호스팅 영역 1개

월 0.5달러

---

## ACM

인증서 무료

ALB에 연결

자동 갱신

---

## Parameter Store

환경변수는

코드나 이미지에 넣지 않는다.

---

| 키 | 값 |
|---|---|
| /whybuy/prod/db-url | RDS 주소 |
| /whybuy/prod/db-username | |
| /whybuy/prod/db-password | SecureString |
| /whybuy/prod/jwt-secret | SecureString |
| /whybuy/prod/google-client-id | |

---

Secrets Manager 대신

Parameter Store를 쓰는 이유

기본 티어가 무료이다.

---

# 보안 그룹

| 이름 | 인바운드 | 소스 |
|---|---|---|
| whybuy-alb-sg | 80, 443 | 0.0.0.0/0 |
| whybuy-ec2-sg | 8080 | whybuy-alb-sg |
| whybuy-ec2-sg | 22 | 내 IP만 |
| whybuy-rds-sg | 3306 | whybuy-ec2-sg |

---

22번 포트를

0.0.0.0/0으로 열어두지 않는다.

---

가능하면

SSH 대신 SSM Session Manager를 쓴다.

키 관리가 필요 없어진다.

---

# 배포

## 방식

GitHub Actions → ECR → EC2

---

## 흐름

```text
main 브랜치 push
    ↓
GitHub Actions
    ↓
Gradle build + test
    ↓
Docker image build
    ↓
ECR push
    ↓
SSM Run Command
    ↓
EC2에서 pull + restart
```

---

## Dockerfile

```dockerfile
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY build/libs/*.jar app.jar

ENV TZ=Asia/Seoul
ENV JAVA_OPTS="-Xms256m -Xmx768m"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

---

## docker-compose.yml (EC2)

```yaml
services:
  app:
    image: ${ECR_REGISTRY}/whybuy:latest
    container_name: whybuy-app
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: prod
    restart: always
    logging:
      driver: json-file
      options:
        max-size: "10m"
        max-file: "3"
```

---

로그 로테이션을 반드시 설정한다.

설정하지 않으면

디스크가 로그로 가득 찬다.

---

## GitHub Actions

```yaml
name: deploy

on:
  push:
    branches: [ main ]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'

      - name: Build
        run: ./gradlew clean build

      - name: Configure AWS
        uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: ${{ secrets.AWS_ROLE_ARN }}
          aws-region: ap-northeast-2

      - name: Login ECR
        id: ecr
        uses: aws-actions/amazon-ecr-login@v2

      - name: Build and push
        run: |
          docker build -t $ECR/whybuy:$SHA -t $ECR/whybuy:latest .
          docker push $ECR/whybuy:$SHA
          docker push $ECR/whybuy:latest
        env:
          ECR: ${{ steps.ecr.outputs.registry }}
          SHA: ${{ github.sha }}

      - name: Deploy
        run: |
          aws ssm send-command \
            --instance-ids ${{ secrets.EC2_ID }} \
            --document-name "AWS-RunShellScript" \
            --parameters 'commands=[
              "cd /home/ec2-user/whybuy",
              "docker-compose pull",
              "docker-compose up -d",
              "docker image prune -f"
            ]'
```

---

AWS 자격증명은

액세스 키 대신

OIDC Role을 사용한다.

---

## 배포 중단 시간

MVP에서는

수 초의 중단을 허용한다.

무중단 배포는

Phase 2에서 도입한다.

---

# 모니터링

## CloudWatch

수집

- EC2 CPU, 메모리
- RDS 연결 수, CPU
- ALB 5xx 비율

---

## 알람

| 항목 | 임계값 | 조치 |
|---|---|---|
| EC2 CPU | 80% 5분 | 이메일 |
| RDS 스토리지 | 80% | 이메일 |
| ALB 5xx | 분당 10건 | 이메일 |
| 예상 요금 | 월 5만원 | 이메일 |

---

요금 알람은

반드시 설정한다.

개인 프로젝트에서

가장 위험한 것은

트래픽이 아니라 청구서이다.

---

## 로그

애플리케이션 로그는

CloudWatch Logs로 보낸다.

보관 기간 30일.

---

백업 payload는

절대 로그에 남기지 않는다.

---

# 비용 예상

## Phase 1 (월)

| 항목 | 비용 |
|---|---|
| EC2 t3.small | 약 30,000원 |
| RDS t4g.micro | 약 22,000원 |
| ALB | 약 25,000원 |
| S3 + CloudFront | 약 3,000원 |
| Route 53 | 약 700원 |
| 기타 | 약 3,000원 |
| 합계 | 약 84,000원 |

---

## 절감안

ALB 제거 (Nginx + Let's Encrypt)

↓

약 59,000원

---

RDS 대신 EC2 내 MySQL 컨테이너

↓

약 37,000원

---

단,

DB를 EC2에 두면

백업과 복구가 전적으로 수동이 된다.

사용자 데이터를 다루는 이상

RDS를 권장한다.

---

## 프리티어 활용

계정 생성 12개월 이내라면

- EC2 t2.micro 750시간 무료
- RDS db.t3.micro 750시간 무료
- S3 5GB 무료

---

개발 기간에는

거의 무료로 진행 가능하다.

---

# 백업과 복구

## RDS 자동 백업

보관 7일

---

## 수동 스냅샷

배포 전

주요 마이그레이션 전

반드시 생성한다.

---

## 복구 훈련

출시 전에

스냅샷 복원을 한 번 실제로 해본다.

해본 적 없는 복구 절차는

없는 것과 같다.

---

# 도메인

## 취득

whybuy.kr 또는 whybuy.app

---

Route 53에서 직접 등록하거나

가비아 등에서 등록 후

네임서버만 Route 53으로 변경한다.

---

## 필수 페이지

Play Console 심사에

개인정보처리방침 URL이 필요하다.

---

https://whybuy.kr/privacy

이 URL은

앱 출시 전에 반드시 살아 있어야 한다.

---

# 체크리스트

## 인프라 구축

- [ ] AWS 계정 생성, MFA 설정
- [ ] 요금 알람 설정
- [ ] IAM 사용자 생성 (루트 사용 금지)
- [ ] VPC, 서브넷 생성
- [ ] 보안 그룹 생성
- [ ] EC2 생성, Docker 설치
- [ ] RDS 생성, 파라미터 그룹 설정
- [ ] S3 버킷 3개 생성
- [ ] CloudFront 배포
- [ ] 도메인 등록
- [ ] Route 53 레코드 설정
- [ ] ACM 인증서 발급
- [ ] ALB 생성, 타깃 그룹 연결
- [ ] Parameter Store 값 등록
- [ ] ECR 리포지토리 생성
- [ ] GitHub OIDC Role 생성
- [ ] Actions 워크플로 작성
- [ ] 헬스체크 확인
- [ ] 개인정보처리방침 페이지 배포
- [ ] 스냅샷 복원 테스트

---

# 마지막

인프라는

WHYBUY의 본질이 아니다.

편이가

사용자의 화면에 나타나는 순간이

본질이다.

서버에 시간을 너무 많이 쓰지 않는다.

Phase 1은

돌아가기만 하면 충분하다.
