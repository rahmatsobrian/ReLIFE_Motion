package com.siroha.relifemotion.data

data class ParamDef(val key: String, val label: String, val min: Float, val max: Float, val def: Float)

data class EffectDef(
    val id: String,
    val nameId: String,
    val nameEn: String,
    val category: String,
    val params: List<ParamDef>
) {
    /** true bila efek ini benar-benar dirender oleh mesin render ReLIFE Motion. */
    val implemented: Boolean get() = params.isNotEmpty() || id in EffectCatalog.noParamImplemented
}

object EffectCatalog {
    val categories = listOf(
        "Warna & Cahaya", "Gambar & Tepi", "Blur", "Distorsi/Warp", "Procedural", "3D",
        "Move/Transform", "Ulangi", "Matte/Mask/Key", "Opacity/Visibility", "Teks", "Lainnya"
    )

    val noParamImplemented = setOf<String>()

    private fun p(k: String, l: String, mn: Float, mx: Float, d: Float) = ParamDef(k, l, mn, mx, d)

    /** Efek yang dirender sungguhan beserta parameternya. */
    private val impl: Map<String, List<ParamDef>> = mapOf(
        "gaussian" to listOf(p("r", "Radius", 0f, 100f, 20f)),
        "box" to listOf(p("r", "Radius", 0f, 100f, 20f)),
        "lens" to listOf(p("r", "Radius", 0f, 100f, 24f)),
        "motion" to listOf(p("d", "Jarak", 0f, 300f, 60f), p("a", "Sudut", -180f, 180f, 0f)),
        "directional" to listOf(p("d", "Jarak", 0f, 300f, 80f), p("a", "Sudut", -180f, 180f, 45f)),
        "zoomblur" to listOf(p("amt", "Jumlah", 0f, 100f, 30f)),
        "spinblur" to listOf(p("amt", "Sudut", 0f, 45f, 10f)),
        "mosaic" to listOf(p("size", "Ukuran", 2f, 100f, 20f)),
        "pixelate" to listOf(p("size", "Ukuran", 2f, 100f, 20f)),
        "brightness" to listOf(p("b", "Kecerahan", -1f, 1f, 0.2f), p("c", "Kontras", 0f, 2f, 1.2f)),
        "saturation" to listOf(p("s", "Saturasi", 0f, 3f, 1.6f)),
        "hue" to listOf(p("deg", "Derajat", -180f, 180f, 90f)),
        "invert" to listOf(p("amt", "Jumlah", 0f, 1f, 1f)),
        "temperature" to listOf(p("t", "Suhu", -1f, 1f, 0.4f)),
        "exposure" to listOf(p("e", "Eksposur", -2f, 2f, 0.6f)),
        "threshold" to listOf(p("lvl", "Level", 0f, 1f, 0.5f)),
        "colorize" to listOf(p("hue", "Hue", 0f, 360f, 200f), p("amt", "Jumlah", 0f, 1f, 1f)),
        "solid" to listOf(p("hue", "Hue", 0f, 360f, 0f), p("sat", "Saturasi", 0f, 1f, 1f)),
        "rgbsplit" to listOf(p("d", "Jarak", 0f, 60f, 12f)),
        "softglow" to listOf(p("r", "Radius", 2f, 60f, 18f), p("i", "Intensitas", 0f, 2f, 1f)),
        "glow" to listOf(p("r", "Radius", 2f, 60f, 24f), p("i", "Intensitas", 0f, 2f, 1.2f)),
        "blink" to listOf(p("rate", "Kecepatan", 0.5f, 10f, 2f)),
        "flicker" to listOf(p("amt", "Jumlah", 0f, 1f, 0.5f)),
        "fade" to listOf(p("fin", "Masuk (dtk)", 0f, 5f, 0.5f), p("fout", "Keluar (dtk)", 0f, 5f, 0.5f)),
        "pulseop" to listOf(p("rate", "Kecepatan", 0.1f, 10f, 1f), p("amt", "Jumlah", 0f, 1f, 0.5f)),
        "swing" to listOf(p("ang", "Sudut", 0f, 180f, 30f), p("rate", "Kecepatan", 0.1f, 5f, 1f)),
        "spin" to listOf(p("spd", "Derajat/dtk", -720f, 720f, 90f)),
        "oscillate" to listOf(p("dist", "Jarak", 0f, 500f, 80f), p("rate", "Kecepatan", 0.1f, 5f, 1f), p("dir", "Arah", 0f, 360f, 0f)),
        "pulsesize" to listOf(p("amt", "Jumlah", 0f, 1f, 0.3f), p("rate", "Kecepatan", 0.1f, 5f, 1f)),
        "autoshake" to listOf(p("amt", "Jumlah", 0f, 100f, 20f), p("rate", "Kecepatan", 1f, 30f, 10f)),
        "jitter" to listOf(p("amt", "Jumlah", 0f, 100f, 15f), p("rate", "Kecepatan", 1f, 30f, 12f)),
        "offset" to listOf(p("x", "X", -1000f, 1000f, 50f), p("y", "Y", -1000f, 1000f, 0f)),
        "replinear" to listOf(p("n", "Jumlah", 1f, 20f, 5f), p("dx", "Jarak X", -500f, 500f, 60f), p("dy", "Jarak Y", -500f, 500f, 0f)),
        "repeat" to listOf(p("n", "Jumlah", 1f, 20f, 5f), p("dx", "Jarak X", -500f, 500f, 40f), p("dy", "Jarak Y", -500f, 500f, 40f)),
        "repgrid" to listOf(p("cols", "Kolom", 1f, 10f, 3f), p("rows", "Baris", 1f, 10f, 3f), p("gx", "Jarak X", 20f, 600f, 120f), p("gy", "Jarak Y", 20f, 600f, 120f)),
        "repradial" to listOf(p("n", "Jumlah", 1f, 24f, 8f), p("rad", "Radius", 0f, 800f, 150f))
    )

    // kategori=slug:Indonesia:English;...
    private val raw = listOf(
        "Warna & Cahaya=softglow:Cahaya Lembut:Light Glow;posterize:Cetak:Posterize;darkglow:Dark Glow:Dark Glow;replacecolor:Ganti Warna:Replace Color;" +
            "fourcolor:Gradien empat warna:Four-color Gradient;gradoverlay:Hamparan Gradien:Gradient Overlay;brightness:Kecerahan / Kontras:Brightness / Contrast;" +
            "colorbalance:Keseimbangan Warna:Color Balance;colortune:Nada Warna:Color Tune;palettemap:Palette Map;exposure:Paparan / Gamma:Exposure / Gamma;" +
            "rgbsplit:Pemisahan RGB:RGB Split;hue:Pergeseran Hue:Hue Shift;bumpmap:Peta Benjolan:Bump Map;gradmap:Peta Gradien:Gradient Map;" +
            "spectral:Peta Spektral:Spectral Map;invert:Putar:Invert;rays:Rays:Rays;saturation:Saturasi / Semangat:Saturation / Vibrance;" +
            "softglow2:Soft Glow:Soft Glow;hilights:Sorotan dan Bayangan:Highlights and Shadows;spotcolor:Spot Color:Spot Color;" +
            "temperature:Suhu Warna:Color Temperature;hotcolor:Warna Cerah:Hot Color;solid:Warna Solid:Solid Color;colorize:Warnai:Colorize;" +
            "threshold:Threshold:Threshold;longshadow:Bayangan Panjang:Long Shadow;radialshadow:Bayangan Radial:Radial Shadow",
        "Gambar & Tepi=glow:Cahaya:Glow;innerglow:Cahaya dari Dalam:Inner Glow;edgeglow:Edge Glow:Edge Glow;halftonelines:Garis Halftone:Halftone Lines;" +
            "contourlines:Garis Kontur:Contour Lines;glowscan:Glow Scan:Glow Scan;contourgrad:Gradien Kontur:Contour Gradient;roughen:Kasarkan Tepi:Roughen Edges;" +
            "drawprogress:Kemajuan Gambar:Drawing Progress;smoothbevel:Miring Halus:Smooth Bevel;stroketaper:Stroke Taper:Stroke Taper;findedges:Temukan Tepi:Find Edges;" +
            "smoothedges:Tepi Halus:Smooth Edges;electricedges:Tepi Listrik:Electric Edges;halftonedots:Titik Halftone:Halftone Dots;cmykdots:Titik Halftone CMYK:CMYK Halftone;" +
            "strokecolor:Warna Stroke:Stroke Color",
        "Blur=directional:Buram Direksional:Directional Blur;motion:Buram Gerak:Motion Blur;box:Buram Kotak:Box Blur;lens:Buram Lensa:Lens Blur;" +
            "spinblur:Buram Putar:Spin Blur;maskblur:Buram Topeng:Mask Blur;zoomblur:Buram Zoom:Zoom Blur;innerblur:Buram dari Dalam:Inner Blur;" +
            "gaussian:Gaussian Blur:Gaussian Blur;warpblur:Kabur Distorsi:Warp Blur;chromzoom:Kabur Zoom Kromatik:Chromatic Zoom Blur;" +
            "linearstreaks:Linear Streaks:Linear Streaks;mosaic:Mosaik:Mosaic;sharpen:Pertajam:Sharpen;spinstreaks:Spin Streaks:Spin Streaks;" +
            "streakstrips:Streak Strips:Streak Strips;unsharp:Unsharp Mask:Unsharp Mask;zoomstreaks:Zoom Streaks:Zoom Streaks",
        "Distorsi/Warp=curl:Ikal:Curl;glass:Kaca:Glass;omniglass:Kaca Omnis:Omnis Glass;kaleido:Kaleidoskop:Kaleidoscope;polar:Koordinat Kutub:Polar Coordinates;" +
            "fractalwarp:Lengkung Fraktal:Fractal Warp;bend:Lengkungan:Bend;wavewarp:Lengkungan Gelombang:Wave Warp;view360:Penampil 360°:360° Viewer;" +
            "hexshift:Pergeseran Petak Heksagon:Hexagon Tile Shift;dispmap:Peta Pemindahan:Displacement Map;polardisp:Peta Pemindahan Kutub:Polar Displacement Map;" +
            "pixelate:Pixelate:Pixelate;swirl:Pusaran:Swirl;hexrotate:Putar Ubin Heksagon:Hexagon Tile Rotate;squeeze:Remas:Squeeze;reorient:Reorientasi Bidang 360°:360° Reorient;" +
            "ripple:Riak Bundar:Circular Ripple;stretchseg:Stretch Segment:Stretch Segment;turbdisp:Turbulent Displace:Turbulent Displace;tiles:Ubin:Tiles;" +
            "tileshift:Ubin Geser:Tile Shift;hextiling:Ubin Heksagonal:Hexagon Tiling;tilerotate:Ubin Putar:Tile Rotate",
        "Procedural=clouds:Awan:Clouds;starfield:Bidang Bintang:Starfield;simplestars:Bidang Bintang Sederhana:Simple Starfield;star:Bintang:Star;" +
            "noiseblocks:Blokir Derau:Noise Blocks;fractalridges:Bubung Fraktal:Fractal Ridges;noise:Derau:Noise;omino:Difusi Omino+:Omino Diffusion+;" +
            "stripes:Garis-garis:Stripes;grunge:Grunge:Grunge;hexarray:Jajaran Heksagonal:Hexagon Array;grid:Kisi:Grid;checker:Papan Dam:Checker;" +
            "lightning:Petir:Lightning;ribbon:Pita:Ribbon;voronoi:Sel Voronoi:Voronoi Cells;energybeam:Sinar Energi:Energy Beam;radialrays:Sinar Radial:Radial Rays;" +
            "contourstrips:Strip Kontur:Contour Strips;lensflare:Suar Lensa:Lens Flare;dots:Titik:Dots;turbulence:Turbulensi:Turbulence;iridescence:Warna-warni:Iridescence",
        "3D=fliplayer:Balik Layer:Flip Layer;polystar:Bintang Polyhedron:Star Polyhedron;spherize:Bulatkan:Spherize;ring:Cincin:Ring;ellipsoid:Ellipsoid:Ellipsoid;" +
            "pagecurl:Gulung Halaman:Page Curl;heart:Hati:Heart;box3d:Kotak:Box;hollowbox:Kotak Berongga:Hollow Box;cube:Kubus:Cube;pyramid:Piramida:Pyramid;" +
            "starprism:Prisma Bintang:Star Prism;hexprism:Prisma Heksagonal:Hexagonal Prism;rasterextrude:Raster Extrude:Raster Extrude;octa:Segi delapan:Octahedron;" +
            "cross3:Silang Tiga Sumbu:Three-axis Cross;cylinder:Silinder:Cylinder;tunnel:Terowongan:Tunnel;torus:Torus:Torus",
        "Move/Transform=swing:Ayun:Swing;scaleassist:Bantuan Skala:Scale Assist;movepath:Gerak Sepanjang Jalur:Move Along Path;autoshake:Guncang Otomatis:Auto-Shake;" +
            "jitter:Kerlipan Acak:Random Jitter;offset:Offset:Offset;oscillate:Ombang-ambing:Oscillate;randdisp:Pemindahan Acak:Random Displacement;spin:Putar:Spin;" +
            "stretchaxis:Regangkan Sumbu:Stretch Axis;rastertransform:Transformasi Raster:Raster Transform;pulsesize:Ukuran Tekanan:Pulse Size",
        "Ulangi=scatterfield:Bidang Sebar:Scatter Field;repeat:Pengulangan:Repeat;repgrid:Pengulangan Kisi:Grid Repeat;replinear:Pengulangan Linear:Linear Repeat;" +
            "repradial:Pengulangan Radial:Radial Repeat;repscatter:Pengulangan Sebaran:Scatter Repeat;reppath:Ulangi Sepanjang Jalur:Repeat Along Path",
        "Matte/Mask/Key=chroma:Chroma Key:Chroma Key;chromaadv:Chroma Key Lanjutan:Advanced Chroma Key;luma:Luma Key:Luma Key;lumastamper:Luma Stamper:Luma Stamper;" +
            "matte choker:Matte Choker:Matte Choker;mattesolid:Matte Solid:Solid Matte;spill:Pembersih Tumpahan Kunci:Key Spill Cleaner;mattefringe:Pinggiran Matte:Matte Fringe;" +
            "vignette:Skema:Vignette;wipe:Usap:Wipe;radialwipe:Usap Radial:Radial Wipe",
        "Opacity/Visibility=blink:Berkedip:Blink;flicker:Berkedip (Flicker):Flicker;blockdissolve:Block Dissolve:Block Dissolve;feather:Bulu:Feather;" +
            "fade:Fade In/Out:Fade In/Out;dissolve:Larut:Dissolve;pulseop:Opasitas Tekanan:Pulse Opacity",
        "Teks=countup:Hitung Naik/Turun:Count Up/Down;textspacing:Jarak Teks:Text Spacing;textprogress:Kemajuan Teks:Text Progress;timecode:Kode waktu:Timecode;" +
            "textrandom:Pengacak Teks:Text Randomizer;texttransform:Ubah Teks:Text Transform",
        "Lainnya=echo:Echo Keyframes:Echo Keyframes;fillbehind:Isi Belakang:Fill Behind;timequant:Kuantisasi Waktu:Time Quantization;remaphsv:Pemetaan Ulang Channel (HSV):Channel Remap (HSV);" +
            "remaprgb:Pemetaan Ulang Channel (RGB):Channel Remap (RGB);parenthelper:Penolong Induk:Parenting Helper;magnifybg:Perbesar Latar Belakang:Magnify Background;" +
            "copybg:Salin Latar Belakang:Copy Background"
    )

    val all: List<EffectDef> = raw.flatMap { line ->
        val cat = line.substringBefore('=')
        line.substringAfter('=').split(';').filter { it.isNotBlank() }.map { e ->
            val f = e.split(':')
            val slug = f[0]
            val nameId = f.getOrElse(1) { slug }
            val nameEn = f.getOrElse(2) { nameId }
            EffectDef(slug, nameId, nameEn, cat, impl[slug] ?: emptyList())
        }
    }.distinctBy { it.id }

    private val byId: Map<String, EffectDef> = all.associateBy { it.id }

    fun get(id: String): EffectDef? = byId[id]
    fun inCategory(c: String): List<EffectDef> = all.filter { it.category == c }
    fun defaults(id: String): Map<String, Float> = byId[id]?.params?.associate { it.key to it.def } ?: emptyMap()
    fun search(q: String): List<EffectDef> =
        all.filter { it.nameId.contains(q, true) || it.nameEn.contains(q, true) }
    val popular: List<EffectDef> by lazy {
        listOf("gaussian", "brightness", "glow", "rgbsplit", "mosaic", "swing", "replinear", "fade")
            .mapNotNull { byId[it] }
    }
}
