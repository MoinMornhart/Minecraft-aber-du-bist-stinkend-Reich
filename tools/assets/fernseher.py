#!/usr/bin/env python3
"""XXL-Fernseher: Standbilder (aus, lädt), Item-Bild und Texte.

Die Textur ist 1024 × 256 groß (das Vierfache der Modell-Abwicklung 256 × 64); der Bildschirm liegt bei
(8, 8) mit 320 × 180 Pixeln, der Rest ist der schwarze Rahmen. Das Bild eines Videos malt der Klient selbst
(FernseherBild.java). Wird von tools/assets/assets.py aufgerufen.
"""
from __future__ import annotations

import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

WURZEL = Path(__file__).resolve().parents[2]
ASSETS = WURZEL / "src/main/resources/assets/stinkendreich"
SCHRIFT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
BX, BY, BB, BH = 8, 8, 320, 180

TEXTE_DE = {
    "entity.stinkendreich.fernseher": "XXL-Fernseher",
    "item.stinkendreich.fernseher": "XXL-Fernseher",
    "stinkendreich.fernseher.titel": "XXL-Fernseher",
    "stinkendreich.fernseher.laeuft": "Läuft gerade: %s",
    "stinkendreich.fernseher.link": "YouTube-Link einfügen (Strg + V)",
    "stinkendreich.fernseher.anzeigen": "Auf dem Fernseher anzeigen",
    "stinkendreich.fernseher.abspielen": "Auf dem PC abspielen (Browser)",
    "stinkendreich.fernseher.aus": "Ausschalten",
    "stinkendreich.fernseher.ungueltig": "Kein gültiger YouTube-Link",
    "stinkendreich.fernseher.hinweis": "Das Video läuft im Browser deines PCs, der Fernseher zeigt Bild und Titel.",
    "stinkendreich.fernseher.hinweis_video": "Bild und Ton laufen im Fernseher, für alle in der Nähe.",
    "stinkendreich.fernseher.im_tv": "Im Fernseher abspielen",
    "stinkendreich.fernseher.anhalten": "Anhalten",
    "stinkendreich.fernseher.weiter": "Weiter",
    "stinkendreich.fernseher.stopp": "Stopp",
    "stinkendreich.fernseher.freigabe.titel": "Videos im Fernseher",
    "stinkendreich.fernseher.freigabe.zeile1": "Für Videos braucht die Mod drei kostenlose Hilfsprogramme:",
    "stinkendreich.fernseher.freigabe.zeile2": "yt-dlp, FFmpeg und Deno (zusammen etwa 200 MB, nur einmal).",
    "stinkendreich.fernseher.freigabe.zeile3": "Sie kommen direkt von GitHub nach .minecraft/stinkendreich/werkzeuge.",
    "stinkendreich.fernseher.freigabe.ja": "Herunterladen und abspielen",
    "stinkendreich.fernseher.freigabe.nein": "Nein, nur das Vorschaubild",
    "stinkendreich.fernseher.status.laden": "Fernseher: Hilfsprogramme werden geladen … %s",
    "stinkendreich.fernseher.status.video": "Fernseher: Video wird geladen …",
    "stinkendreich.fernseher.status.fehler": "Fernseher: %s",
    "stinkendreich.fernseher.status.freigabe": "Hier läuft ein Video. Rechtsklick auf den Fernseher, um Videos im Spiel einzuschalten.",
    "stinkendreich.fernbedienung.keiner": "Kein Fernseher in der Nähe",
}
TEXTE_EN = {
    "entity.stinkendreich.fernseher": "XXL TV",
    "item.stinkendreich.fernseher": "XXL TV",
    "stinkendreich.fernseher.titel": "XXL TV",
    "stinkendreich.fernseher.laeuft": "Now showing: %s",
    "stinkendreich.fernseher.link": "Paste a YouTube link (Ctrl + V)",
    "stinkendreich.fernseher.anzeigen": "Show on the TV",
    "stinkendreich.fernseher.abspielen": "Play on your PC (browser)",
    "stinkendreich.fernseher.aus": "Turn off",
    "stinkendreich.fernseher.ungueltig": "Not a valid YouTube link",
    "stinkendreich.fernseher.hinweis": "The video plays in your PC's browser; the TV shows its picture and title.",
    "stinkendreich.fernseher.hinweis_video": "Picture and sound play on the TV for everyone nearby.",
    "stinkendreich.fernseher.im_tv": "Play on the TV",
    "stinkendreich.fernseher.anhalten": "Pause",
    "stinkendreich.fernseher.weiter": "Resume",
    "stinkendreich.fernseher.stopp": "Stop",
    "stinkendreich.fernseher.freigabe.titel": "Videos on the TV",
    "stinkendreich.fernseher.freigabe.zeile1": "For videos the mod needs three free helper programs:",
    "stinkendreich.fernseher.freigabe.zeile2": "yt-dlp, FFmpeg and Deno (about 200 MB in total, only once).",
    "stinkendreich.fernseher.freigabe.zeile3": "They come straight from GitHub into .minecraft/stinkendreich/werkzeuge.",
    "stinkendreich.fernseher.freigabe.ja": "Download and play",
    "stinkendreich.fernseher.freigabe.nein": "No, just the preview picture",
    "stinkendreich.fernseher.status.laden": "TV: downloading helper programs … %s",
    "stinkendreich.fernseher.status.video": "TV: loading video …",
    "stinkendreich.fernseher.status.fehler": "TV: %s",
    "stinkendreich.fernseher.status.freigabe": "A video is playing here. Right-click the TV to turn on videos in the game.",
    "stinkendreich.fernbedienung.keiner": "No TV nearby",
}


def standbild(zeile1: str, zeile2: str) -> Image.Image:
    img = Image.new("RGBA", (1024, 256), (10, 10, 12, 255))
    d = ImageDraw.Draw(img)
    for y in range(BH):
        t = y / BH
        d.line([(BX, BY + y), (BX + BB - 1, BY + y)], fill=(int(16 + 20 * t), int(18 + 10 * t), int(40 + 40 * t), 255))
    gross = ImageFont.truetype(SCHRIFT, 26)
    klein = ImageFont.truetype(SCHRIFT, 13)
    for text, schrift, y, farbe in ((zeile1, gross, BY + 62, (255, 214, 90)), (zeile2, klein, BY + 108, (220, 224, 236))):
        b = d.textlength(text, font=schrift)
        d.text((BX + (BB - b) / 2, y), text, font=schrift, fill=(*farbe, 255))
    d.rectangle([BX + 140, BY + 150, BX + 180, BY + 152], fill=(255, 214, 90, 255))
    return img


def item_bild() -> Image.Image:
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 3, 15, 12], fill=(16, 16, 18, 255))
    d.rectangle([1, 4, 14, 11], fill=(40, 60, 120, 255))
    d.polygon([(6, 6), (6, 9), (9, 7)], fill=(255, 255, 255, 255))
    d.line([(6, 13), (9, 13)], fill=(212, 168, 64, 255))
    return img


def schreibe_json(pfad: Path, daten) -> None:
    pfad.parent.mkdir(parents=True, exist_ok=True)
    pfad.write_text(json.dumps(daten, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def main() -> dict:
    ordner = ASSETS / "textures/entity/fernseher"
    ordner.mkdir(parents=True, exist_ok=True)
    standbild("STINKEND REICH TV", "Rechtsklick: YouTube-Link einfügen").save(ordner / "aus.png")
    standbild("STINKEND REICH TV", "Lädt …").save(ordner / "laedt.png")
    item_bild().save(ASSETS / "textures/item/fernseher.png")
    schreibe_json(ASSETS / "models/item/fernseher.json",
                  {"parent": "minecraft:item/generated", "textures": {"layer0": "stinkendreich:item/fernseher"}})
    schreibe_json(ASSETS / "items/fernseher.json", {"model": {"type": "minecraft:model", "model": "stinkendreich:item/fernseher"}})
    return {"de": TEXTE_DE, "en": TEXTE_EN}


if __name__ == "__main__":
    main()
