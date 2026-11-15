"""Generate the Android search index from the same data as the floor navigator."""
import json
from pathlib import Path

assets = Path(__file__).resolve().parents[1] / 'app/src/main/assets/maps'

def read(name):
    script = (assets / name).read_text(encoding='utf-8')
    return json.JSONDecoder().raw_decode(script.split('=', 1)[1].lstrip())[0]

campus = read('campus-data.js')
floors = read('map-data.js')
rooms = {}
for floor in floors.values():
    for room in floor['rooms']:
        name = room['name']
        if not name.startswith(floor['building'] + '-'):
            continue
        rooms.setdefault(name, [name, room['label'], floor['building'], str(floor['floor'])])
(assets / 'native-index.json').write_text(
    json.dumps({'buildings': campus, 'rooms': list(rooms.values())}, ensure_ascii=False, separators=(',', ':')),
    encoding='utf-8',
)
