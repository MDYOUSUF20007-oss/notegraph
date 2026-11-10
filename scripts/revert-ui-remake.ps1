param(
    [string]$Code = ""
)

$ExpectedCode = "NG-REVERT-82E6F03"
if ($Code -ne $ExpectedCode) {
    Write-Host "Pass the exact reverse code to restore NoteGraph UI to pre-ui-remake (82e6f03):" -ForegroundColor Yellow
    Write-Host "  .\scripts\revert-ui-remake.ps1 -Code $ExpectedCode" -ForegroundColor Cyan
    exit 1
}

git rev-parse --verify pre-ui-remake *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host "Fetching baseline tag pre-ui-remake from origin..." -ForegroundColor Yellow
    git fetch origin tag pre-ui-remake
}

$ahead = git rev-list pre-ui-remake..HEAD --count
if ([int]$ahead -eq 0) {
    Write-Host "Already at pre-ui-remake baseline (82e6f03)." -ForegroundColor Green
    exit 0
}

Write-Host "Reverting $ahead commit(s) back to pre-ui-remake (82e6f03)..." -ForegroundColor Cyan
git revert --no-commit pre-ui-remake..HEAD
if ($LASTEXITCODE -ne 0) {
    Write-Host "Revert failed. Inspect 'git status' or run: git reset --hard pre-ui-remake" -ForegroundColor Red
    exit 1
}

git commit -m "revert: restore UI prior to remake ($ExpectedCode)"
Write-Host "Reverted cleanly! Run 'git push origin main' to publish the restored UI." -ForegroundColor Green
