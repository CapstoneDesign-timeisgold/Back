param([string]$Before = 'before-valid', [string]$After = 'after-valid')
$ErrorActionPreference = 'Stop'
$rows = foreach ($label in @($Before, $After)) {
    foreach ($vus in @(1, 10, 30)) {
        foreach ($run in 1..3) {
            $path = Join-Path $PSScriptRoot "results/$label/vus-$vus-run-$run-summary.json"
            $data = Get-Content -Raw -LiteralPath $path | ConvertFrom-Json
            [pscustomobject]@{
                version = $label
                vus = $vus
                repetition = $run
                requests = $data.metrics.http_reqs.values.count
                rps = $data.metrics.http_reqs.values.rate
                p95_ms = $data.metrics.http_req_duration.values.'p(95)'
                p99_ms = $data.metrics.http_req_duration.values.'p(99)'
                error_rate = $data.metrics.http_req_failed.values.rate
                failed_checks = $data.metrics.checks.values.fails
            }
        }
    }
}
$rows | Export-Csv -LiteralPath (Join-Path $PSScriptRoot 'results/comparison.csv') -NoTypeInformation -Encoding utf8
$table = @('| VUs | Before p95 median (range), ms | After p95 median (range), ms | p95 reduction | Before RPS median | After RPS median |',
    '|---:|---:|---:|---:|---:|---:|')
foreach ($vus in @(1, 10, 30)) {
    $old = @($rows | Where-Object { $_.version -eq $Before -and $_.vus -eq $vus })
    $new = @($rows | Where-Object { $_.version -eq $After -and $_.vus -eq $vus })
    $oldP95 = @($old.p95_ms | Sort-Object)
    $newP95 = @($new.p95_ms | Sort-Object)
    $oldRps = @($old.rps | Sort-Object)
    $newRps = @($new.rps | Sort-Object)
    $table += [string]::Format([Globalization.CultureInfo]::InvariantCulture,
        '| {0} | {1:F2} ({2:F2}-{3:F2}) | {4:F2} ({5:F2}-{6:F2}) | {7:F1}% | {8:F2} | {9:F2} |',
        $vus, $oldP95[1], $oldP95[0], $oldP95[2], $newP95[1], $newP95[0], $newP95[2],
        (100 * (1 - $newP95[1] / $oldP95[1])), $oldRps[1], $newRps[1])
}
$table | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'results/comparison.md') -Encoding utf8
$table
$rows | Group-Object version | ForEach-Object {
    [pscustomobject]@{
        version = $_.Name
        total_requests = ($_.Group.requests | Measure-Object -Sum).Sum
        max_error_rate = ($_.Group.error_rate | Measure-Object -Maximum).Maximum
        failed_checks = ($_.Group.failed_checks | Measure-Object -Sum).Sum
    }
}
