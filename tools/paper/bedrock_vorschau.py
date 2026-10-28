#!/usr/bin/env python3
"""Vorschau der Bedrock-Modelle aus dem Paper-Plugin (ohne Bedrock-Client).

Zeichnet ausgewählte Modelle zweimal mit three.js in Chromium: links aus dem Java-Modell (wie Minecraft es liest),
rechts aus der Bedrock-Geometrie im Bedrock-Paket des Plugins – zurückgerechnet mit den Regeln von Blockbench (x
gespiegelt, Drehung um x und y mit umgekehrtem Vorzeichen, oben/unten umgedrehter Bildausschnitt). Sehen beide gleich
aus, stimmt die Umrechnung in paper/.../bedrock/Modellwandler.java. Wie Bedrock selbst zeichnet, prüft das nicht.

Aufruf:  python3 tools/paper/bedrock_vorschau.py <stinkendreich.mcpack> <ausgabe.png> [item-modell …]
         (Paket nach einem Spieltest mit GEYSER=1: build/paper-test/plugins/StinkendReich/bedrock/stinkendreich.mcpack)
Braucht: Playwright (wie die Spieltests), three.js wird einmal nach build/vorschau/ geladen.
"""
import base64
import http.server
import json
import os
import subprocess
import sys
import threading
import urllib.request
import zipfile
from pathlib import Path

WURZEL = Path(__file__).resolve().parents[2]
ASSETS = [WURZEL / "src/main/resources", WURZEL / "paper/src/main/resources/paket", WURZEL / "paper/src/main/resources/zusatz"]
THREE = "https://cdn.jsdelivr.net/npm/three@0.160.0/build/three.module.js"
STANDARD = [
    "stinkendreich:anzeige/stinkendreich/block/deko/sofa_weiss_einzeln",
    "stinkendreich:anzeige/stinkendreich/block/deko/konzertfluegel",
    "stinkendreich:anzeige/stinkendreich/block/deko/kronleuchter",
    "stinkendreich:anzeige/stinkendreich/block/deko/gamingstuhl_einzeln",
    "stinkendreich:anzeige/stinkendreich/block/deko/goldtoilette",
    "stinkendreich:polymer/fahrzeug/bonzenauto_rot",
]


# ---------------------------------------------------------------------------
# Java-Modelle (aus dem Repo)
# ---------------------------------------------------------------------------

def java_datei(pfad):
    for a in ASSETS:
        p = a / pfad
        if p.exists():
            return p.read_bytes()
    return None


GRUND = {  # wie Grundmodelle.java (Formen aus dem Spiel, selbst nachgebaut)
    "minecraft:block/cube": {"elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
        f: {"texture": "#" + f} for f in ["down", "up", "north", "south", "west", "east"]}}]},
    "minecraft:block/cube_all": {"parent": "minecraft:block/cube", "textures": {f: "#all" for f in ["down", "up", "north", "south", "west", "east"]}},
}


def java_modell(mid):
    mid = mid if ":" in mid else "minecraft:" + mid
    if mid in GRUND:
        return GRUND[mid]
    ns, p = mid.split(":", 1)
    b = java_datei(f"assets/{ns}/models/{p}.json")
    return json.loads(b) if b else {}


def textur_bild(tid):
    ns, p = (tid if ":" in tid else "minecraft:" + tid).split(":", 1)
    b = java_datei(f"assets/{ns}/textures/{p}.png")
    return b


def png_groesse(b):
    return int.from_bytes(b[16:20], "big"), int.from_bytes(b[20:24], "big")


def java_wuerfel(mid):
    """Java-Modell → Würfel in Modellraum (Mitte 0), Ausschnitte in Pixeln der Textur."""
    texturen, elemente, m = {}, None, mid
    for _ in range(10):
        if not m:
            break
        d = java_modell(m)
        for k, v in d.get("textures", {}).items():
            texturen.setdefault(k, v)
        if elemente is None and "elements" in d:
            elemente = d["elements"]
        m = d.get("parent")
    aus = []
    for el in elemente or []:
        f, t = el["from"], el["to"]
        w = {"from": [f[0] - 8, f[1] - 8, f[2] - 8], "to": [t[0] - 8, t[1] - 8, t[2] - 8], "pivot": [0, 0, 0], "rot": [0, 0, 0], "faces": {}}
        r = el.get("rotation")
        if r:
            o = r.get("origin", [8, 8, 8])
            w["pivot"] = [o[0] - 8, o[1] - 8, o[2] - 8]
            if "axis" in r:
                w["rot"] = [r["angle"] if r["axis"] == a else 0 for a in "xyz"]
            else:
                w["rot"] = [r.get("x", 0), r.get("y", 0), r.get("z", 0)]
        for name, face in el.get("faces", {}).items():
            tx = face.get("texture", "")
            for _ in range(10):
                if tx.startswith("#"):
                    tx = texturen.get(tx[1:], "")
            bild = textur_bild(tx) if tx else None
            bw, bh = png_groesse(bild) if bild else (16, 16)
            fh = bw if bild and java_datei(f"assets/{(tx if ':' in tx else 'minecraft:' + tx).split(':', 1)[0]}/textures/{(tx if ':' in tx else 'minecraft:' + tx).split(':', 1)[1]}.png.mcmeta") and bh > bw else bh
            uv = face.get("uv") or standard_uv(name, f, t)
            sx, sy = bw / 16, fh / 16
            w["faces"][name] = {"uv": [uv[0] * sx, uv[1] * sy, uv[2] * sx, uv[3] * sy], "w": bw, "h": bh,
                                "rot": face.get("rotation", 0), "tex": tx}
        aus.append(w)
    return aus


def standard_uv(name, f, t):
    return {"down": [f[0], 16 - t[2], t[0], 16 - f[2]], "up": [f[0], f[2], t[0], t[2]],
            "north": [16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]], "south": [f[0], 16 - t[1], t[0], 16 - f[1]],
            "west": [f[2], 16 - t[1], t[2], 16 - f[1]], "east": [16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]]}[name]


def java_modell_von_item(item):
    ns, p = item.split(":", 1)
    b = java_datei(f"assets/{ns}/items/{p}.json")
    if b:
        def suche(o):
            if isinstance(o, dict):
                if str(o.get("type", "")).endswith("model") and isinstance(o.get("model"), str):
                    return o["model"]
                for v in o.values():
                    r = suche(v)
                    if r:
                        return r
            if isinstance(o, list):
                for v in o:
                    r = suche(v)
                    if r:
                        return r
        return suche(json.loads(b))
    if p.startswith("anzeige/"):           # Item-Definitionen der Anzeige-Figuren baut das Plugin selbst
        rest = p[len("anzeige/"):]
        return rest.split("/", 1)[0] + ":" + rest.split("/", 1)[1]
    return None


# ---------------------------------------------------------------------------
# Bedrock-Paket
# ---------------------------------------------------------------------------

def bedrock_wuerfel(zp, item):
    varianten = json.loads(zp.read("sr_varianten.json"))
    v = varianten[item]
    figur = json.loads(zp.read("entity/sr_anzeige.entity.json"))["minecraft:client_entity"]["description"]
    regeln = json.loads(zp.read("render_controllers/sr_anzeige.render_controllers.json"))["render_controllers"]
    geodateien = {}
    for n in zp.namelist():
        if n.startswith("models/entity/sr/") and n.endswith(".geo.json"):
            g = json.loads(zp.read(n))["minecraft:geometry"][0]
            geodateien[g["description"]["identifier"]] = g
    aus = []
    for r in regeln.values():
        gname = r["arrays"]["geometries"]["Array.g"][v].split(".", 1)[1]
        tname = r["arrays"]["textures"]["Array.t"][v].split(".", 1)[1]
        if gname == "leer":
            continue
        g = geodateien[figur["geometry"][gname]]
        tw, th = g["description"]["texture_width"], g["description"]["texture_height"]
        tpfad = figur["textures"][tname]
        for knochen in g["bones"]:
            for c in knochen.get("cubes", []):
                o, s = c["origin"], c["size"]
                von = [-(o[0] + s[0]), o[1], o[2]]
                w = {"from": von, "to": [von[0] + s[0], von[1] + s[1], von[2] + s[2]], "pivot": [0, 0, 0], "rot": [0, 0, 0], "faces": {}}
                if "rotation" in c:
                    p = c.get("pivot", [0, 0, 0])
                    w["pivot"] = [-p[0], p[1], p[2]]
                    w["rot"] = [-c["rotation"][0], -c["rotation"][1], c["rotation"][2]]
                for name, f in c["uv"].items():
                    (u, vv), (du, dv) = f["uv"], f["uv_size"]
                    if name in ("up", "down"):        # Blockbench dreht oben/unten beim Speichern um
                        u, vv, du, dv = u + du, vv + dv, -du, -dv
                    w["faces"][name] = {"uv": [u, vv, u + du, vv + dv], "w": tw, "h": th, "rot": f.get("uv_rotation", 0), "tex": "bedrock:" + tpfad}
                aus.append(w)
    return aus


# ---------------------------------------------------------------------------
# Zeichnen
# ---------------------------------------------------------------------------

SEITE = r"""<!doctype html><meta charset="utf-8"><body style="margin:0;background:#20242b">
<canvas id="c"></canvas>
<script type="module">
import * as THREE from './three.module.js';
const D = await (await fetch('daten.json')).json();
const Z = 300, N = D.modelle.length;
const r = new THREE.WebGLRenderer({canvas: document.getElementById('c'), antialias: true, preserveDrawingBuffer: true});
r.setSize(2 * Z, N * Z); r.setScissorTest(true);
const lader = new THREE.TextureLoader(), cache = {};
// Erst alle Bilder laden, dann zeichnen (sonst bleiben die Flächen leer)
for (const [k, url] of Object.entries(D.texturen)) {
  if (!url) { cache[k] = null; continue; }
  const t = await lader.loadAsync(url);
  t.magFilter = THREE.NearestFilter; t.minFilter = THREE.NearestFilter; t.colorSpace = THREE.SRGBColorSpace;
  cache[k] = t;
}
function tex(k) { return cache[k] || null; }
const SEITEN = ['east', 'west', 'up', 'down', 'south', 'north'];   // Reihenfolge der BoxGeometry
function wuerfel(w) {
  const gr = [0, 1, 2].map(i => Math.max(Math.abs(w.to[i] - w.from[i]), 0.001));
  const geo = new THREE.BoxGeometry(...gr);
  const uv = geo.attributes.uv, mats = [];
  SEITEN.forEach((s, i) => {
    const f = w.faces[s];
    if (!f) { mats.push(new THREE.MeshBasicMaterial({visible: false})); return; }
    const [u1, v1, u2, v2] = f.uv, ecken = [[u1, v1], [u2, v1], [u2, v2], [u1, v2]];
    const k = ((f.rot || 0) / 90) | 0, idx = [0, 1, 3, 2];  // Ecken TL TR BR BL → Punkte 0 1 3 2
    for (let e = 0; e < 4; e++) { const [u, v] = ecken[(e - k + 4) % 4]; uv.setXY(i * 4 + idx[e], u / f.w, 1 - v / f.h); }
    const t = tex(f.tex);
    mats.push(t ? new THREE.MeshLambertMaterial({map: t, alphaTest: 0.5, side: THREE.DoubleSide, transparent: false})
                : new THREE.MeshLambertMaterial({color: 0x888888, side: THREE.DoubleSide}));
  });
  const m = new THREE.Mesh(geo, mats);
  const mitte = [0, 1, 2].map(i => (w.from[i] + w.to[i]) / 2);
  const g = new THREE.Group();
  g.position.set(...w.pivot);
  m.position.set(mitte[0] - w.pivot[0], mitte[1] - w.pivot[1], mitte[2] - w.pivot[2]);
  g.rotation.set(...w.rot.map(a => a * Math.PI / 180), 'ZYX');
  g.add(m);
  return g;
}
D.modelle.forEach((mod, zeile) => {
  ['java', 'bedrock'].forEach((art, spalte) => {
    const szene = new THREE.Scene();
    szene.background = new THREE.Color(spalte ? 0x2b3340 : 0x33302a);
    szene.add(new THREE.AmbientLight(0xffffff, 1.6));
    const l = new THREE.DirectionalLight(0xffffff, 1.4); l.position.set(3, 5, 4); szene.add(l);
    const wurzel = new THREE.Group();
    mod[art].forEach(w => wurzel.add(wuerfel(w)));
    wurzel.rotation.y = Math.PI;                       // wie in der Welt (Anzeige-Figur dreht Gegenstände um 180°)
    szene.add(wurzel);
    const box = new THREE.Box3().setFromObject(wurzel), gr = box.getSize(new THREE.Vector3()).length(), mitte = box.getCenter(new THREE.Vector3());
    const cam = new THREE.PerspectiveCamera(35, 1, 0.1, 1000);
    cam.position.copy(mitte).add(new THREE.Vector3(0.9, 0.7, 1.2).normalize().multiplyScalar(gr * 1.6 + 1));
    cam.lookAt(mitte);
    r.setViewport(spalte * Z, (N - 1 - zeile) * Z, Z, Z); r.setScissor(spalte * Z, (N - 1 - zeile) * Z, Z, Z);
    r.render(szene, cam);
  });
});
document.title = 'fertig';
</script>"""


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(1)
    paket, ausgabe = Path(sys.argv[1]), Path(sys.argv[2]).resolve()
    items = sys.argv[3:] or STANDARD
    arbeit = WURZEL / "build/vorschau"
    arbeit.mkdir(parents=True, exist_ok=True)
    if not (arbeit / "three.module.js").exists():
        urllib.request.urlretrieve(THREE, arbeit / "three.module.js")
    daten = {"modelle": [], "texturen": {}}
    with zipfile.ZipFile(paket) as zp:
        namen = set(zp.namelist())
        for item in items:
            jm = java_modell_von_item(item)
            mod = {"name": item, "java": java_wuerfel(jm) if jm else [], "bedrock": bedrock_wuerfel(zp, item)}
            for art in ("java", "bedrock"):
                for w in mod[art]:
                    for f in w["faces"].values():
                        k = f["tex"]
                        if k in daten["texturen"]:
                            continue
                        if k.startswith("bedrock:"):
                            p = k[len("bedrock:"):] + ".png"
                            b = zp.read(p) if p in namen else None
                        else:
                            b = textur_bild(k)
                        daten["texturen"][k] = "data:image/png;base64," + base64.b64encode(b).decode() if b else None
            daten["modelle"].append(mod)
            print(f"{item}: Java {len(mod['java'])} Würfel, Bedrock {len(mod['bedrock'])} Würfel")
    (arbeit / "daten.json").write_text(json.dumps(daten))
    (arbeit / "index.html").write_text(SEITE)
    os.chdir(arbeit)
    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), http.server.SimpleHTTPRequestHandler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    hoehe = 300 * len(items)
    js = f"""
const {{chromium}} = require('/opt/node22/lib/node_modules/playwright');
(async () => {{
  const b = await chromium.launch({{args: ['--use-gl=swiftshader', '--enable-webgl', '--ignore-gpu-blocklist']}});
  const p = await b.newPage({{viewport: {{width: 600, height: {hoehe}}}}});
  p.on('console', m => console.log('Seite:', m.text()));
  await p.goto('http://127.0.0.1:{server.server_address[1]}/index.html');
  await p.waitForFunction(() => document.title === 'fertig', null, {{timeout: 60000}});
  await p.waitForTimeout(1500);
  await p.screenshot({{path: '{ausgabe}'}});
  await b.close();
}})();
"""
    subprocess.run(["node", "-e", js], check=True)
    server.shutdown()
    print(f"Vorschau: {ausgabe} (links Java, rechts Bedrock)")


if __name__ == "__main__":
    main()
