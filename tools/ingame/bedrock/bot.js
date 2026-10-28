// Test-Client für Bedrock-Spieler: verbindet sich (ohne echtes Bedrock) mit Geyser auf dem Testserver und schreibt
// mit, was ein Bedrock-Spieler bekommt – Figuren, Spieler/Puppen mit Häuten, Formulare, Töne, Pakete, Gegenstände.
//
// Aufruf:  node bot.js <host> <port> <name> <protokoll> <befehlsdatei> [paketordner]
// Befehle (je Zeile in der Befehlsdatei, wird laufend gelesen):
//   chat <text>              Chatnachricht
//   befehl </befehl>         Befehl wie aus dem Chat
//   formular <knopf-nr>      letztes Formular beantworten (einfaches Formular: Nummer des Knopfes; Dialog: JSON-Text)
//   formular-json <json>     letztes Formular mit genau dieser Antwort beantworten
//   formular-zu              letztes Formular schließen
//   zaehlen                  Figuren nach Bedrock-Art zählen (im Protokoll „Zählung …“)
//   ende                     Verbindung trennen
// Ohne echtes Bedrock lässt sich nicht sehen, wie es aussieht – geprüft wird, was Geyser schickt.
'use strict'
const fs = require('fs')
const path = require('path')
const bedrock = require('bedrock-protocol')

const [host, port, name, protokoll, befehlsdatei, paketordner] = process.argv.slice(2)
const aus = fs.createWriteStream(protokoll, { flags: 'a' })
const zeit = () => new Date().toTimeString().slice(0, 8)
const log = (text) => { aus.write(`[${zeit()}] ${text}\n`) }

const client = bedrock.createClient({
  host, port: Number(port), username: name, offline: true, raknetBackend: 'raknet-native', version: '1.26.51',
  skipPing: true, viewDistance: 6
})

const figuren = new Map()          // Laufzeit-Nummer → Bedrock-Art
const spieler = new Map()          // UUID → Name (Spieler und Schaufensterpuppen)
let formular = null                // letztes Formular {id, daten}
let itemNamen = new Map()          // Netzwerk-Nummer → Item-Name

const meta = (m, schluessel) => {
  const e = (m || []).find(x => x.key === schluessel)
  return e ? e.value : undefined
}

client.on('resource_packs_info', (p) => {
  const pakete = (p.texture_packs || []).map(x => `${x.uuid} ${x.version} ${x.size} Bytes`)
  log(`Pakete angeboten (${pakete.length}): ${pakete.join(' | ') || '–'}`)
})
client.on('resource_pack_stack', (p) => {
  log(`Paketstapel: ${(p.resource_packs || []).map(x => x.uuid + ' ' + x.version).join(' | ') || '–'}`)
})
client.on('start_game', (p) => {
  log(`Spielstart als ${name} bei ${JSON.stringify(p.player_position)}`)
  for (const it of p.itemstates || []) itemNamen.set(it.runtime_id, it.name)
  const eigene = (p.itemstates || []).filter(i => !i.name.startsWith('minecraft:')).map(i => i.name)
  log(`Eigene Gegenstände (${eigene.length}): ${eigene.slice(0, 30).join(', ')}${eigene.length > 30 ? ' …' : ''}`)
})
client.on('item_registry', (p) => {
  const liste = p.itemstates || p.items || []
  for (const it of liste) itemNamen.set(it.runtime_id, it.name)
  const eigene = liste.filter(i => i.name && !i.name.startsWith('minecraft:')).map(i => i.name)
  log(`Gegenstands-Liste: ${liste.length}, eigene (${eigene.length}): ${eigene.slice(0, 40).join(', ')}${eigene.length > 40 ? ' …' : ''}`)
})
client.on('available_entity_identifiers', (p) => {
  const ids = JSON.stringify(p.nbt || p)
  const eigene = [...ids.matchAll(/"([a-z0-9_]+:[a-z0-9_/.]+)"/g)].map(m => m[1]).filter(x => !x.startsWith('minecraft:'))
  log(`Figurenarten, eigene: ${[...new Set(eigene)].join(', ') || '–'}`)
})
client.on('spawn', () => log('Im Spiel (Spawn)'))
client.on('add_entity', (p) => {
  figuren.set(String(p.runtime_id), p.entity_type)
  const skala = meta(p.metadata, 'scale')
  const variante = meta(p.metadata, 'variant')
  const pos = p.position
  const eig = (p.properties && (p.properties.ints || []).length + (p.properties.floats || []).length) || 0
  log(`Figur ${p.entity_type} #${p.runtime_id} bei ${pos.x.toFixed(1)} ${pos.y.toFixed(1)} ${pos.z.toFixed(1)}` +
    `${skala !== undefined ? ' Maßstab ' + skala : ''}${variante !== undefined ? ' Variante ' + variante : ''}${eig ? ' Eigenschaften ' + JSON.stringify(p.properties) : ''}`)
})
client.on('remove_entity', (p) => { figuren.delete(String(p.entity_id_self)) })
client.on('add_player', (p) => {
  spieler.set(p.uuid, p.username)
  log(`Spieler/Puppe „${p.username}“ ${p.uuid} #${p.runtime_id} bei ${p.position.x.toFixed(1)} ${p.position.y.toFixed(1)} ${p.position.z.toFixed(1)}`)
})
client.on('player_list', (p) => {
  const r = p.records || []
  for (const e of (Array.isArray(r) ? r : (r.records || []))) {
    if (e.username !== undefined) {
      const haut = e.skin_data || {}
      log(`Spielerliste: „${e.username}“ ${e.uuid} Haut ${haut.skin_id || '?'} ${haut.skin_data ? haut.skin_data.width + 'x' + haut.skin_data.height : ''}`)
    }
  }
})
client.on('player_skin', (p) => {
  const h = p.skin || {}
  log(`Haut für ${spieler.get(p.uuid) || p.uuid}: ${h.skin_id || '?'} ${h.skin_data ? h.skin_data.width + 'x' + h.skin_data.height : ''} Geometrie ${(h.geometry_data || '').length} Zeichen`)
})
client.on('modal_form_request', (p) => {
  formular = { id: p.form_id, daten: p.data }
  let kurz = p.data
  try {
    const f = JSON.parse(p.data)
    kurz = `${f.type} „${typeof f.title === 'string' ? f.title : JSON.stringify(f.title)}“ ` +
      (f.buttons ? 'Knöpfe: ' + f.buttons.map((b, i) => i + '=' + (typeof b.text === 'string' ? b.text : JSON.stringify(b.text))).join(' | ') : '') +
      (f.content && Array.isArray(f.content) ? 'Felder: ' + f.content.map(c => c.type + ':' + c.text).join(' | ') : '')
  } catch (e) { /* kein JSON */ }
  log(`Formular #${p.form_id}: ${kurz}`)
})
client.on('text', (p) => { log(`Text (${p.type}): ${p.message}${p.parameters && p.parameters.length ? ' ' + JSON.stringify(p.parameters) : ''}`) })
client.on('play_sound', (p) => { log(`Ton: ${p.name}`) })
client.on('level_sound_event', (p) => { if (p.sound_id === 'undefined' || typeof p.sound_id === 'number') return })
client.on('set_title', (p) => { if (p.text) log(`Titel (${p.type}): ${p.text}`) })
client.on('inventory_content', (p) => {
  const inhalt = (p.input || []).filter(i => i.network_id).map(i => (itemNamen.get(i.network_id) || i.network_id) + (i.count > 1 ? '×' + i.count : ''))
  if (inhalt.length) log(`Inventar ${p.window_id}: ${inhalt.join(', ')}`)
})
client.on('disconnect', (p) => { log(`Getrennt: ${p.message || JSON.stringify(p)}`) })
client.on('kick', (p) => { log(`Rausgeworfen: ${JSON.stringify(p)}`) })
client.on('error', (e) => { log(`Fehler: ${e && e.stack ? e.stack : e}`) })
client.on('close', () => { log('Verbindung zu'); setTimeout(() => process.exit(0), 200) })

// Befehle aus der Datei lesen (wie die Autotest-Zeilen des Servers)
let gelesen = 0
setInterval(() => {
  let text
  try { text = fs.readFileSync(befehlsdatei, 'utf8') } catch (e) { return }
  const zeilen = text.split('\n')
  for (; gelesen < zeilen.length - 1; gelesen++) ausfuehren(zeilen[gelesen].trim())
}, 300)

function ausfuehren (zeile) {
  if (!zeile) return
  const [wort, ...rest] = zeile.split(' ')
  const arg = rest.join(' ')
  log(`Befehl: ${zeile}`)
  switch (wort) {
    case 'chat':
      client.queue('text', {
        needs_translation: false, category: 'authored', type: 'chat', source_name: name, message: arg,
        xuid: '', platform_chat_id: '', has_filtered_message: false
      })
      break
    case 'befehl':
      client.queue('command_request', {
        command: arg, internal: false, version: '52',
        origin: { type: 'player', uuid: client.profile ? client.profile.uuid : '00000000-0000-0000-0000-000000000000', request_id: '', player_entity_id: 0n }
      })
      break
    case 'formular':
    case 'formular-json':
      if (!formular) { log('Kein Formular offen'); break }
      client.queue('modal_form_response', {
        form_id: formular.id, has_response_data: true, data: wort === 'formular' && /^\d+$/.test(arg) ? arg : arg, has_cancel_reason: false
      })
      formular = null
      break
    case 'formular-zu':
      if (!formular) break
      client.queue('modal_form_response', { form_id: formular.id, has_response_data: false, has_cancel_reason: true, cancel_reason: 'closed' })
      formular = null
      break
    case 'zaehlen': {
      const z = {}
      for (const art of figuren.values()) z[art] = (z[art] || 0) + 1
      log(`Zählung: ${Object.entries(z).sort((a, b) => b[1] - a[1]).map(([a, n]) => a + ' ' + n).join(', ')}`)
      break
    }
    case 'ende':
      client.disconnect()
      break
    default:
      log(`Unbekannter Befehl: ${zeile}`)
  }
}

if (paketordner) fs.mkdirSync(path.resolve(paketordner), { recursive: true })
