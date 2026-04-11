param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Drawing

$generatedDir = Join-Path $ProjectRoot "installer\generated"
New-Item -ItemType Directory -Path $generatedDir -Force | Out-Null

$backgroundPath = Join-Path $ProjectRoot "installer\assets\Screenshot_7.png"

function Fill-CanvasBackground {
    param(
        [System.Drawing.Graphics]$Graphics,
        [int]$Width,
        [int]$Height
    )

    $rect = New-Object System.Drawing.Rectangle -ArgumentList 0, 0, $Width, $Height
    $baseBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        $rect,
        [System.Drawing.Color]::FromArgb(255, 3, 12, 10),
        [System.Drawing.Color]::FromArgb(255, 9, 27, 23),
        90
    )
    try {
        $Graphics.FillRectangle($baseBrush, $rect)
    } finally {
        $baseBrush.Dispose()
    }
}

function Draw-FitImage {
    param(
        [System.Drawing.Graphics]$Graphics,
        [string]$SourcePath,
        [int]$X,
        [int]$Y,
        [int]$Width,
        [int]$Height,
        [ValidateSet("Contain", "Cover")]
        [string]$Mode = "Contain",
        [double]$AlignX = 0.5,
        [double]$AlignY = 0.5,
        [System.Drawing.Color]$MatteColor = [System.Drawing.Color]::FromArgb(255, 5, 16, 14)
    )

    if (!(Test-Path -LiteralPath $SourcePath)) {
        return $false
    }

    $img = [System.Drawing.Image]::FromFile($SourcePath)
    $matteBrush = New-Object System.Drawing.SolidBrush($MatteColor)
    try {
        $Graphics.FillRectangle($matteBrush, $X, $Y, $Width, $Height)
        $scale = if ($Mode -eq "Cover") {
            [Math]::Max($Width / $img.Width, $Height / $img.Height)
        } else {
            [Math]::Min($Width / $img.Width, $Height / $img.Height)
        }
        $drawW = [int][Math]::Ceiling($img.Width * $scale)
        $drawH = [int][Math]::Ceiling($img.Height * $scale)
        $drawX = $X + [int][Math]::Round(($Width - $drawW) * $AlignX)
        $drawY = $Y + [int][Math]::Round(($Height - $drawH) * $AlignY)
        $Graphics.DrawImage($img, $drawX, $drawY, $drawW, $drawH)
        return $true
    } finally {
        $matteBrush.Dispose()
        $img.Dispose()
    }
}

function Draw-PanelFrame {
    param(
        [System.Drawing.Graphics]$Graphics,
        [int]$X,
        [int]$Y,
        [int]$Width,
        [int]$Height,
        [System.Drawing.Color]$FillColor
    )

    $fillBrush = New-Object System.Drawing.SolidBrush($FillColor)
    $outerPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(230, 93, 255, 219), 2)
    $innerPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(150, 255, 207, 104), 1)
    try {
        $Graphics.FillRectangle($fillBrush, $X, $Y, $Width, $Height)
        $Graphics.DrawRectangle($outerPen, $X, $Y, $Width - 1, $Height - 1)
        $Graphics.DrawRectangle($innerPen, $X + 6, $Y + 6, $Width - 13, $Height - 13)
    } finally {
        $fillBrush.Dispose()
        $outerPen.Dispose()
        $innerPen.Dispose()
    }
}

function Draw-GlowText {
    param(
        [System.Drawing.Graphics]$Graphics,
        [string]$Text,
        [System.Drawing.Font]$Font,
        [System.Drawing.Brush]$Brush,
        [float]$X,
        [float]$Y,
        [System.Drawing.Color]$ShadowColor
    )

    $shadowBrush = New-Object System.Drawing.SolidBrush($ShadowColor)
    try {
        $Graphics.DrawString($Text, $Font, $shadowBrush, $X + 2, $Y + 2)
        $Graphics.DrawString($Text, $Font, $Brush, $X, $Y)
    } finally {
        $shadowBrush.Dispose()
    }
}

function Draw-Tag {
    param(
        [System.Drawing.Graphics]$Graphics,
        [string]$Text,
        [System.Drawing.Font]$Font,
        [int]$X,
        [int]$Y,
        [int]$Width
    )

    $fillBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(155, 8, 20, 18))
    $borderPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(185, 62, 218, 184), 1)
    $textBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 150, 255, 223))
    $sf = New-Object System.Drawing.StringFormat
    $sf.Alignment = [System.Drawing.StringAlignment]::Center
    $sf.LineAlignment = [System.Drawing.StringAlignment]::Center
    try {
        $Graphics.FillRectangle($fillBrush, $X, $Y, $Width, 24)
        $Graphics.DrawRectangle($borderPen, $X, $Y, $Width - 1, 23)
        $textRect = New-Object System.Drawing.RectangleF -ArgumentList $X, $Y, $Width, 24
        $Graphics.DrawString($Text, $Font, $textBrush, $textRect, $sf)
    } finally {
        $fillBrush.Dispose()
        $borderPen.Dispose()
        $textBrush.Dispose()
        $sf.Dispose()
    }
}

function Draw-FeatureLine {
    param(
        [System.Drawing.Graphics]$Graphics,
        [string]$Text,
        [System.Drawing.Font]$Font,
        [int]$X,
        [int]$Y
    )

    $accentBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 255, 195, 112))
    $textBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(236, 138, 232, 205))
    try {
        $Graphics.FillRectangle($accentBrush, $X, $Y + 6, 8, 8)
        $Graphics.DrawString($Text, $Font, $textBrush, $X + 16, $Y)
    } finally {
        $accentBrush.Dispose()
        $textBrush.Dispose()
    }
}

function New-InstallerBitmap {
    param(
        [string]$OutPath,
        [int]$Width,
        [int]$Height,
        [bool]$Compact
    )

    $bitmap = New-Object System.Drawing.Bitmap($Width, $Height)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
        $graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

        Fill-CanvasBackground -Graphics $graphics -Width $Width -Height $Height

        if ($Compact) {
            $cardX = $Width - 72
            Draw-PanelFrame -Graphics $graphics -X $cardX -Y 8 -Width 60 -Height 60 -FillColor ([System.Drawing.Color]::FromArgb(214, 4, 15, 13))
            [void](Draw-FitImage -Graphics $graphics -SourcePath $backgroundPath -X ($cardX + 6) -Y 14 -Width 48 -Height 26 -Mode "Contain" -AlignX 0.5 -AlignY 0.5)

            $bandBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(168, 3, 11, 10))
            $titleFont = New-Object System.Drawing.Font("Segoe UI", 7.0, [System.Drawing.FontStyle]::Bold)
            $metaFont = New-Object System.Drawing.Font("Segoe UI", 7.2, [System.Drawing.FontStyle]::Regular)
            $badgeFont = New-Object System.Drawing.Font("Segoe UI", 7.6, [System.Drawing.FontStyle]::Bold)
            $titleBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 148, 255, 226))
            $metaBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(232, 124, 221, 191))
            $badgeBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 255, 204, 108))
            try {
                $graphics.FillRectangle($bandBrush, $cardX + 6, 38, 48, 12)
                $graphics.DrawString("LE", $titleFont, $titleBrush, $cardX + 10, 39)
                $graphics.DrawString("SC2", $metaFont, $metaBrush, $cardX + 30, 39)
                $graphics.DrawString("2.0", $badgeFont, $badgeBrush, $cardX + 20, 52)
            } finally {
                $bandBrush.Dispose()
                $titleFont.Dispose()
                $metaFont.Dispose()
                $badgeFont.Dispose()
                $titleBrush.Dispose()
                $metaBrush.Dispose()
                $badgeBrush.Dispose()
            }
        } else {
            $leftSafeWidth = 194
            Draw-PanelFrame -Graphics $graphics -X 10 -Y 12 -Width 176 -Height 126 -FillColor ([System.Drawing.Color]::FromArgb(212, 4, 15, 13))
            [void](Draw-FitImage -Graphics $graphics -SourcePath $backgroundPath -X 18 -Y 20 -Width 160 -Height 94 -Mode "Contain" -AlignX 0.5 -AlignY 0.5)

            $previewBand = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(158, 3, 11, 10))
            $accentPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(86, 128, 255, 224), 2)
            $titleFont = New-Object System.Drawing.Font("Segoe UI", 16, [System.Drawing.FontStyle]::Bold)
            $featureFont = New-Object System.Drawing.Font("Segoe UI", 9.3, [System.Drawing.FontStyle]::Regular)
            $metaFont = New-Object System.Drawing.Font("Segoe UI", 8.2, [System.Drawing.FontStyle]::Bold)
            $versionFont = New-Object System.Drawing.Font("Segoe UI", 10.5, [System.Drawing.FontStyle]::Bold)
            $titleBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 145, 255, 226))
            $versionBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 255, 205, 108))
            try {
                $graphics.FillRectangle($previewBand, 18, 100, 160, 14)
                $graphics.DrawString("SC2 workspace preview", $metaFont, $titleBrush, 24, 99)

                Draw-GlowText -Graphics $graphics -Text "LOCALIZATION" -Font $titleFont -Brush $titleBrush -X 14 -Y 152 -ShadowColor ([System.Drawing.Color]::FromArgb(124, 17, 255, 220))
                Draw-GlowText -Graphics $graphics -Text "EDITOR" -Font $titleFont -Brush $titleBrush -X 14 -Y 178 -ShadowColor ([System.Drawing.Color]::FromArgb(124, 17, 255, 220))
                Draw-FeatureLine -Graphics $graphics -Text "Archive editor" -Font $featureFont -X 16 -Y 220
                Draw-FeatureLine -Graphics $graphics -Text "AI translation flow" -Font $featureFont -X 16 -Y 242
                Draw-FeatureLine -Graphics $graphics -Text "Editable glossaries" -Font $featureFont -X 16 -Y 264
                $graphics.DrawString("VERSION 2.0", $versionFont, $versionBrush, 16, 288)

                $graphics.DrawLine($accentPen, $leftSafeWidth, 34, $Width - 22, 34)
                $graphics.DrawLine($accentPen, $leftSafeWidth + 52, 48, $Width - 60, 48)
            } finally {
                $previewBand.Dispose()
                $accentPen.Dispose()
                $titleFont.Dispose()
                $featureFont.Dispose()
                $metaFont.Dispose()
                $versionFont.Dispose()
                $titleBrush.Dispose()
                $versionBrush.Dispose()
            }
        }

        $bitmap.Save($OutPath, [System.Drawing.Imaging.ImageFormat]::Bmp)
    } finally {
        $graphics.Dispose()
        $bitmap.Dispose()
    }
}

$smallOut = Join-Path $generatedDir "wizard-small.bmp"
$largeOut = Join-Path $generatedDir "wizard-large.bmp"

New-InstallerBitmap -OutPath $smallOut -Width 164 -Height 314 -Compact $true
New-InstallerBitmap -OutPath $largeOut -Width 497 -Height 314 -Compact $false

Write-Host "Installer art generated:"
Write-Host "  $smallOut"
Write-Host "  $largeOut"
