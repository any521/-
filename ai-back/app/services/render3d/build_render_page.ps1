# ============================================================
# 构建 render_page 执行器（Track G · extreme 渲染闭环）
# 入口 = front-user/src/three/renderEntry.ts（复用 specModel.ts 执行器语义）
# 产物 = render_page/spec-executor.iife.js（纳入仓库，仅前端依赖变更时重跑）
# 前置：front-user 已 npm install（vite 8 自带 rolldown，即本脚本打包器）
# ============================================================
$ErrorActionPreference = 'Stop'

$proj = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))  # ai-back
$repo = Split-Path -Parent $proj                                                      # 项目根
$frontUser = Join-Path $repo 'front-user'
$entry = Join-Path $frontUser 'src\three\renderEntry.ts'
$outDir = Join-Path $PSScriptRoot 'render_page'
$out = Join-Path $outDir 'spec-executor.iife.js'
$rolldown = Join-Path $frontUser 'node_modules\rolldown\bin\cli.mjs'

if (-not (Test-Path $rolldown)) { throw "front-user 未安装依赖（缺 rolldown），请先 npm install" }

node $rolldown $entry --format iife --minify --platform browser --file $out
if ($LASTEXITCODE -ne 0) { throw "rolldown 构建失败（exit=$LASTEXITCODE）" }
Write-Host "render page executor built: $out ($((Get-Item $out).Length) bytes)"
