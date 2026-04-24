$token = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiI3NjU2MTE5OTE1NTMyNDc2MiIsImlhdCI6MTc3Njk4ODE1MiwiZXhwIjoxNzc2OTkxNzUyfQ.egOZVkglBAvM49-SWULIGpRCfeChr0UGZsRihTa-KSc'
$headers = @{ Authorization = "Bearer $token" }
try {
    $r = Invoke-RestMethod -Uri 'http://localhost:8081/api/google/templates' -Method Get -Headers $headers -ErrorAction Stop
    Write-Host "OK:"; $r | ConvertTo-Json -Depth 5;
} catch {
    Write-Host "Request failed:" $_.Exception.Message
    if ($_.Exception.Response) {
        $resp = $_.Exception.Response
        try {
            $sr = New-Object System.IO.StreamReader($resp.GetResponseStream())
            $content = $sr.ReadToEnd()
            Write-Host "RESPONSE BODY:"; Write-Host $content
        } catch {}
    }
}
