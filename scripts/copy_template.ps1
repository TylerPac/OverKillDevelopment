$token = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiI3NjU2MTE5OTE1NTMyNDc2MiIsImlhdCI6MTc3Njk4ODE1MiwiZXhwIjoxNzc2OTkxNzUyfQ.egOZVkglBAvM49-SWULIGpRCfeChr0UGZsRihTa-KSc'
$headers = @{ Authorization = "Bearer $token"; 'Content-Type' = 'application/json' }
$body = Get-Content -Raw 'scripts\request.json'
try {
    $r = Invoke-WebRequest -Uri 'http://localhost:8081/api/google/copy-template' -Method Post -Headers $headers -Body $body -UseBasicParsing -ErrorAction Stop
    Write-Host "STATUS: $($r.StatusCode)"
    Write-Host "CONTENT:"
    Write-Host $r.Content
} catch {
    $err = $_.Exception
    if ($err -and $err.Response) {
        $resp = $err.Response
        try {
            $sr = New-Object System.IO.StreamReader($resp.GetResponseStream())
            $content = $sr.ReadToEnd()
            Write-Host "ERROR STATUS: $($resp.StatusCode.value__)"
            Write-Host "ERROR CONTENT:"
            Write-Host $content
        } catch {
            Write-Host "Failed to read error response: $($_.Exception.Message)"
        }
    } else {
        Write-Host "Invoke-WebRequest failed: $($err.Message)"
    }
}
