# .claude/hooks/protect-files.ps1 — PreToolUse(Edit|Write): 보호 파일 수정 차단 (Windows PowerShell 버전)
# Step 3의 protect-files.sh와 같은 규칙. 막을 때는 "이유 + 대신 할 일"을 stderr로 쓰고 exit 2

# 한글 메시지가 깨지지 않도록 콘솔 출력 인코딩을 UTF-8로
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

function Block([string]$message) {
    [Console]::Error.WriteLine($message)
    exit 2
}

# 1) stdin으로 들어온 훅 입력(JSON)에서 수정하려는 파일 경로를 꺼낸다
try {
    $hookInput = [Console]::In.ReadToEnd() | ConvertFrom-Json
} catch {
    exit 0   # 입력을 읽지 못하면 막지 않는다 (훅 오류로 모든 작업이 멈추지 않게)
}
$file = [string]$hookInput.tool_input.file_path
if ([string]::IsNullOrEmpty($file)) { exit 0 }

# 2) 경로 구분자를 / 로 통일 (C:\work\eduhub\.env → C:/work/eduhub/.env)
$path = $file -replace '\\', '/'

# 3) 보호 규칙 (위에서부터 검사. -match는 대소문자를 구분하지 않는다)
if ($path -match 'application-prod\.yml$') {
    Block '운영 설정은 사람이 PR로만 수정합니다. 개발 설정은 application-dev.yml을 수정하세요.'
}
if ($path -match '(^|/)\.env\.example$') {
    exit 0   # .env.example은 변수 이름만 적는 파일이라 허용
}
if ($path -match '(^|/)\.env(\..+)?$') {
    Block '비밀 파일은 수정하지 않습니다. 필요한 변수 이름은 .env.example에 추가하세요.'
}
if ($path -match '/api-client/src/generated/') {
    Block "자동 생성 코드입니다. 'npm run generate:api'로 다시 생성하세요."
}
if ($path -match '/db/migration/V[^/]*\.sql$' -and (Test-Path -LiteralPath $file)) {
    Block '기존 Flyway 마이그레이션은 수정할 수 없습니다. 다음 버전 번호로 새 파일을 추가하세요.'
}
exit 0