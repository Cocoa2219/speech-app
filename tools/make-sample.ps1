# Generates the debug sample recording (16 kHz mono 16-bit WAV) from the seasons script with Windows text-to-speech,
# and optionally copies it into the debug app on a connected device.
#   pwsh tools/make-sample.ps1 [-Out sample.wav] [-Push]
# Needs the Korean voice "Microsoft Heami Desktop" (installed with the Korean language pack).
# TTS has no natural fillers or false starts, so this sample cannot check filler transcription.
param(
    [string]$Out = "sample.wav",
    [switch]$Push
)

$ErrorActionPreference = "Stop"
$script = Join-Path $PSScriptRoot "..\shared\src\commonMain\composeResources\files\fixtures\sample-seasons\script.txt"
$text = Get-Content -Raw -Encoding UTF8 $script

Add-Type -AssemblyName System.Speech
$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
try {
    $synth.SelectVoice("Microsoft Heami Desktop")
    $synth.Rate = -2
    $format = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(
        16000,
        [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen,
        [System.Speech.AudioFormat.AudioChannel]::Mono)
    $outPath = if ([System.IO.Path]::IsPathRooted($Out)) { $Out } else { Join-Path (Get-Location) $Out }
    $synth.SetOutputToWaveFile($outPath, $format)
    $synth.Speak($text)
} finally {
    $synth.Dispose()
}

$bytes = (Get-Item $Out).Length
"Wrote $Out ($bytes bytes, {0:N1} s)" -f (($bytes - 44) / 32000)

if ($Push) {
    adb push $Out /data/local/tmp/sample.wav
    adb shell run-as dev.cocoa.speech cp /data/local/tmp/sample.wav files/sample.wav
    "Copied into the app. Tap '샘플 파일로 분석 (디버그)'."
}
