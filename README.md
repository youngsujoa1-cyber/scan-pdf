# 스캔 PDF (ScanPDF) — 1.0.4

문서를 찍어 바로 PDF 로 만드는 안드로이드 앱입니다.
스캔 · 자르기 · 필터 · 병합 · 압축을 **모두 기기 안에서** 처리하고, 하단 배너 + PDF 저장 후 전면 광고로 수익화합니다.

- 패키지: `com.youngsu.scanpdf`
- minSdk 26 / targetSdk 35, AGP 8.7.3, Kotlin 2.0.21
- 언어: 한국어 · 영어(기본) · 일본어 · 스페인어 · 중국어 (`values-ko/ja/es/zh`)

## 화면

| 화면 | 내용 |
| --- | --- |
| 홈 | 카메라 스캔 히어로 카드, 사진에서 PDF / PDF 합치기 / PDF 압축 / PDF 열기, 최근 만든 PDF 3개 |
| 카메라 | CameraX 연속 촬영, 문서 가이드 프레임, 플래시, 촬영 썸네일, 갤러리에서 추가 |
| 편집 | 페이지별 회전 · 자르기(4점 원근) · 필터(원본/문서/흑백/그레이) · 순서 · 삭제 → PDF 만들기 |
| 자르기 | 모서리 4점 드래그, 격자 안내, 전체 선택 |
| 뷰어 | 저장한 PDF 넘겨 보기, 공유 · 이름 변경 · 다운로드에 저장 · 다른 앱으로 열기 · 삭제 |
| 설정 | PDF 화질(300/200/150dpi), 앱 언어(기기 언어 따르기 · 한국어 · English · 日本語 · 中文 · Español), 개인정보·저장 위치 안내, 광고 상태, 버전 |
| 첫 실행 | 언어 선택 카드 (6개 언어, 고른 언어로 카드 문구가 바로 미리 바뀜) → [시작하기] |

## 언어 선택

### 처음 실행 (1.0.4)

- 앱을 **처음 열면** `MainActivity` 가 `ui/LanguageOnboarding.kt` 의 언어 카드를 먼저 띄운다.
  구름 날씨 레이더의 첫 실행 카드와 같은 방식으로, 다크 카드 안에 6개 언어를 **2열 × 3줄**로 놓고
  고른 언어로 제목·안내·버튼 문구가 **그 자리에서 미리 바뀐다**(앱을 다시 그리지 않음).
  각 언어 이름은 그 나라 말(한국어 · English · 日本語 · 中文 · Español)로 적어 어느 언어로 열려 있어도 찾을 수 있다.
- **[시작하기]** → `AppPrefs.languageOnboarded = true` **저장 후** `AppLanguage.apply(tag)` 로 언어를 적용한다.
  (언어가 바뀌면 액티비티가 다시 만들어지므로 저장을 먼저 한다 — 순서가 바뀌면 카드가 다시 뜬다.)
- 뒤로 가기로 닫아도 완료로 저장해 다시 묻지 않는다(`setOnCancelListener`). 바깥 터치로는 닫히지 않는다.
- 설정 → 앱 언어에서 언제든 바꿀 수 있는 것은 그대로다.

### 설정에서 바꾸기 (1.0.2~1.0.3)

- 설정 탭 → **언어** 카드를 누르면 `기기 언어 따르기` / `한국어` / `English` / `日本語` / `中文` / `Español` 중에서 고릅니다.
  안내 문구("휴대폰 언어를 따르거나 앱에서만 쓸 언어를 고를 수 있습니다")는 카드 아래에 작게 적고,
  다이얼로그는 **제목 + 선택 목록 + 취소** 만으로 그립니다(1.0.3).
  `MaterialAlertDialogBuilder` 에 `setMessage` 와 `setSingleChoiceItems` 를 함께 넣으면 AppCompat `AlertController.setupContent` 가
  안내 문구만 그리고 목록(`ListView`)을 붙이지 않아 선택지가 통째로 사라집니다(1.0.2 버그).
- `data/AppLanguage.kt` 가 `AppCompatDelegate.setApplicationLocales` 로 전환합니다. 액티비티가 모두 `AppCompatActivity` 라
  Android 8~15 에서 같은 방식으로 동작하고, Android 13+ 의 '앱별 언어' 시스템 설정에도 같은 값이 보입니다.
- 고르는 즉시 열려 있는 화면이 새 언어로 다시 그려지고, 카메라·편집·뷰어·합치기·압축도 같은 언어로 열립니다.
  다시 그려질 때 보던 탭은 `onSaveInstanceState` 로 기억해 그대로 엽니다.
- Android 12 이하에서도 선택이 남도록 매니페스트에 `androidx.appcompat.app.AppLocalesMetadataHolderService`(`autoStoreLocales=true`)를 둡니다.

## PDF 처리 방식

- 생성: 페이지 사진을 자르기 → 회전 → 필터 → JPEG 로 한 번 압축해
  `PdfWriter`(순수 Kotlin)가 **JPEG(DCTDecode) 그대로** PDF 에 넣습니다.
  이미지를 다시 그리지 않아 페이지당 수백 KB 수준이고 화질 손실이 없습니다.
  (A4 긴 변 842pt 기준으로 페이지 비율을 유지)
- 병합: `PdfRenderer` 로 각 페이지를 약 216dpi 로 다시 그려 원본 페이지 크기(pt) 그대로 이어 붙입니다.
- 압축: `PdfRenderer` 로 다시 그린 뒤 프리셋(약하게 3.0x/q88, 보통 2.2x/q78, 강하게 1.6x/q62)으로
  JPEG 품질을 낮춰 저장하고 전/후 용량을 비교합니다. 원본보다 작아지지 않으면 원본을 남깁니다.
- 저장 위치: 앱 전용 `Android/data/com.youngsu.scanpdf/files/PDF` — 공유/다운로드 저장으로 내보냅니다.

## 광고 (AdMob)

- 광고 ID 는 코드 상수가 아니라 `app/build.gradle.kts` 의 `buildConfigField` 가 넣습니다.
  `ads/AdConfig.kt` 는 `BuildConfig.ADMOB_BANNER_UNIT_ID` / `ADMOB_INTERSTITIAL_UNIT_ID` 를 읽기만 하고,
  앱 ID 는 `AndroidManifest.xml` 의 `${admobAppId}` 자리표시자가 받습니다.
  - debug: Google 공식 테스트 ID (실기기에서 눌러도 계정에 문제 없음)
  - release: AdMob 실제 ID — 앱 `ca-app-pub-4832055633016363~6067607403`, 배너 `…/7197369596`, 전면 `…/2192454922`
  - 그래서 release `.aab` 의 매니페스트·dex 에는 테스트 ID 문자열이 0건입니다.
- 배너는 하단 탭 바 **아래, 화면 맨 끝**에 붙습니다 (광고가 없으면 자리도 차지하지 않음).
  시스템 3버튼 내비게이션 바는 평소에 숨기고(아래를 스와이프하면 잠깐 표시), 보일 때는 배너가 그만큼 여백을 비켜 갑니다.
- 전면 광고는 PDF 저장·병합·압축이 끝난 뒤, 준비된 광고가 있을 때만(최소 60초 간격) 표시합니다.

## 빌드

```bash
export JAVA_HOME=/workspace/buildenv/jdk-17.0.21.1+2
export GRADLE_USER_HOME=/workspace/buildenv/gradle-home
./gradlew lintDebug assembleDebug --offline
# app/build/outputs/apk/debug/app-debug.apk
```

### Play 스토어 업로드용 release `.aab`

서명 키는 저장소 밖 `projects/keystores/topster-upload.jks` + `projects/keystores/keystore.properties`(권한 600)이고,
`app/build.gradle.kts` 가 `../keystores/keystore.properties` 를 찾아 `signingConfigs.release` 를 만듭니다.
두 파일은 `.gitignore` 로 제외되어 있습니다(**잃으면 이후 업데이트 서명 불가**).

```bash
./gradlew lintDebug assembleDebug --offline
./gradlew bundleRelease --offline        # app/build/outputs/bundle/release/app-release.aab
./gradlew assembleRelease --offline      # 실기기 테스트용 APK(같은 키로 서명)
$JAVA_HOME/bin/jarsigner -verify app/build/outputs/bundle/release/app-release.aab   # "jar verified."
```

> 작업 환경 메모리가 작으면 `--no-daemon --max-workers=1 -Dorg.gradle.parallel=false -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs="-Xmx900m -XX:MaxMetaspaceSize=320m"` 로 R8 단계를 넘깁니다.
> 한 번에 5분을 넘기면 `:app:compileReleaseKotlin` → `:app:minifyReleaseWithR8` → `bundleRelease` 처럼 나눠 실행해도 됩니다(캐시가 이어집니다).

## 검증

안드로이드 없이 돌아가는 순수 로직은 JVM 에서, 만들어진 PDF 는 MuPDF 로 다시 열어 확인합니다.

```bash
# 1) 순수 로직 + PDF 파일 생성
bash tools/logic-check/run.sh
# 2) 생성된 PDF 를 PyMuPDF 로 구조·렌더링 검사
python3 tools/logic-check/verify.py
```

`run.sh` 는 PdfWriter/Filters/ScanGeometry/FileNames 와 Check.kt 를 Gradle 캐시의
kotlin-compiler-embeddable 로 컴파일해 실행합니다. 마지막 줄이 `ALL CHECKS PASSED` 이면 통과입니다.

### 1.0.4 release 빌드 검증 결과 (Play 출시용)

- `./gradlew lintDebug assembleDebug --offline` → **BUILD SUCCESSFUL**, lint `0 errors, 43 warnings`
- `./gradlew bundleRelease --offline` → **BUILD SUCCESSFUL** → `app/build/outputs/bundle/release/app-release.aab` 7,027,101 bytes
  (sha256 `d6fe7637fa48c776843af7e798dd88cb31d7f80df7bb42d03802f58958f2e223`)
- 서명: `jarsigner -verify` → **jar verified.** / `CN=Topster, O=Topster, C=KR` / 인증서 만료 2054-04-13
- AAB 매니페스트(`bundletool dump manifest`): `package=com.youngsu.scanpdf`, `versionCode=5`, `versionName=1.0.4`,
  `minSdkVersion=26`, **`targetSdkVersion=36`**, `compileSdkVersion=36`,
  `com.google.android.gms.ads.APPLICATION_ID = ca-app-pub-4832055633016363~6067607403`
- AAB `base/dex/classes.dex`(5,215,132 bytes): 실제 배너 ID 1회·전면 ID 1회, **테스트 배너/전면 ID 0회**
- debug APK: 병합 매니페스트 `APPLICATION_ID` 가 테스트 ID, dex 에 테스트 배너·전면 ID만 남고 실제 ID 0회
- R8 축소(release)로 dex 가 debug(23.5MB, 11개)에서 5.2MB(1개)로 줄었습니다.

### 1.0.4 검증 결과 (첫 실행 언어 선택)

- `./gradlew lintDebug --offline` → **BUILD SUCCESSFUL**, `0 errors, 43 warnings` (기존 경고만)
- `./gradlew assembleDebug --offline` → **BUILD SUCCESSFUL**
- APK 확인: `versionCode='5' versionName='1.0.4'` (12,177,269 bytes, `aapt2 dump badging`)
- `aapt2 dump resources`: `onboarding_language_title/sub/note`, `onboarding_start` 가 **en/ko/ja/zh/es 5개 언어 모두** 들어 있음
- 컴파일된 `MainActivity` 바이트코드에서 `AppPrefs.getLanguageOnboarded()` 로 막고 `LanguageOnboarding.show()` 를 부르는 경로 확인,
  `LanguageOnboarding` 클래스가 dex 에 포함됨
- 새 코드에서 생긴 lint 경고 `AppBundleLocaleChanges`(언어 미리보기용 임시 `Configuration`)는 실제 로케일 전환이 아니라
  `@SuppressLint` 로 이유를 적고 끔
- 기기에서의 실제 표시(첫 실행 카드가 뜨고, 언어를 고르면 그 언어로 시작되고, 다음 실행부터는 안 뜨는지)는
  빌드 환경에 기기·에뮬레이터가 없어 **실행 확인 전** — APK 설치 후 1분이면 확인 가능

### 1.0.3 검증 결과 (언어 목록 + 자르기 화면)

- `./gradlew lintDebug assembleDebug --offline` 통과 (lint 오류 0건, 경고 44건)
- APK 확인: `versionCode 4 / versionName 1.0.3` (`aapt2 dump badging`)
- **언어 선택 다이얼로그**: AppCompat 1.7.0 `AlertController.setupContent` 바이트코드에서
  `mMessage != null` 이면 `ListView` 를 붙이지 않고 안내 문구만 남기는 경로를 확인 → `setMessage` 제거로 목록이 그려지는 경로만 남김.
  컴파일된 `SettingsScreen` 에서 `showLanguagePicker` 가 `setSingleChoiceItems` 만 호출하고 `setMessage` 를 부르지 않음을 확인.
- **자르기 화면**: 원인은 `CropOverlayView.imageRect` 가 `onSizeChanged` 에서만 계산되던 것.
  사진은 화면 배치가 끝난 뒤 비동기로 들어오므로 `imageRect` 가 `0×0` 으로 남아
  `drawBitmap` 이 아무것도 그리지 않고 모서리 핸들 4개가 모두 `(0,0)` 에 겹쳤습니다.
  `bitmap` 설정 시 `updateImageRect()` 를 다시 부르고, `onDraw` 에서도 비어 있으면 한 번 더 계산하도록 고쳤습니다(자리 계산은 `ScanGeometry.fitRect`/`project`).
  JVM 검사(`tools/logic-check`)에 fitRect·핸들 좌표 검사를 추가해 통과,
  컴파일된 `CropOverlayView.setBitmap` 이 `updateImageRect` 를 호출함을 바이트코드로 확인.
- 기기에서의 실제 화면 확인은 빌드 환경에 기기/에뮬레이터가 없어 실행 확인 전입니다.

### 1.0.2 검증 결과 (앱 내 언어 선택)

- `./gradlew lintDebug assembleDebug --offline` 통과 (lint 오류 0건, 경고만)
- APK 확인: `versionCode 3 / versionName 1.0.2`, 5개 언어 리소스에 새 문구 포함(`aapt2 dump resources`)
- 매니페스트 병합본에 `AppLocalesMetadataHolderService` + `autoStoreLocales=true` 포함 확인
- 언어 변경 시 화면 재생성 경로 확인: AppCompat `updateAppConfiguration` 이 `CONFIG_LOCALE` 미처리 액티비티에
  `ActivityCompat.recreate` 를 호출하는 것을 바이트코드에서 확인 (모든 액티비티가 locale 을 configChanges 에 넣지 않음)
- 기기에서의 실제 전환(설정에서 언어 선택 → 즉시 반영 → 앱 재시작 후 유지)은 빌드 환경에 기기가 없어 실행 확인 전입니다.

### 1.0.0 검증 결과

- `./gradlew lintDebug assembleDebug --offline` 통과 (lint 오류 0건)
- JVM 로직 검사 50여 항목 통과 — 회전/자르기 좌표, 4가지 필터, PDF 조립(xref 오프셋·페이지 수·페이지 크기),
  JPEG 헤더 해석, 파일 이름/용량 표기
- 만들어진 PDF(3페이지 509KB, 흑백 2페이지 289KB)를 MuPDF 로 열어 페이지 수·MediaBox·JPEG(DCTDecode)
  임베드·렌더링·한글 제목 메타데이터까지 확인
- 기기(실제 안드로이드)에서의 카메라/광고/렌더링은 이 빌드 환경에 기기가 없어 실행 확인 전입니다.
  APK 설치 후 첫 화면 → 카메라 스캔 → 편집 → PDF 만들기 순으로 확인이 필요합니다.
