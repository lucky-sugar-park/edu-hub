# scripts/deploy-frontend.ps1 — 빌드한 MFE 하나를 화면 폴더(기본: 프로젝트 루트의 run\web)에 배포(교체)하거나 직전 버전으로 되돌린다
# 사용: .\scripts\deploy-frontend.ps1 -App course-mfe [-Source <빌드 폴더>] [-WebRoot <화면 폴더>] [-Rollback]   (Windows PowerShell 5.1 이상)
param(
    [Parameter(Mandatory = $true)][ValidateSet('shell', 'course-mfe', 'enrollment-mfe')][string]$App,
    [string]$Source,                                   # 기본: frontend\apps\<App>\dist
    [string]$WebRoot,                                  # 기본: 프로젝트 루트의 run\web. 운영에서는 웹 서버(nginx 등)의 폴더
    [switch]$Rollback
)
$ErrorActionPreference = 'Stop'
# Windows PowerShell 5.1은 [Parameter()]가 있는 스크립트의 param 기본값 계산 때 $PSScriptRoot가 비어 있으므로 본문에서 정한다
if (-not $WebRoot) { $WebRoot = Join-Path $PSScriptRoot '..\run\web' }

$target = @{ 'shell' = 'shell'; 'course-mfe' = 'mfe\course'; 'enrollment-mfe' = 'mfe\enrollment' }[$App]
$dest = Join-Path $WebRoot $target
$prev = "$dest.prev"; $new = "$dest.new"; $broken = "$dest.broken"

# 폴더 이름 바꾸기. 서버가 마침 그 폴더의 파일을 읽는 중이면 Windows가 잠시 거부할 수 있어 몇 번 다시 시도한다
function Rename-Retry([string]$Path, [string]$NewPath) {
    for ($i = 1; $i -le 5; $i++) {
        try { Rename-Item -LiteralPath $Path -NewName (Split-Path $NewPath -Leaf) -ErrorAction Stop; return }
        catch { if ($i -eq 5) { throw }; Start-Sleep -Milliseconds 500 }
    }
}

if ($Rollback) {
    if (-not (Test-Path $prev)) { throw "되돌릴 이전 버전이 없습니다: $prev" }
    if (Test-Path $broken) { Remove-Item $broken -Recurse -Force }
    if (Test-Path $dest) { Rename-Retry $dest $broken }
    Rename-Retry $prev $dest
    Write-Host "롤백 완료: $App <- 이전 버전 ($dest). 문제가 된 버전은 $broken"
    exit 0
}

if (-not $Source) { $Source = Join-Path $PSScriptRoot "..\frontend\apps\$App\dist" }
$entry = if ($App -eq 'shell') { 'index.html' } else { 'remoteEntry.js' }
if (-not (Test-Path (Join-Path $Source $entry))) { throw "빌드 결과가 없습니다: $Source\$entry (npm run build:deploy 먼저)" }

# 1) 새 버전을 옆 폴더(.new)에 모두 복사해 둔다 — 복사 도중의 반쪽 상태가 사용자에게 보이지 않게
if (Test-Path $new) { Remove-Item $new -Recurse -Force }
New-Item -ItemType Directory -Force -Path (Split-Path $dest) | Out-Null
Copy-Item -Path $Source -Destination $new -Recurse
# 2) 이름만 바꿔 교체: 현재 → .prev(롤백용으로 한 단계 보관), .new → 현재
if (Test-Path $prev) { Remove-Item $prev -Recurse -Force }
if (Test-Path $dest) { Rename-Retry $dest $prev }
try { Rename-Retry $new $dest }
catch { if (Test-Path $prev) { Rename-Retry $prev $dest }; throw }   # 실패하면 이전 버전을 제자리로
Write-Host "배포 완료: $App -> $dest"