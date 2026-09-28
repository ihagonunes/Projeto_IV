<#
.SYNOPSIS
    Carrega o dataset sintético da QSOFT no schema `historical` (SPI-75).

.DESCRIPTION
    O dataset vem em um dump custom-format (`pg_dump -Fc`) com 17 tabelas em
    `public` (text/bigint/double precision, sem FKs). Este script:

      1. restaura o dump em um banco TEMPORÁRIO (não polui o banco alvo);
      2. copia cada tabela para `historical.<tabela>` no banco alvo;
      3. opcionalmente reescreve `company_id` para o `tenant_id` da plataforma
         (mapeamento), tornando o histórico filtrável por tenant (ADR-03/26);
      4. remove o banco temporário.

    O histórico NÃO tem FK para as tabelas operacionais (ADR-26) e é massa de
    teste/BI — não simula aggregates operacionais.

.EXAMPLE
    ./scripts/db/load-historical.ps1 -DumpPath "$env:USERPROFILE\Downloads\backup_banco.sql"
    ./scripts/db/load-historical.ps1 -DumpPath ... -CompanyMapping "COMP-0001=b621948f-0e43-4086-a2fc-ed0f2737d716"
#>
param(
    [Parameter(Mandatory = $true)][string]$DumpPath,
    [string]$TargetDb = "qsoft_governance",
    [string]$Host_ = "localhost",
    [string]$Port = "5433",
    [string]$User = "qsoft",
    [string]$Password = "change_me_local",
    # "COMP-A=<uuid>;COMP-B=<uuid>" — reescreve company_id para o tenant_id.
    [string]$CompanyMapping = "",
    [string]$PgBin = "C:\Program Files\PostgreSQL\18\bin"
)

$ErrorActionPreference = "Stop"
$env:PGPASSWORD = $Password
$psql = Join-Path $PgBin "psql.exe"
$pgRestore = Join-Path $PgBin "pg_restore.exe"
$pgDump = Join-Path $PgBin "pg_dump.exe"
$tempDb = "qsoft_historical_load"

# As 17 tabelas do dataset QSOFT (ver V014__create_historical_schema.sql).
$tables = @(
    "companies", "departments", "employees", "projects", "builds", "deployments",
    "issues", "pull_requests", "ai_tools", "ai_models", "ai_usage_logs", "ai_billing",
    "ai_budgets", "ai_licenses", "approved_ai_tools", "security_events", "training_records"
)

if (-not (Test-Path -LiteralPath $DumpPath)) { throw "Dump não encontrado: $DumpPath" }
foreach ($exe in @($psql, $pgRestore, $pgDump)) {
    if (-not (Test-Path -LiteralPath $exe)) { throw "Ferramenta não encontrada: $exe" }
}

function Invoke-Psql([string]$Db, [string]$Sql) {
    & $psql -h $Host_ -p $Port -U $User -d $Db -v ON_ERROR_STOP=1 -q -c $Sql
    if ($LASTEXITCODE -ne 0) { throw "psql falhou (db=$Db): $Sql" }
}

Write-Host "==> Recriando banco temporário '$tempDb'"
Invoke-Psql "postgres" "DROP DATABASE IF EXISTS $tempDb;"
Invoke-Psql "postgres" "CREATE DATABASE $tempDb;"

try {
    Write-Host "==> Restaurando o dump no banco temporário"
    & $pgRestore -h $Host_ -p $Port -U $User -d $tempDb --no-owner --no-privileges $DumpPath
    if ($LASTEXITCODE -ne 0) { throw "pg_restore falhou." }

    foreach ($table in $tables) {
        Write-Host "==> Copiando $table -> historical.$table"
        # pg_dump -a --column-inserts gera INSERTs; reescrevemos o destino para historical.*
        $sql = & $pgDump -h $Host_ -p $Port -U $User -d $tempDb -a -t $table --column-inserts --no-owner
        if ($LASTEXITCODE -ne 0) { throw "pg_dump falhou para $table." }
        $sql = $sql -replace "(?m)^INSERT INTO public\.$table ", "INSERT INTO historical.$table "
        $sql = $sql -replace "(?m)^INSERT INTO $table ", "INSERT INTO historical.$table "
        if ($sql -match "INSERT INTO") {
            $file = [System.IO.Path]::GetTempFileName()
            [System.IO.File]::WriteAllText($file, ($sql -join "`n"), [System.Text.UTF8Encoding]::new($false))
            & $psql -h $Host_ -p $Port -U $User -d $TargetDb -v ON_ERROR_STOP=1 -q -f $file
            if ($LASTEXITCODE -ne 0) { throw "Carga de $table falhou." }
            Remove-Item -LiteralPath $file -Force
        }
    }

    if ($CompanyMapping.Trim().Length -gt 0) {
        foreach ($pair in $CompanyMapping.Split(";")) {
            $kv = $pair.Split("=", 2)
            if ($kv.Length -ne 2) { throw "Mapeamento inválido: $pair (use COMP-A=<uuid>)" }
            $from = $kv[0].Trim(); $to = $kv[1].Trim()
            Write-Host "==> company_id '$from' -> tenant '$to'"
            foreach ($table in $tables) {
                $hasColumn = & $psql -h $Host_ -p $Port -U $User -d $TargetDb -t -A -c `
                    "SELECT count(*) FROM information_schema.columns WHERE table_schema='historical' AND table_name='$table' AND column_name='company_id';"
                if ($hasColumn.Trim() -eq "1") {
                    Invoke-Psql $TargetDb "UPDATE historical.$table SET company_id = '$to' WHERE company_id = '$from';"
                }
            }
        }
    }

    Write-Host "==> Dataset carregado em historical.* ($($tables.Count) tabelas)."
}
finally {
    Write-Host "==> Removendo banco temporário"
    Invoke-Psql "postgres" "DROP DATABASE IF EXISTS $tempDb;"
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}
